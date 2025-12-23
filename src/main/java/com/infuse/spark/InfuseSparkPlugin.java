package com.infuse.spark;

import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.infuses.EffectSelection;
import com.infuse.spark.infuses.InfuseContext;
import com.infuse.spark.infuses.InfuseRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class InfuseSparkPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Random random = new Random();

    private PlayerDataStore dataStore;
    private InfuseItems infuseItems;
    private NamespacedKey infuseItemKey;
    private HttpServer resourcePackServer;
    private String resourcePackUrl;
    private byte[] resourcePackHash;
    private InfuseRegistry infuseRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.infuseItemKey = new NamespacedKey(this, "infuse_item");
        this.infuseItems = new InfuseItems(this);
        infuseItems.registerItems();
        infuseItems.registerRecipes();

        File dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException ex) {
                getLogger().warning("Failed to create data.yml: " + ex.getMessage());
            }
        }
        this.dataStore = new PlayerDataStore(dataFile);

        setupResourcePack();

        InfuseContext context = new InfuseContext(this, infuseItems);
        this.infuseRegistry = new InfuseRegistry(context);

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("infuse")).setExecutor(this::handleInfuseCommand);
        Objects.requireNonNull(getCommand("infuse")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("drain")).setExecutor(this::handleDrain);
        Objects.requireNonNull(getCommand("drain")).setTabCompleter(this);

        Bukkit.getOnlinePlayers().forEach(this::loadPlayerData);

        Bukkit.getScheduler().runTaskTimer(this, this::tickActionBar, 1L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickCooldowns, InfuseConstants.TICKS_PER_SECOND, InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onDisable() {
        if (resourcePackServer != null) {
            resourcePackServer.stop(0);
        }
        if (infuseRegistry != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                infuseRegistry.onDisable(player, getData(player));
            }
        }
        try {
            for (PlayerData data : playerData.values()) {
                dataStore.save(data);
            }
            dataStore.saveToDisk();
        } catch (IOException ex) {
            getLogger().warning("Failed to save data.yml: " + ex.getMessage());
        }
    }

    public NamespacedKey getInfuseItemKey() {
        return infuseItemKey;
    }

    private void setupResourcePack() {
        if (!getConfig().getBoolean("resource-pack.enable", true)) {
            return;
        }
        String packPath = getConfig().getString("resource-pack.path", "Infuse Pack.zip");
        File packFile = new File(getDataFolder(), packPath);
        if (!packFile.exists()) {
            getLogger().warning("Resource pack not found at " + packFile.getAbsolutePath() + ". Place the pack there.");
            return;
        }
        resourcePackHash = computeSha1(packFile);
        if (resourcePackHash == null) {
            return;
        }
        int port = getConfig().getInt("resource-pack.port", 8173);
        String publicUrl = getConfig().getString("resource-pack.public-url", "").trim();
        try {
            resourcePackServer = HttpServer.create(new InetSocketAddress(port), 0);
            resourcePackServer.createContext("/resourcepack", new ResourcePackHandler(packFile));
            resourcePackServer.setExecutor(null);
            resourcePackServer.start();
        } catch (IOException ex) {
            getLogger().warning("Unable to start resource pack server: " + ex.getMessage());
            return;
        }
        if (publicUrl.isEmpty()) {
            String host = getServer().getIp();
            if (host == null || host.isBlank()) {
                host = "localhost";
            }
            publicUrl = "http://" + host + ":" + port + "/resourcepack";
        }
        resourcePackUrl = publicUrl;
        getLogger().info("Resource pack URL set to: " + resourcePackUrl);
    }

    private byte[] computeSha1(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = Files.readAllBytes(file.toPath());
            return digest.digest(bytes);
        } catch (NoSuchAlgorithmException | IOException ex) {
            getLogger().warning("Unable to compute resource pack hash: " + ex.getMessage());
            return null;
        }
    }

    private PlayerData loadPlayerData(Player player) {
        PlayerData data = dataStore.load(player.getUniqueId());
        data.setPrimaryActive(false);
        data.setSupportActive(false);
        playerData.put(player.getUniqueId(), data);
        return data;
    }

    private PlayerData getData(Player player) {
        return playerData.computeIfAbsent(player.getUniqueId(), uuid -> dataStore.load(uuid));
    }

    private void tickActionBar() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getData(player);
            infuseRegistry.updateSlot(player, data, 1);
            infuseRegistry.updateSlot(player, data, 2);
            infuseRegistry.tickPlayer(player, data);
            updateCooldownDisplay(data);
            sendActionBar(player, data);
        }
    }

    private void tickCooldowns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getData(player);
            decrementCooldown(data, true);
            decrementCooldown(data, false);
        }
    }

    private void decrementCooldown(PlayerData data, boolean primary) {
        if (primary) {
            data.setPrimarySeconds(data.getPrimarySeconds() - 1);
            if (data.getPrimarySeconds() <= -1) {
                data.setPrimaryMinutes(data.getPrimaryMinutes() - 1);
                data.setPrimarySeconds(59);
            }
        } else {
            data.setSupportSeconds(data.getSupportSeconds() - 1);
            if (data.getSupportSeconds() <= -1) {
                data.setSupportMinutes(data.getSupportMinutes() - 1);
                data.setSupportSeconds(59);
            }
        }
    }

    private void updateCooldownDisplay(PlayerData data) {
        data.setSupportShow(buildCooldownDisplay(data.getSupportMinutes(), data.getSupportSeconds(), "", false));
        data.setSupportShowActive(data.getSupportShow());
        String primaryDisplay = buildCooldownDisplay(data.getPrimaryMinutes(), data.getPrimarySeconds(), data.getPrimaryColorCode(), true);
        data.setPrimaryShow(primaryDisplay);
        data.setPrimaryShowActive(primaryDisplay);
    }

    private String buildCooldownDisplay(int minutes, int seconds, String prefix, boolean includeReset) {
        if (minutes >= 0) {
            if (seconds >= 10) {
                return String.format(" %s%s:%s&r", prefix, minutes, seconds);
            }
            if (seconds >= 0) {
                return String.format(" %s%s:0%s&r", prefix, minutes, seconds);
            }
            if (minutes <= 0) {
                return "";
            }
            return String.format(" %s%s:0%s&r", prefix, minutes, seconds);
        }
        return includeReset ? InfuseConstants.READY_SPACES : InfuseConstants.READY_SPACES;
    }

    private void sendActionBar(Player player, PlayerData data) {
        String primaryShow = data.isPrimaryActive() ? data.getPrimaryShowActive() : data.getPrimaryShow();
        String supportShow = data.isSupportActive() ? data.getSupportShowActive() : data.getSupportShow();
        String message = String.format("%s %s %s %s", primaryShow, data.getActionBarPrimary(), data.getActionBarSupport(), supportShow);
        Component component = LEGACY_SERIALIZER.deserialize(message);
        player.sendActionBar(component);
    }

    private boolean handleInfuseCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }
        PlayerData data = getData(player);
        if (args.length >= 1 && args[0].equalsIgnoreCase("spark")) {
            return handleSparkSubcommand(player, data, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("settings")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("control_set")) {
                handleControlSet(player, data, args[2]);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("primary")) {
            runSlotAbility(player, data, 1);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("support")) {
            runSlotAbility(player, data, 2);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("temp")) {
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("ability")) {
            if (args.length >= 2) {
                if (args[1].equalsIgnoreCase("primary")) {
                    runSlotAbility(player, data, 1);
                } else if (args[1].equalsIgnoreCase("support")) {
                    runSlotAbility(player, data, 2);
                }
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("trust")) {
            handleTrustCommand(player, data, args);
            return true;
        }
        return true;
    }

    private boolean handleSparkSubcommand(Player player, PlayerData data, String[] args) {
        if (!player.hasPermission("infuse.command.infusespark")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("equip")) {
            if (args.length >= 5 && args[2].equalsIgnoreCase("effect")) {
                String type = args[3].toLowerCase(Locale.ROOT);
                int slot = parseSlot(args[4]);
                if (slot != 0) {
                    EffectSelection selection = infuseRegistry.getSelectionByKey(type);
                    SlotHelper.setSlotEffect(data, slot, selection.group(), selection.effectId());
                }
            }
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("cd_reset")) {
            data.setSupportActive(false);
            data.setPrimaryActive(false);
            data.setSupportMinutes(-2);
            data.setPrimaryMinutes(-2);
            data.setSupportSeconds(0);
            data.setPrimarySeconds(0);
            return true;
        }
        return true;
    }

    private int parseSlot(String slotInput) {
        if (slotInput == null) {
            return 0;
        }
        return switch (slotInput) {
            case "1" -> 1;
            case "2" -> 2;
            default -> 0;
        };
    }

    private void handleControlSet(Player player, PlayerData data, String mode) {
        if (mode.equalsIgnoreCase("offhand")) {
            data.setControlSet(1);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            player.sendMessage(ChatColor.GREEN + "You have swapped you're controls to Offhand");
            player.sendMessage(ChatColor.GREEN + "To use these controls is offhand for Support Spark and crouch offhand for Primary Spark");
            player.sendMessage(ChatColor.GREEN + "Better For Java No Mods");
        } else if (mode.equalsIgnoreCase("crouch_mouseclicks")) {
            data.setControlSet(2);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            player.sendMessage(ChatColor.GREEN + "You have swapped you're controls to Mouse Clicks");
            player.sendMessage(ChatColor.GREEN + "To use these controls is Crouch Left Click for Support Spark and Crouch Right Click for Primary Spark");
            player.sendMessage(ChatColor.GREEN + "Better For Bedrock");
        } else if (mode.equalsIgnoreCase("custom_keys") || mode.equalsIgnoreCase("ari_keys")) {
            data.setControlSet(3);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            player.sendMessage(ChatColor.GREEN + "You have swapped you're controls to Custom Key's using the mod CommandKeys 1.21 or Ari Keys 1.20-1.18");
            player.sendMessage(ChatColor.DARK_RED + " MAKE SURE THIS SERVER HAS THE ARI KEYS PLUGIN INSTALLED AND CONFIGED TO WORK WITH THIS IF USING ARI KEYS");
            player.sendMessage(ChatColor.GREEN + "Better For Java with Ari Keys Client Side and Server Side Mod or CommandKeys Client Side Only Mod ");
        }
    }

    private void handleTrustCommand(Player player, PlayerData data, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                data.getTrusted().add(target.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + target.getName() + ChatColor.GREEN + " has been trusted");
            }
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
            if (args[2].equalsIgnoreCase("all")) {
                data.getTrusted().clear();
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Everyone " + ChatColor.RED + "in you're trust list has been un-trusted");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                data.getTrusted().remove(target.getUniqueId());
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + target.getName() + ChatColor.RED + " has been un-trusted");
            }
        }
    }

    private boolean handleDrain(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        PlayerData data = getData(player);
        if (args.length < 1) {
            int slot = data.getNextDrainSlot();
            boolean result = handleSlotDrain(player, slot);
            data.setNextDrainSlot(slot == 1 ? 2 : 1);
            return result;
        }
        if (args[0].equals("1")) {
            return handleSlotDrain(player, 1);
        }
        if (args[0].equals("2")) {
            return handleSlotDrain(player, 2);
        }
        return true;
    }

    private boolean handleSlotDrain(Player player, int slot) {
        PlayerData data = getData(player);
        if (InfuseConstants.READY_SPACES.equals(SlotHelper.getSlotShow(data, slot))) {
            int effect = SlotHelper.getSlotEffect(data, slot);
            if (effect != 0) {
                infuseRegistry.giveEffectItem(player, SlotHelper.getSlotGroup(data, slot), effect);
                SlotHelper.setSlotEffect(data, slot, SlotHelper.getSlotGroup(data, slot), 0);
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INFUSE " + ChatColor.GRAY + "" + ChatColor.BOLD + ">> " + ChatColor.WHITE + "Your Cooldown must be depleted to drain out");
        }
        return true;
    }

    private void runSlotAbility(Player player, PlayerData data, int slot) {
        infuseRegistry.activateSlot(player, data, slot);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = loadPlayerData(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (resourcePackUrl != null && resourcePackHash != null) {
                player.setResourcePack(resourcePackUrl, resourcePackHash);
            }
        }, 1L);
        if (!data.isJoined()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INFUSE " + ChatColor.GRAY
                    + "" + ChatColor.BOLD + ">> " + ChatColor.WHITE
                    + "Make sure to do \"/infuse settings control_set\" to set how you activate your abilities.");
                SlotHelper.setSlotEffect(data, 2, EffectGroup.SUPPORT, random.nextInt(4) + 1);
                data.setJoined(true);
            }, 5L * InfuseConstants.TICKS_PER_SECOND);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        infuseRegistry.onPlayerQuit(event, data);
        dataStore.save(data);
        try {
            dataStore.saveToDisk();
        } catch (IOException ex) {
            getLogger().warning("Failed to save data.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        InfuseItem type = infuseItems.getItemType(item);
        if (type == null) {
            return;
        }
        EffectSelection selection = infuseRegistry.getSelectionByItem(type);
        if (selection == null) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        int slot = SlotHelper.getSlotEffect(data, 1) == 0 ? 1 : (SlotHelper.getSlotEffect(data, 2) == 0 ? 2 : 2);
        int existingEffect = SlotHelper.getSlotEffect(data, slot);
        if (existingEffect != 0) {
            infuseRegistry.giveEffectItem(player, SlotHelper.getSlotGroup(data, slot), existingEffect);
        }
        SlotHelper.setSlotEffect(data, slot, selection.group(), selection.effectId());
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, 1)));
        } else {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(item));
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (data.getControlSet() == 1) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                runSlotAbility(player, data, 1);
            } else {
                runSlotAbility(player, data, 2);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (data.getControlSet() == 2 && player.isSneaking()) {
            if (EnumSet.of(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK).contains(event.getAction())) {
                event.setCancelled(true);
                runSlotAbility(player, data, 1);
            } else if (EnumSet.of(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK).contains(event.getAction())) {
                runSlotAbility(player, data, 2);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        PlayerData data = getData(event.getPlayer());
        infuseRegistry.onPlayerMove(event, data);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        infuseRegistry.onEntityDamage(event, data);
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            infuseRegistry.onEntityDamageByDamager(event, getData(player));
        }
        if (event.getEntity() instanceof Player player) {
            infuseRegistry.onEntityDamageByVictim(event, getData(player));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        PlayerData data = getData(event.getPlayer());
        infuseRegistry.onBlockBreak(event, data);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        infuseRegistry.dropEffectOnDeath(player, data, 2);
        infuseRegistry.dropEffectOnDeath(player, data, 1);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("drain")) {
            if (args.length == 1) {
                return List.of("1", "2");
            }
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("infuse")) {
            if (args.length == 1) {
                return List.of("spark", "settings", "ability", "trust", "primary", "support");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("spark")) {
                return List.of("equip", "cd_reset");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("spark") && args[1].equalsIgnoreCase("equip")) {
                return List.of("effect");
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("spark") && args[1].equalsIgnoreCase("equip")) {
                if (args[2].equalsIgnoreCase("effect")) {
                    return List.of("empty", "strength", "heart", "haste", "invisibility", "feather", "frost", "thunder", "regeneration",
                        "pig", "ocean", "fire", "emerald", "speed");
                }
            }
            if (args.length == 5 && args[0].equalsIgnoreCase("spark") && args[1].equalsIgnoreCase("equip")) {
                if (args[2].equalsIgnoreCase("effect")) {
                    return List.of("1", "2");
                }
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("spark") && args[1].equalsIgnoreCase("cd_reset")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
                return List.of("control_set");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("settings") && args[1].equalsIgnoreCase("control_set")) {
                return List.of("offhand", "crouch_mouseclicks", "ari_keys");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("ability")) {
                return List.of("support", "primary");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
                return List.of("add", "remove");
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
                List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                if (args[1].equalsIgnoreCase("remove")) {
                    List<String> extended = new java.util.ArrayList<>(names);
                    extended.add("all");
                    return extended;
                }
                return names;
            }
        }
        return List.of();
    }

    private class ResourcePackHandler implements HttpHandler {
        private final File file;

        private ResourcePackHandler(File file) {
            this.file = file;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
