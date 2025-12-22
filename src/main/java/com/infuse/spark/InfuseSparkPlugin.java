package com.infuse.spark;

import com.infuse.spark.InfuseItems.InfuseItem;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Particle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class InfuseSparkPlugin extends JavaPlugin implements Listener, TabCompleter {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final String READY_SPACES = "      ";
    private static final int TICKS_PER_SECOND = 20;
    private static final double STRENGTH_DAMAGE_BASE = 2.0;
    private static final double STRENGTH_DAMAGE_SPARK = 1.5;
    private static final double OCEAN_ATTACK_DAMAGE = 2.0;
    private static final double FIRE_ATTACK_DAMAGE = 1.0;

    private static final UUID STRENGTH_MODIFIER = UUID.fromString("f7d5d5c4-5d1b-4b92-9ee4-0c8c293b5f5a");
    private static final UUID STRENGTH_SPARK_MODIFIER = UUID.fromString("6b0e9d41-90d8-447f-b6ab-3000f84509ee");
    private static final UUID OCEAN_ATTACK_MODIFIER = UUID.fromString("1a91d0a7-4f22-4a7a-9dfe-f2b7d8b5c934");
    private static final UUID FIRE_ATTACK_MODIFIER = UUID.fromString("b7db23cc-fba1-4f7a-8896-9cf813f6a47b");
    private static final UUID HEART_EQUIP_MODIFIER = UUID.fromString("1f57d91f-1f48-4c91-bbb5-7e8d7a4b59a4");
    private static final UUID HEART_SPARK_MODIFIER = UUID.fromString("8f7625b1-2f43-4a28-9e1c-c5c1c4e5c169");
    private static final UUID PIG_KNOCKBACK_MODIFIER = UUID.fromString("e3a44368-b83e-49a6-a79c-0d0c5978a9c8");

    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, Integer> pigHitCounts = new HashMap<>();
    private final Set<UUID> heartEquipApplied = new HashSet<>();
    private final Set<UUID> pigKnockbackApplied = new HashSet<>();
    private final Set<UUID> invisibilityHidden = new HashSet<>();
    private final Random random = new Random();

    private PlayerDataStore dataStore;
    private InfuseItems infuseItems;
    private NamespacedKey infuseItemKey;
    private NamespacedKey pigSparkKey;
    private HttpServer resourcePackServer;
    private String resourcePackUrl;
    private byte[] resourcePackHash;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.infuseItemKey = new NamespacedKey(this, "infuse_item");
        this.pigSparkKey = new NamespacedKey(this, "pig_spark_owner");
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

        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("infuse")).setExecutor(this::handleInfuseCommand);
        Objects.requireNonNull(getCommand("infuse")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("drain")).setExecutor(this::handleDrain);
        Objects.requireNonNull(getCommand("drain")).setTabCompleter(this);

        Bukkit.getOnlinePlayers().forEach(this::loadPlayerData);

        Bukkit.getScheduler().runTaskTimer(this, this::tickActionBar, 1L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickCooldowns, TICKS_PER_SECOND, TICKS_PER_SECOND);
    }

    @Override
    public void onDisable() {
        if (resourcePackServer != null) {
            resourcePackServer.stop(0);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeTemporaryModifiers(player);
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
            updateIconsAndPassives(player, data);
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
        return includeReset ? READY_SPACES : READY_SPACES;
    }

    private void sendActionBar(Player player, PlayerData data) {
        String primaryShow = data.isPrimaryActive() ? data.getPrimaryShowActive() : data.getPrimaryShow();
        String supportShow = data.isSupportActive() ? data.getSupportShowActive() : data.getSupportShow();
        String message = String.format("%s %s %s %s", primaryShow, data.getActionBarPrimary(), data.getActionBarSupport(), supportShow);
        Component component = LEGACY_SERIALIZER.deserialize(message);
        player.sendActionBar(component);
    }

    private void updateIconsAndPassives(Player player, PlayerData data) {
        if (data.isPrimaryActive()) {
            switch (data.getPrimary()) {
                case 0 -> {
                    data.setActionBarPrimary("\uE001");
                    data.setPrimaryColorCode("&f&l");
                }
                case 1 -> {
                    data.setActionBarPrimary("\uE014");
                    data.setPrimaryColorCode("&4&l");
                    applyStrengthEquipped(player, data);
                }
                case 2 -> {
                    data.setActionBarPrimary("\uE015");
                    data.setPrimaryColorCode("&5&l");
                    applyHeartEquipped(player);
                }
                case 3 -> {
                    data.setActionBarPrimary("\uE016");
                    data.setPrimaryColorCode("&6&l");
                    applyHasteEquipped(player);
                }
                case 4 -> {
                    data.setActionBarPrimary("\uE017");
                    data.setPrimaryColorCode("&5&l");
                    applyInvisibilityEquipped(player);
                }
                case 5 -> {
                    data.setActionBarPrimary("\uE018");
                    data.setPrimaryColorCode("&2&l");
                }
                case 6 -> {
                    data.setActionBarPrimary("\uE019");
                    data.setPrimaryColorCode("&b&l");
                    applyFrostEquipped(player);
                }
                case 7 -> {
                    data.setActionBarPrimary("\uE020");
                    data.setPrimaryColorCode("&9&l");
                }
                case 8 -> {
                    data.setActionBarPrimary("\uE021");
                    data.setPrimaryColorCode("&c&l");
                }
                case 9 -> {
                    data.setActionBarPrimary("\uE027");
                    data.setPrimaryColorCode("&d&l");
                    applyPigEquipped(player);
                }
                default -> {
                }
            }
        } else {
            if (data.getPrimary() != 2 && heartEquipApplied.contains(player.getUniqueId())) {
                removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER);
                heartEquipApplied.remove(player.getUniqueId());
            }
            switch (data.getPrimary()) {
                case 0 -> {
                    data.setActionBarPrimary("\uE001");
                    data.setPrimaryColorCode("&f&l");
                }
                case 1 -> {
                    data.setActionBarPrimary("\uE002");
                    data.setPrimaryColorCode("&f&l");
                    applyStrengthEquipped(player, data);
                }
                case 2 -> {
                    data.setActionBarPrimary("\uE003");
                    data.setPrimaryColorCode("&f&l");
                    applyHeartEquipped(player);
                }
                case 3 -> {
                    data.setActionBarPrimary("\uE004");
                    data.setPrimaryColorCode("&f&l");
                    applyHasteEquipped(player);
                }
                case 4 -> {
                    data.setActionBarPrimary("\uE005");
                    data.setPrimaryColorCode("&f&l");
                    applyInvisibilityEquipped(player);
                }
                case 5 -> {
                    data.setActionBarPrimary("\uE006");
                    data.setPrimaryColorCode("&f&l");
                }
                case 6 -> {
                    data.setActionBarPrimary("\uE007");
                    data.setPrimaryColorCode("&f&l");
                    applyFrostEquipped(player);
                }
                case 7 -> {
                    data.setActionBarPrimary("\uE008");
                    data.setPrimaryColorCode("&f&l");
                }
                case 8 -> {
                    data.setActionBarPrimary("\uE009");
                    data.setPrimaryColorCode("&f&l");
                }
                case 9 -> {
                    data.setActionBarPrimary("\uE026");
                    data.setPrimaryColorCode("&f&l");
                    applyPigEquipped(player);
                }
                default -> {
                }
            }
        }

        if (data.getPrimary() != 9) {
            pigHitCounts.remove(player.getUniqueId());
            removePigKnockback(player);
        }

        if (data.isSupportActive()) {
            switch (data.getSupport()) {
                case 0 -> data.setActionBarSupport("\uE022&f&l");
                case 1 -> {
                    data.setActionBarSupport("\uE022&9&l");
                    applyOceanEquipped(player);
                }
                case 2 -> data.setActionBarSupport("\uE023&6&l");
                case 3 -> {
                    data.setActionBarSupport("\uE024&a&l");
                    applyEmeraldEquipped(player);
                }
                case 4 -> {
                    data.setActionBarSupport("\uE025&e&l");
                    applySpeedEquipped(player);
                }
                default -> {
                }
            }
        } else {
            switch (data.getSupport()) {
                case 0 -> data.setActionBarSupport("\uE001&f&l");
                case 1 -> {
                    data.setActionBarSupport("\uE010&f&l");
                    applyOceanEquipped(player);
                }
                case 2 -> data.setActionBarSupport("\uE011&f&l");
                case 3 -> {
                    data.setActionBarSupport("\uE012&f&l");
                    applyEmeraldEquipped(player);
                }
                case 4 -> {
                    data.setActionBarSupport("\uE013&f&l");
                    applySpeedEquipped(player);
                }
                default -> {
                }
            }
        }
    }

    private void applyStrengthEquipped(Player player, PlayerData data) {
        applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_MODIFIER,
            STRENGTH_DAMAGE_BASE, 4);
        if (data.isStrengthSparkActive()) {
            applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_SPARK_MODIFIER,
                STRENGTH_DAMAGE_SPARK, 4);
        }
    }

    private void applyHeartEquipped(Player player) {
        if (heartEquipApplied.contains(player.getUniqueId())) {
            return;
        }
        applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER, 10.0);
        heartEquipApplied.add(player.getUniqueId());
    }

    private void applyHasteEquipped(Player player) {
        applyPotion(player, PotionEffectType.HASTE, 2, 2, false, false);
    }

    private void applyInvisibilityEquipped(Player player) {
        applyPotion(player, PotionEffectType.INVISIBILITY, 1, 2, true, true);
    }

    private void applyFrostEquipped(Player player) {
        boolean hasIce = false;
        boolean hasSnow = false;
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Material material = player.getLocation().clone().add(x, y, z).getBlock().getType();
                    if (material == Material.ICE || material == Material.BLUE_ICE || material == Material.PACKED_ICE) {
                        hasIce = true;
                    }
                    if (material == Material.SNOW || material == Material.SNOW_BLOCK) {
                        hasSnow = true;
                    }
                }
            }
        }
        if (hasIce) {
            applyPotion(player, PotionEffectType.SPEED, 10, 2, false, false);
        } else if (hasSnow) {
            applyPotion(player, PotionEffectType.SPEED, 3, 2, false, false);
        }
    }

    private void applyOceanEquipped(Player player) {
        applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 1, 2, false, false);
        applyPotion(player, PotionEffectType.CONDUIT_POWER, 1, 2, false, false);
        if (player.getLocation().getBlock().isLiquid()) {
            applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER,
                OCEAN_ATTACK_DAMAGE, 4);
        }
    }

    private void applyEmeraldEquipped(Player player) {
        applyPotion(player, PotionEffectType.HERO_OF_THE_VILLAGE, 3, 2, false, false);
    }

    private void applySpeedEquipped(Player player) {
        applyPotion(player, PotionEffectType.SPEED, 2, 2, false, false);
    }

    private void applyPigEquipped(Player player) {
        if (pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        applyAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER, 0.05);
        pigKnockbackApplied.add(player.getUniqueId());
    }

    private void removePigKnockback(Player player) {
        if (!pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        removeAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER);
        pigKnockbackApplied.remove(player.getUniqueId());
    }

    private void applyPotion(Player player, PotionEffectType type, int level, int seconds, boolean particles, boolean icon) {
        int amplifier = Math.max(0, level - 1);
        PotionEffect effect = new PotionEffect(type, seconds * TICKS_PER_SECOND, amplifier, false, particles, icon);
        player.addPotionEffect(effect, true);
    }

    private void applyAttributeModifier(Player player, Attribute attribute, UUID uuid, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeAttributeModifier(player, attribute, uuid);
        AttributeModifier modifier = new AttributeModifier(uuid, "infuse" + attribute.name(), amount, AttributeModifier.Operation.ADD_NUMBER);
        instance.addModifier(modifier);
    }

    private void applyTemporaryAttributeModifier(Player player, Attribute attribute, UUID uuid, double amount, int ticks) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeAttributeModifier(player, attribute, uuid);
        AttributeModifier modifier = new AttributeModifier(uuid, "infuse_temp_" + attribute.name(), amount, AttributeModifier.Operation.ADD_NUMBER);
        instance.addModifier(modifier);
        Bukkit.getScheduler().runTaskLater(this, () -> removeAttributeModifier(player, attribute, uuid), ticks);
    }

    private void removeAttributeModifier(Player player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
            .filter(mod -> mod.getUniqueId().equals(uuid))
            .findFirst()
            .ifPresent(instance::removeModifier);
    }

    private void removeTemporaryModifiers(Player player) {
        removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_MODIFIER);
        removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_SPARK_MODIFIER);
        removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER);
        removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER);
        removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
        removeAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER);
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
            runPrimaryAbility(player, data);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("support")) {
            runSupportAbility(player, data);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("temp")) {
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("ability")) {
            if (args.length >= 2) {
                if (args[1].equalsIgnoreCase("primary")) {
                    runPrimaryAbility(player, data);
                } else if (args[1].equalsIgnoreCase("support")) {
                    runSupportAbility(player, data);
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
            if (args.length >= 4) {
                String category = args[2].toLowerCase(Locale.ROOT);
                String type = args[3].toLowerCase(Locale.ROOT);
                if (category.equals("primary")) {
                    data.setPrimary(parsePrimaryType(type));
                } else if (category.equals("support")) {
                    data.setSupport(parseSupportType(type));
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

    private int parsePrimaryType(String type) {
        return switch (type) {
            case "empty" -> 0;
            case "strength" -> 1;
            case "heart" -> 2;
            case "haste" -> 3;
            case "invisibility" -> 4;
            case "feather" -> 5;
            case "frost" -> 6;
            case "thunder" -> 7;
            case "regeneration" -> 8;
            case "pig" -> 9;
            default -> 0;
        };
    }

    private int parseSupportType(String type) {
        return switch (type) {
            case "empty" -> 0;
            case "ocean" -> 1;
            case "fire" -> 2;
            case "emerald" -> 3;
            case "speed" -> 4;
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
        if (args.length < 1) {
            return true;
        }
        if (args[0].equals("1")) {
            return handlePrimaryDrain(player);
        }
        if (args[0].equals("2")) {
            return handleSupportDrain(player);
        }
        return true;
    }

    private boolean handlePrimaryDrain(Player player) {
        PlayerData data = getData(player);
        if (READY_SPACES.equals(data.getPrimaryShow())) {
            if (data.getPrimary() != 0) {
                givePrimaryItem(player, data.getPrimary());
                data.setPrimary(0);
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INFUSE " + ChatColor.GRAY + "" + ChatColor.BOLD + ">> " + ChatColor.WHITE + "Your Cooldown must be depleted to drain out");
        }
        return true;
    }

    private boolean handleSupportDrain(Player player) {
        PlayerData data = getData(player);
        if (READY_SPACES.equals(data.getSupportShow())) {
            if (data.getSupport() != 0) {
                giveSupportItem(player, data.getSupport());
                data.setSupport(0);
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INFUSE " + ChatColor.GRAY + "" + ChatColor.BOLD + ">> " + ChatColor.WHITE + "Your Cooldown must be depleted to drain out");
        }
        return true;
    }

    private void runSupportAbility(Player player, PlayerData data) {
        if (data.getSupport() == 0) {
            return;
        }
        if (!READY_SPACES.equals(data.getSupportShow())) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        switch (data.getSupport()) {
            case 1 -> runOceanSpark(player, data);
            case 2 -> runFireSpark(player, data);
            case 3 -> runEmeraldSpark(player, data);
            case 4 -> runSpeedSpark(player, data);
            default -> {
            }
        }
    }

    private void runPrimaryAbility(Player player, PlayerData data) {
        if (data.getPrimary() == 0) {
            return;
        }
        if (!READY_SPACES.equals(data.getPrimaryShow())) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        switch (data.getPrimary()) {
            case 1 -> runStrengthSpark(player, data);
            case 2 -> runHeartSpark(player, data);
            case 3 -> runHasteSpark(player, data);
            case 4 -> runInvisibilitySpark(player, data);
            case 5 -> runFeatherSpark(player, data);
            case 6 -> runFrostSpark(player, data);
            case 7 -> runThunderSpark(player, data);
            case 8 -> runRegenerationSpark(player, data);
            case 9 -> runPigSpark(player, data);
            default -> {
            }
        }
    }

    private void runOceanSpark(Player player, PlayerData data) {
        data.setSupportActive(true);
        data.setSupportSeconds(30);
        data.setSupportMinutes(0);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().getBlock().isLiquid()) {
                    applyPotion(player, PotionEffectType.REGENERATION, 1, 2, false, false);
                }
                count++;
                if (count >= 60) {
                    data.setSupportActive(false);
                    data.setSupportSeconds(0);
                    data.setSupportMinutes(1);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void runFireSpark(Player player, PlayerData data) {
        data.setSupportActive(true);
        data.setSupportSeconds(30);
        data.setSupportMinutes(0);
        data.setFireSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setFireSparkActive(false);
            data.setSupportActive(false);
            data.setSupportSeconds(0);
            data.setSupportMinutes(1);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runEmeraldSpark(Player player, PlayerData data) {
        data.setSupportActive(true);
        data.setSupportSeconds(30);
        data.setSupportMinutes(1);
        applyPotion(player, PotionEffectType.HERO_OF_THE_VILLAGE, 200, 90, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setSupportActive(false);
            data.setSupportSeconds(0);
            data.setSupportMinutes(5);
        }, 90L * TICKS_PER_SECOND);
    }

    private void runSpeedSpark(Player player, PlayerData data) {
        data.setSupportActive(true);
        data.setSupportSeconds(1);
        data.setSupportMinutes(0);
        Vector direction = player.getLocation().getDirection().normalize().multiply(2);
        player.setVelocity(direction);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setSupportActive(false);
            data.setSupportSeconds(15);
            data.setSupportMinutes(0);
        }, TICKS_PER_SECOND);
    }

    private void runStrengthSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(30);
        data.setPrimaryMinutes(0);
        data.setStrengthSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setStrengthSparkActive(false);
            data.setPrimaryActive(false);
            data.setPrimarySeconds(0);
            data.setPrimaryMinutes(2);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runHeartSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(30);
        data.setPrimaryMinutes(0);
        applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER, 10.0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
            data.setPrimaryActive(false);
            data.setPrimarySeconds(0);
            data.setPrimaryMinutes(1);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runHasteSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(45);
        data.setPrimaryMinutes(0);
        applyPotion(player, PotionEffectType.HASTE, 255, 45, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setPrimaryActive(false);
            data.setPrimarySeconds(15);
            data.setPrimaryMinutes(1);
        }, 45L * TICKS_PER_SECOND);
    }

    private void runInvisibilitySpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(20);
        data.setPrimaryMinutes(0);
        hidePlayerFromAll(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            revealPlayerToAll(player);
            data.setPrimaryActive(false);
            data.setPrimarySeconds(45);
            data.setPrimaryMinutes(0);
        }, 20L * TICKS_PER_SECOND);
    }

    private void runFeatherSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(2);
        data.setPrimaryMinutes(0);
        applyPotion(player, PotionEffectType.LEVITATION, 30, 2, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setPrimaryActive(false);
            data.setPrimarySeconds(30);
            data.setPrimaryMinutes(0);
        }, 2L * TICKS_PER_SECOND);
    }

    private void runFrostSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(30);
        data.setPrimaryMinutes(0);
        data.setFrostSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setPrimaryActive(false);
            data.setPrimarySeconds(0);
            data.setPrimaryMinutes(1);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runThunderSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(10);
        data.setPrimaryMinutes(0);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (entity instanceof Player target && data.getTrusted().contains(target.getUniqueId())) {
                        continue;
                    }
                    entity.getWorld().strikeLightning(entity.getLocation());
                }
                count++;
                if (count >= 10) {
                    data.setPrimaryActive(false);
                    data.setPrimarySeconds(20);
                    data.setPrimaryMinutes(1);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, TICKS_PER_SECOND);
    }

    private void runRegenerationSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(15);
        data.setPrimaryMinutes(0);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                applyPotion(player, PotionEffectType.REGENERATION, 2, 3, false, false);
                for (Player nearby : player.getWorld().getPlayers()) {
                    if (nearby.getLocation().distance(player.getLocation()) > 16) {
                        continue;
                    }
                    if (!nearby.equals(player) && data.getTrusted().contains(nearby.getUniqueId())) {
                        applyPotion(player, PotionEffectType.REGENERATION, 2, 3, false, false);
                    }
                }
                count++;
                if (count >= 15) {
                    data.setPrimaryActive(false);
                    data.setPrimarySeconds(20);
                    data.setPrimaryMinutes(1);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, TICKS_PER_SECOND);
    }

    private void runPigSpark(Player player, PlayerData data) {
        data.setPrimaryActive(true);
        data.setPrimarySeconds(1);
        data.setPrimaryMinutes(0);
        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.2f);
        Vector baseDirection = player.getLocation().getDirection().normalize();
        for (int i = 0; i < 3; i++) {
            Vector spread = new Vector(
                (random.nextDouble() - 0.5) * 0.2,
                (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.2
            );
            launchPigProjectile(player, data, baseDirection.clone().add(spread).normalize());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setPrimaryActive(false);
            data.setPrimarySeconds(30);
            data.setPrimaryMinutes(0);
        }, TICKS_PER_SECOND);
    }

    private void launchPigProjectile(Player shooter, PlayerData data, Vector direction) {
        Pig pig = shooter.getWorld().spawn(shooter.getLocation().add(0, 1.2, 0), Pig.class, spawned -> {
            spawned.setBaby();
            spawned.setAI(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(true);
        });
        pig.getPersistentDataContainer().set(pigSparkKey, PersistentDataType.STRING, shooter.getUniqueId().toString());
        pig.setVelocity(direction.multiply(1.6));
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!pig.isValid() || pig.isDead()) {
                    cancel();
                    return;
                }
                if (pig.isOnGround() || ticks >= 60) {
                    detonatePig(pig, shooter, data);
                    cancel();
                    return;
                }
                for (Player target : pig.getWorld().getPlayers()) {
                    if (target.getUniqueId().equals(shooter.getUniqueId())) {
                        continue;
                    }
                    if (data.getTrusted().contains(target.getUniqueId())) {
                        continue;
                    }
                    if (target.getLocation().distanceSquared(pig.getLocation()) <= 1.2) {
                        detonatePig(pig, shooter, data);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void detonatePig(Pig pig, Player shooter, PlayerData data) {
        if (!pig.isValid() || pig.isDead()) {
            return;
        }
        var world = pig.getWorld();
        var location = pig.getLocation();
        pig.remove();
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        for (Player target : world.getPlayers()) {
            if (target.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }
            if (data.getTrusted().contains(target.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distanceSquared(location) <= 4.0) {
                target.damage(4.0, shooter);
            }
        }
    }

    private void hidePlayerFromAll(Player player) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.hidePlayer(this, player);
        }
        invisibilityHidden.add(player.getUniqueId());
    }

    private void revealPlayerToAll(Player player) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.showPlayer(this, player);
        }
        invisibilityHidden.remove(player.getUniqueId());
    }

    private void giveSupportItem(Player player, int support) {
        InfuseItem item = switch (support) {
            case 1 -> InfuseItem.SUPPORT_OCEAN;
            case 2 -> InfuseItem.SUPPORT_FIRE;
            case 3 -> InfuseItem.SUPPORT_EMERALD;
            case 4 -> InfuseItem.SUPPORT_SPEED;
            default -> null;
        };
        if (item != null) {
            player.getInventory().addItem(infuseItems.getItem(item));
        }
    }

    private void givePrimaryItem(Player player, int primary) {
        InfuseItem item = switch (primary) {
            case 1 -> InfuseItem.PRIMARY_STRENGTH;
            case 2 -> InfuseItem.PRIMARY_HEART;
            case 3 -> InfuseItem.PRIMARY_HASTE;
            case 4 -> InfuseItem.PRIMARY_INVISIBILITY;
            case 5 -> InfuseItem.PRIMARY_FEATHER;
            case 6 -> InfuseItem.PRIMARY_FROST;
            case 7 -> InfuseItem.PRIMARY_THUNDER;
            case 8 -> InfuseItem.PRIMARY_REGENERATION;
            case 9 -> InfuseItem.PRIMARY_PIG;
            default -> null;
        };
        if (item != null) {
            player.getInventory().addItem(infuseItems.getItem(item));
        }
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
                data.setSupport(random.nextInt(4) + 1);
                data.setJoined(true);
            }, 5L * TICKS_PER_SECOND);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeTemporaryModifiers(player);
        revealPlayerToAll(player);
        pigHitCounts.remove(player.getUniqueId());
        pigKnockbackApplied.remove(player.getUniqueId());
        PlayerData data = getData(player);
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
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (type.name().startsWith("PRIMARY")) {
            givePrimaryItem(player, data.getPrimary());
            data.setPrimary(primaryFromItem(type));
        } else {
            giveSupportItem(player, data.getSupport());
            data.setSupport(supportFromItem(type));
        }
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, 1)));
        } else {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(item));
        }
    }

    private int primaryFromItem(InfuseItem type) {
        return switch (type) {
            case PRIMARY_STRENGTH -> 1;
            case PRIMARY_HEART -> 2;
            case PRIMARY_HASTE -> 3;
            case PRIMARY_INVISIBILITY -> 4;
            case PRIMARY_FEATHER -> 5;
            case PRIMARY_FROST -> 6;
            case PRIMARY_THUNDER -> 7;
            case PRIMARY_REGENERATION -> 8;
            case PRIMARY_PIG -> 9;
            default -> 0;
        };
    }

    private int supportFromItem(InfuseItem type) {
        return switch (type) {
            case SUPPORT_OCEAN -> 1;
            case SUPPORT_FIRE -> 2;
            case SUPPORT_EMERALD -> 3;
            case SUPPORT_SPEED -> 4;
            default -> 0;
        };
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (data.getControlSet() == 1) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                runPrimaryAbility(player, data);
            } else {
                runSupportAbility(player, data);
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
                runPrimaryAbility(player, data);
            } else if (EnumSet.of(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK).contains(event.getAction())) {
                runSupportAbility(player, data);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (data.getPrimary() == 5 && !player.isSneaking()) {
            Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
            Material below2 = player.getLocation().clone().subtract(0, 2, 0).getBlock().getType();
            Material below3 = player.getLocation().clone().subtract(0, 3, 0).getBlock().getType();
            boolean onLiquid = isWaterOrLava(below) || isWaterOrLava(below2) || isWaterOrLava(below3);
            boolean headLiquid = isWaterOrLava(player.getEyeLocation().getBlock().getType());
            if (onLiquid && !headLiquid) {
                List<org.bukkit.block.Block> blocks = new java.util.ArrayList<>();
                org.bukkit.block.Block center = player.getLocation().getBlock();
                for (int x = -2; x <= 2; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -2; z <= 2; z++) {
                            org.bukkit.block.Block block = center.getRelative(x, y, z);
                            if (block.getType() == Material.WATER) {
                                blocks.add(block);
                                block.setType(Material.ICE);
                            } else if (block.getType() == Material.LAVA) {
                                blocks.add(block);
                                block.setType(Material.OBSIDIAN);
                            }
                        }
                    }
                }
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    for (org.bukkit.block.Block block : blocks) {
                        if (block.getLocation().equals(player.getLocation().clone().subtract(0, 1, 0).getBlock().getLocation())) {
                            waitForPlayerLeave(player, block);
                        } else {
                            revertBlock(block);
                        }
                    }
                }, 3L * TICKS_PER_SECOND);
            }
        }
    }

    private void waitForPlayerLeave(Player player, org.bukkit.block.Block block) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    revertBlock(block);
                    cancel();
                    return;
                }
                org.bukkit.block.Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                if (!below.getLocation().equals(block.getLocation())) {
                    revertBlock(block);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void revertBlock(org.bukkit.block.Block block) {
        if (block.getType() == Material.ICE) {
            block.setType(Material.WATER);
        } else if (block.getType() == Material.OBSIDIAN) {
            block.setType(Material.LAVA);
        }
    }

    private boolean isWaterOrLava(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (data.getPrimary() == 5 && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSupportFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (data.getSupport() == 2 && player.getFireTicks() > 0) {
            event.setCancelled(true);
            if (data.isFireSparkActive()) {
                applyPotion(player, PotionEffectType.REGENERATION, 1, 2, false, false);
            }
            applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER, FIRE_ATTACK_DAMAGE, 20);
            applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 1, 1, false, true);
        }
        if (data.getPrimary() == 7 && event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (data.getPrimary() == 6 && data.isFrostSparkActive() && event.getEntity() instanceof LivingEntity victim) {
            if (victim.getFreezeTicks() >= TICKS_PER_SECOND) {
                event.setDamage(event.getDamage() + 3);
            }
            victim.setFreezeTicks(TICKS_PER_SECOND * 30);
        }
        if (data.getPrimary() == 7 && event.isCritical()) {
            Bukkit.getScheduler().runTask(this, () -> event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation()));
        }
        if (data.getPrimary() == 8 && event.isCritical()) {
            applyPotion(player, PotionEffectType.REGENERATION, 2, 4, false, false);
        }
    }

    @EventHandler
    public void onPigEffectHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        PlayerData data = getData(player);
        if (data.getPrimary() != 9) {
            return;
        }
        int hits = pigHitCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        if (hits >= 5) {
            pigHitCounts.put(player.getUniqueId(), 0);
            applyPotion(player, PotionEffectType.SPEED, 3, 5, false, false);
        } else {
            pigHitCounts.put(player.getUniqueId(), hits);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (data.getPrimary() != 3) {
            return;
        }
        Material type = event.getBlock().getType();
        Set<Material> extraDrops = Set.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.AMETHYST_CLUSTER
        );
        if (!extraDrops.contains(type)) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getItemMeta() != null && tool.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return;
        }
        event.getBlock().getDrops(tool, player).forEach(drop -> player.getWorld().dropItemNaturally(event.getBlock().getLocation(), drop));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (data.getSupport() != 0) {
            ItemStack supportItem = infuseItems.getItem(switch (data.getSupport()) {
                case 1 -> InfuseItem.SUPPORT_OCEAN;
                case 2 -> InfuseItem.SUPPORT_FIRE;
                case 3 -> InfuseItem.SUPPORT_EMERALD;
                case 4 -> InfuseItem.SUPPORT_SPEED;
                default -> InfuseItem.SUPPORT_OCEAN;
            });
            player.getWorld().dropItemNaturally(player.getLocation(), supportItem);
            data.setSupport(0);
        }
        if (data.getPrimary() != 0) {
            ItemStack primaryItem = infuseItems.getItem(switch (data.getPrimary()) {
                case 1 -> InfuseItem.PRIMARY_STRENGTH;
                case 2 -> InfuseItem.PRIMARY_HEART;
                case 3 -> InfuseItem.PRIMARY_HASTE;
                case 4 -> InfuseItem.PRIMARY_INVISIBILITY;
                case 5 -> InfuseItem.PRIMARY_FEATHER;
                case 6 -> InfuseItem.PRIMARY_FROST;
                case 7 -> InfuseItem.PRIMARY_THUNDER;
                case 8 -> InfuseItem.PRIMARY_REGENERATION;
                case 9 -> InfuseItem.PRIMARY_PIG;
                default -> InfuseItem.PRIMARY_STRENGTH;
            });
            player.getWorld().dropItemNaturally(player.getLocation(), primaryItem);
            data.setPrimary(0);
        }
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
                return List.of("primary", "support");
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("spark") && args[1].equalsIgnoreCase("equip")) {
                if (args[2].equalsIgnoreCase("primary")) {
                    return List.of("empty", "strength", "heart", "haste", "invisibility", "feather", "frost", "thunder", "regeneration", "pig");
                }
                if (args[2].equalsIgnoreCase("support")) {
                    return List.of("empty", "ocean", "fire", "emerald", "speed");
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
