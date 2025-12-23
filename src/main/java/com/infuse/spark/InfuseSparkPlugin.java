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
    private static final double PIG_KNOCKBACK_REDUCTION = 0.08;
    private static final double PIGLIN_MARK_DAMAGE = 1.5;
    private static final double PIGLIN_BLOODMARK_DAMAGE = 3.0;
    private static final int PIGLIN_MARK_WINDOW_SECONDS = 2;
    private static final int PIGLIN_BLOODMARK_WINDOW_SECONDS = 3;
    private static final int PIGLIN_SPARK_DURATION_SECONDS = 10;
    private static final int PIGLIN_SPARK_COOLDOWN_SECONDS = 60;
    private static final int PIG_SPARK_DURATION_SECONDS = 40;
    private static final int PIG_SPARK_COOLDOWN_SECONDS = 60;

    private static final UUID STRENGTH_MODIFIER = UUID.fromString("f7d5d5c4-5d1b-4b92-9ee4-0c8c293b5f5a");
    private static final UUID STRENGTH_SPARK_MODIFIER = UUID.fromString("6b0e9d41-90d8-447f-b6ab-3000f84509ee");
    private static final UUID OCEAN_ATTACK_MODIFIER = UUID.fromString("1a91d0a7-4f22-4a7a-9dfe-f2b7d8b5c934");
    private static final UUID FIRE_ATTACK_MODIFIER = UUID.fromString("b7db23cc-fba1-4f7a-8896-9cf813f6a47b");
    private static final UUID HEART_EQUIP_MODIFIER = UUID.fromString("1f57d91f-1f48-4c91-bbb5-7e8d7a4b59a4");
    private static final UUID HEART_SPARK_MODIFIER = UUID.fromString("8f7625b1-2f43-4a28-9e1c-c5c1c4e5c169");

    private record EffectSelection(EffectGroup group, int effectId) {
    }

    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, Integer> pigHitCounts = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> piglinMarks = new HashMap<>();
    private final Map<UUID, Integer> piglinGlowCounts = new HashMap<>();
    private final Set<UUID> heartEquipApplied = new HashSet<>();
    private final Set<UUID> invisibilityHidden = new HashSet<>();
    private final Random random = new Random();

    private PlayerDataStore dataStore;
    private InfuseItems infuseItems;
    private NamespacedKey infuseItemKey;
    private HttpServer resourcePackServer;
    private String resourcePackUrl;
    private byte[] resourcePackHash;

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

    private int getSlotEffect(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimary() : data.getSupport();
    }

    private EffectGroup getSlotGroup(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimaryGroup() : data.getSupportGroup();
    }

    private boolean isSlotActive(PlayerData data, int slot) {
        return slot == 1 ? data.isPrimaryActive() : data.isSupportActive();
    }

    private String getSlotShow(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimaryShow() : data.getSupportShow();
    }

    private void setSlotEffect(PlayerData data, int slot, EffectGroup group, int effectId) {
        if (slot == 1) {
            data.setPrimary(effectId);
            data.setPrimaryGroup(group);
        } else {
            data.setSupport(effectId);
            data.setSupportGroup(group);
        }
    }

    private void setSlotActive(PlayerData data, int slot, boolean active) {
        if (slot == 1) {
            data.setPrimaryActive(active);
        } else {
            data.setSupportActive(active);
        }
    }

    private void setSlotCooldown(PlayerData data, int slot, int minutes, int seconds) {
        if (slot == 1) {
            data.setPrimaryMinutes(minutes);
            data.setPrimarySeconds(seconds);
        } else {
            data.setSupportMinutes(minutes);
            data.setSupportSeconds(seconds);
        }
    }

    private void setSlotSeconds(PlayerData data, int slot, int seconds) {
        if (slot == 1) {
            data.setPrimarySeconds(seconds);
        } else {
            data.setSupportSeconds(seconds);
        }
    }

    private void setSlotMinutes(PlayerData data, int slot, int minutes) {
        if (slot == 1) {
            data.setPrimaryMinutes(minutes);
        } else {
            data.setSupportMinutes(minutes);
        }
    }

    private boolean hasEffect(PlayerData data, EffectGroup group, int effectId) {
        return (data.getPrimaryGroup() == group && data.getPrimary() == effectId)
            || (data.getSupportGroup() == group && data.getSupport() == effectId);
    }

    private boolean isEffectActive(PlayerData data, EffectGroup group, int effectId) {
        return (data.getPrimaryGroup() == group && data.getPrimary() == effectId && data.isPrimaryActive())
            || (data.getSupportGroup() == group && data.getSupport() == effectId && data.isSupportActive());
    }

    private int getActiveSlotForEffect(PlayerData data, EffectGroup group, int effectId) {
        if (data.getPrimaryGroup() == group && data.getPrimary() == effectId && data.isPrimaryActive()) {
            return 1;
        }
        if (data.getSupportGroup() == group && data.getSupport() == effectId && data.isSupportActive()) {
            return 2;
        }
        return 0;
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
        updateSlotIconsAndPassives(player, data, 1);
        updateSlotIconsAndPassives(player, data, 2);

        if (!hasEffect(data, EffectGroup.PRIMARY, 2) && heartEquipApplied.contains(player.getUniqueId())) {
            removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER);
            heartEquipApplied.remove(player.getUniqueId());
        }

        if (!hasEffect(data, EffectGroup.PRIMARY, 10)) {
            pigHitCounts.remove(player.getUniqueId());
            removePigKnockback(player);
        }

        if (!hasEffect(data, EffectGroup.PRIMARY, 9)) {
            clearPiglinAttackerState(player.getUniqueId());
        }
    }

    private void updateSlotIconsAndPassives(Player player, PlayerData data, int slot) {
        boolean active = isSlotActive(data, slot);
        EffectGroup group = getSlotGroup(data, slot);
        int effect = getSlotEffect(data, slot);

        if (group == EffectGroup.PRIMARY) {
            if (active) {
                switch (effect) {
                    case 0 -> setSlotActionBar(data, slot, "\uE001");
                    case 1 -> {
                        setSlotActionBar(data, slot, "\uE014");
                        applyStrengthEquipped(player, data);
                    }
                    case 2 -> {
                        setSlotActionBar(data, slot, "\uE015");
                        applyHeartEquipped(player);
                    }
                    case 3 -> {
                        setSlotActionBar(data, slot, "\uE016");
                        applyHasteEquipped(player);
                    }
                    case 4 -> {
                        setSlotActionBar(data, slot, "\uE017");
                        applyInvisibilityEquipped(player);
                    }
                    case 5 -> setSlotActionBar(data, slot, "\uE018");
                    case 6 -> {
                        setSlotActionBar(data, slot, "\uE019");
                        applyFrostEquipped(player);
                    }
                    case 7 -> setSlotActionBar(data, slot, "\uE020");
                    case 8 -> setSlotActionBar(data, slot, "\uE021");
                    case 9 -> setSlotActionBar(data, slot, "\uE029");
                    case 10 -> {
                        setSlotActionBar(data, slot, "\uE027");
                        applyPigEquipped(player);
                    }
                    default -> {
                    }
                }
            } else {
                switch (effect) {
                    case 0 -> setSlotActionBar(data, slot, "\uE001");
                    case 1 -> {
                        setSlotActionBar(data, slot, "\uE002");
                        applyStrengthEquipped(player, data);
                    }
                    case 2 -> {
                        setSlotActionBar(data, slot, "\uE003");
                        applyHeartEquipped(player);
                    }
                    case 3 -> {
                        setSlotActionBar(data, slot, "\uE004");
                        applyHasteEquipped(player);
                    }
                    case 4 -> {
                        setSlotActionBar(data, slot, "\uE005");
                        applyInvisibilityEquipped(player);
                    }
                    case 5 -> setSlotActionBar(data, slot, "\uE006");
                    case 6 -> {
                        setSlotActionBar(data, slot, "\uE007");
                        applyFrostEquipped(player);
                    }
                    case 7 -> setSlotActionBar(data, slot, "\uE008");
                    case 8 -> setSlotActionBar(data, slot, "\uE009");
                    case 9 -> setSlotActionBar(data, slot, "\uE028");
                    case 10 -> {
                        setSlotActionBar(data, slot, "\uE026");
                        applyPigEquipped(player);
                    }
                    default -> {
                    }
                }
            }

            if (slot == 1) {
                data.setPrimaryColorCode(getPrimaryColorCode(effect, active));
            }
        } else {
            if (active) {
                switch (effect) {
                    case 0 -> setSlotActionBar(data, slot, "\uE022&f&l");
                    case 1 -> {
                        setSlotActionBar(data, slot, "\uE022&9&l");
                        applyOceanEquipped(player);
                    }
                    case 2 -> setSlotActionBar(data, slot, "\uE023&6&l");
                    case 3 -> {
                        setSlotActionBar(data, slot, "\uE024&a&l");
                        applyEmeraldEquipped(player);
                    }
                    case 4 -> {
                        setSlotActionBar(data, slot, "\uE025&e&l");
                        applySpeedEquipped(player);
                    }
                    default -> {
                    }
                }
            } else {
                switch (effect) {
                    case 0 -> setSlotActionBar(data, slot, "\uE001&f&l");
                    case 1 -> {
                        setSlotActionBar(data, slot, "\uE010&f&l");
                        applyOceanEquipped(player);
                    }
                    case 2 -> setSlotActionBar(data, slot, "\uE011&f&l");
                    case 3 -> {
                        setSlotActionBar(data, slot, "\uE012&f&l");
                        applyEmeraldEquipped(player);
                    }
                    case 4 -> {
                        setSlotActionBar(data, slot, "\uE013&f&l");
                        applySpeedEquipped(player);
                    }
                    default -> {
                    }
                }
            }
            if (slot == 1) {
                data.setPrimaryColorCode("&f&l");
            }
        }
    }

    private void setSlotActionBar(PlayerData data, int slot, String value) {
        if (slot == 1) {
            data.setActionBarPrimary(value);
        } else {
            data.setActionBarSupport(value);
        }
    }

    private String getPrimaryColorCode(int effect, boolean active) {
        if (!active) {
            return "&f&l";
        }
        return switch (effect) {
            case 1 -> "&4&l";
            case 2 -> "&5&l";
            case 3 -> "&6&l";
            case 4 -> "&5&l";
            case 5 -> "&2&l";
            case 6 -> "&b&l";
            case 7 -> "&9&l";
            case 8 -> "&c&l";
            case 9 -> "&6&l";
            case 10 -> "&d&l";
            default -> "&f&l";
        };
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
        applyAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER, PIG_KNOCKBACK_REDUCTION);
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
    }

    private void handlePiglinMarkAttack(Player attacker, PlayerData data, EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (data.getTrusted().contains(target.getUniqueId())) {
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();
        Map<UUID, Long> marks = piglinMarks.computeIfAbsent(attackerId, id -> new HashMap<>());
        Long markExpires = marks.get(targetId);
        if (markExpires != null) {
            if (markExpires > now) {
                return;
            }
            removePiglinMark(attackerId, targetId);
        }
        Long windowExpires = piglinMarkWindows.get(attackerId);
        if (windowExpires == null || windowExpires <= now) {
            return;
        }
        int windowSeconds = data.isPiglinSparkActive() ? PIGLIN_BLOODMARK_WINDOW_SECONDS : PIGLIN_MARK_WINDOW_SECONDS;
        double bonusDamage = data.isPiglinSparkActive() ? PIGLIN_BLOODMARK_DAMAGE : PIGLIN_MARK_DAMAGE;
        event.setDamage(event.getDamage() + bonusDamage);
        addPiglinMark(attackerId, target, windowSeconds);
    }

    private void addPiglinMark(UUID attackerId, Player target, int durationSeconds) {
        Map<UUID, Long> marks = piglinMarks.computeIfAbsent(attackerId, id -> new HashMap<>());
        UUID targetId = target.getUniqueId();
        if (marks.containsKey(targetId)) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        marks.put(targetId, expiresAt);
        adjustPiglinGlow(targetId, 1);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Map<UUID, Long> current = piglinMarks.get(attackerId);
            if (current == null) {
                return;
            }
            Long currentExpires = current.get(targetId);
            if (currentExpires != null && currentExpires <= System.currentTimeMillis()) {
                removePiglinMark(attackerId, targetId);
            }
        }, durationSeconds * TICKS_PER_SECOND);
    }

    private void removePiglinMark(UUID attackerId, UUID targetId) {
        Map<UUID, Long> marks = piglinMarks.get(attackerId);
        if (marks == null) {
            return;
        }
        if (marks.remove(targetId) != null) {
            adjustPiglinGlow(targetId, -1);
        }
        if (marks.isEmpty()) {
            piglinMarks.remove(attackerId);
        }
    }

    private void adjustPiglinGlow(UUID targetId, int delta) {
        int next = piglinGlowCounts.getOrDefault(targetId, 0) + delta;
        if (next <= 0) {
            piglinGlowCounts.remove(targetId);
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.setGlowing(false);
            }
            return;
        }
        piglinGlowCounts.put(targetId, next);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            target.setGlowing(true);
        }
    }

    private void clearPiglinAttackerState(UUID attackerId) {
        piglinMarkWindows.remove(attackerId);
        Map<UUID, Long> marks = piglinMarks.remove(attackerId);
        if (marks == null) {
            return;
        }
        for (UUID targetId : marks.keySet()) {
            adjustPiglinGlow(targetId, -1);
        }
    }

    private void clearPiglinTargetState(UUID targetId) {
        for (UUID attackerId : Set.copyOf(piglinMarks.keySet())) {
            Map<UUID, Long> marks = piglinMarks.get(attackerId);
            if (marks == null || !marks.containsKey(targetId)) {
                continue;
            }
            removePiglinMark(attackerId, targetId);
        }
    }

    private void triggerPigSparkHeal(Player player, PlayerData data, int slot) {
        if (!data.isPigSparkPrimed()) {
            return;
        }
        data.setPigSparkPrimed(false);
        setSlotActive(data, slot, false);
        setSlotCooldown(data, slot, PIG_SPARK_COOLDOWN_SECONDS / 60, PIG_SPARK_COOLDOWN_SECONDS % 60);
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxHealth != null ? maxHealth.getValue() : 20.0;
            player.setHealth(Math.min(max, player.getHealth() + 8.0));
        });
    }

    private void handlePiglinMarkAttack(Player attacker, PlayerData data, EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (data.getTrusted().contains(target.getUniqueId())) {
            return;
        }
        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();
        long now = System.currentTimeMillis();
        Map<UUID, Long> marks = piglinMarks.computeIfAbsent(attackerId, id -> new HashMap<>());
        Long markExpires = marks.get(targetId);
        if (markExpires == null) {
            return;
        }
        if (markExpires <= now) {
            removePiglinMark(attackerId, targetId);
            return;
        }
        double bonusDamage = data.isPiglinSparkActive() ? PIGLIN_BLOODMARK_DAMAGE : PIGLIN_MARK_DAMAGE;
        event.setDamage(event.getDamage() + bonusDamage);
        removePiglinMark(attackerId, targetId);
    }

    private void addPiglinMark(UUID attackerId, Player target, int durationSeconds) {
        Map<UUID, Long> marks = piglinMarks.computeIfAbsent(attackerId, id -> new HashMap<>());
        UUID targetId = target.getUniqueId();
        if (marks.containsKey(targetId)) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        marks.put(targetId, expiresAt);
        adjustPiglinGlow(targetId, 1);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Map<UUID, Long> current = piglinMarks.get(attackerId);
            if (current == null) {
                return;
            }
            Long currentExpires = current.get(targetId);
            if (currentExpires != null && currentExpires <= System.currentTimeMillis()) {
                removePiglinMark(attackerId, targetId);
            }
        }, durationSeconds * TICKS_PER_SECOND);
    }

    private void removePiglinMark(UUID attackerId, UUID targetId) {
        Map<UUID, Long> marks = piglinMarks.get(attackerId);
        if (marks == null) {
            return;
        }
        if (marks.remove(targetId) != null) {
            adjustPiglinGlow(targetId, -1);
        }
        if (marks.isEmpty()) {
            piglinMarks.remove(attackerId);
        }
    }

    private void adjustPiglinGlow(UUID targetId, int delta) {
        int next = piglinGlowCounts.getOrDefault(targetId, 0) + delta;
        if (next <= 0) {
            piglinGlowCounts.remove(targetId);
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                target.setGlowing(false);
            }
            return;
        }
        piglinGlowCounts.put(targetId, next);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            target.setGlowing(true);
        }
    }

    private void clearPiglinAttackerState(UUID attackerId) {
        Map<UUID, Long> marks = piglinMarks.remove(attackerId);
        if (marks == null) {
            return;
        }
        for (UUID targetId : marks.keySet()) {
            adjustPiglinGlow(targetId, -1);
        }
    }

    private void clearPiglinTargetState(UUID targetId) {
        for (UUID attackerId : Set.copyOf(piglinMarks.keySet())) {
            Map<UUID, Long> marks = piglinMarks.get(attackerId);
            if (marks == null || !marks.containsKey(targetId)) {
                continue;
            }
            removePiglinMark(attackerId, targetId);
        }
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
                    EffectSelection selection = parseEffectType(type);
                    setSlotEffect(data, slot, selection.group, selection.effectId);
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

    private EffectSelection parseEffectType(String type) {
        return switch (type) {
            case "strength" -> new EffectSelection(EffectGroup.PRIMARY, 1);
            case "heart" -> new EffectSelection(EffectGroup.PRIMARY, 2);
            case "haste" -> new EffectSelection(EffectGroup.PRIMARY, 3);
            case "invisibility" -> new EffectSelection(EffectGroup.PRIMARY, 4);
            case "feather" -> new EffectSelection(EffectGroup.PRIMARY, 5);
            case "frost" -> new EffectSelection(EffectGroup.PRIMARY, 6);
            case "thunder" -> new EffectSelection(EffectGroup.PRIMARY, 7);
            case "regeneration" -> new EffectSelection(EffectGroup.PRIMARY, 8);
            case "piglin" -> new EffectSelection(EffectGroup.PRIMARY, 9);
            case "pig" -> new EffectSelection(EffectGroup.PRIMARY, 10);
            case "ocean" -> new EffectSelection(EffectGroup.SUPPORT, 1);
            case "fire" -> new EffectSelection(EffectGroup.SUPPORT, 2);
            case "emerald" -> new EffectSelection(EffectGroup.SUPPORT, 3);
            case "speed" -> new EffectSelection(EffectGroup.SUPPORT, 4);
            default -> new EffectSelection(EffectGroup.PRIMARY, 0);
        };
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
        if (READY_SPACES.equals(getSlotShow(data, slot))) {
            int effect = getSlotEffect(data, slot);
            if (effect != 0) {
                giveEffectItem(player, getSlotGroup(data, slot), effect);
                setSlotEffect(data, slot, getSlotGroup(data, slot), 0);
            }
        } else {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "INFUSE " + ChatColor.GRAY + "" + ChatColor.BOLD + ">> " + ChatColor.WHITE + "Your Cooldown must be depleted to drain out");
        }
        return true;
    }

    private void runSlotAbility(Player player, PlayerData data, int slot) {
        int effect = getSlotEffect(data, slot);
        if (effect == 0) {
            return;
        }
        if (!READY_SPACES.equals(getSlotShow(data, slot))) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        EffectGroup group = getSlotGroup(data, slot);
        if (group == EffectGroup.SUPPORT) {
            switch (effect) {
                case 1 -> runOceanSpark(player, data, slot);
                case 2 -> runFireSpark(player, data, slot);
                case 3 -> runEmeraldSpark(player, data, slot);
                case 4 -> runSpeedSpark(player, data, slot);
                default -> {
                }
            }
            return;
        }
        switch (effect) {
            case 1 -> runStrengthSpark(player, data, slot);
            case 2 -> runHeartSpark(player, data, slot);
            case 3 -> runHasteSpark(player, data, slot);
            case 4 -> runInvisibilitySpark(player, data, slot);
            case 5 -> runFeatherSpark(player, data, slot);
            case 6 -> runFrostSpark(player, data, slot);
            case 7 -> runThunderSpark(player, data, slot);
            case 8 -> runRegenerationSpark(player, data, slot);
            case 9 -> runPiglinSpark(player, data, slot);
            case 10 -> runPigSpark(player, data, slot);
            default -> {
            }
        }
    }

    private void runOceanSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 30);
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
                    setSlotActive(data, slot, false);
                    setSlotCooldown(data, slot, 1, 0);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void runFireSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 30);
        data.setFireSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setFireSparkActive(false);
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 1, 0);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runEmeraldSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 1, 30);
        applyPotion(player, PotionEffectType.HERO_OF_THE_VILLAGE, 200, 90, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 5, 0);
        }, 90L * TICKS_PER_SECOND);
    }

    private void runSpeedSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 1);
        Vector direction = player.getLocation().getDirection().normalize().multiply(2);
        player.setVelocity(direction);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 0, 15);
        }, TICKS_PER_SECOND);
    }

    private void runStrengthSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 30);
        data.setStrengthSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            data.setStrengthSparkActive(false);
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 2, 0);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runHeartSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 30);
        applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER, 10.0);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 1, 0);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runHasteSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 45);
        applyPotion(player, PotionEffectType.HASTE, 255, 45, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 1, 15);
        }, 45L * TICKS_PER_SECOND);
    }

    private void runInvisibilitySpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 20);
        hidePlayerFromAll(player);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            revealPlayerToAll(player);
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 0, 45);
        }, 20L * TICKS_PER_SECOND);
    }

    private void runFeatherSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 2);
        applyPotion(player, PotionEffectType.LEVITATION, 30, 2, false, false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 0, 30);
        }, 2L * TICKS_PER_SECOND);
    }

    private void runFrostSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 30);
        data.setFrostSparkActive(true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, 1, 0);
        }, 30L * TICKS_PER_SECOND);
    }

    private void runThunderSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 10);
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
                    setSlotActive(data, slot, false);
                    setSlotCooldown(data, slot, 1, 20);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, TICKS_PER_SECOND);
    }

    private void runRegenerationSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, 15);
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
                    setSlotActive(data, slot, false);
                    setSlotCooldown(data, slot, 1, 20);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, TICKS_PER_SECOND);
    }

    private void runPiglinSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, PIGLIN_SPARK_DURATION_SECONDS);
        data.setPiglinSparkActive(true);
        player.playSound(player.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0f, 0.9f);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.BLOCK,
                    player.getLocation().add(0, 0.1, 0),
                    12,
                    0.4,
                    0.1,
                    0.4,
                    0.02,
                    Material.NETHERRACK.createBlockData()
                );
                ticks += 5;
                if (ticks >= PIGLIN_SPARK_DURATION_SECONDS * TICKS_PER_SECOND) {
                    data.setPiglinSparkActive(false);
                    setSlotActive(data, slot, false);
                    setSlotCooldown(data, slot, PIGLIN_SPARK_COOLDOWN_SECONDS / 60, PIGLIN_SPARK_COOLDOWN_SECONDS % 60);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    private void runPigSpark(Player player, PlayerData data, int slot) {
        setSlotActive(data, slot, true);
        setSlotCooldown(data, slot, 0, PIG_SPARK_DURATION_SECONDS);
        data.setPigSparkPrimed(true);
        player.playSound(player.getLocation(), Sound.ENTITY_PIG_SADDLE, 1.0f, 1.0f);
        int healthThreshold = 8;
        if (player.getHealth() <= healthThreshold) {
            triggerPigSparkHeal(player, data, slot);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isSlotActive(data, slot) || !data.isPigSparkPrimed()) {
                return;
            }
            data.setPigSparkPrimed(false);
            setSlotActive(data, slot, false);
            setSlotCooldown(data, slot, PIG_SPARK_COOLDOWN_SECONDS / 60, PIG_SPARK_COOLDOWN_SECONDS % 60);
        }, PIG_SPARK_DURATION_SECONDS * TICKS_PER_SECOND);
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

    private void giveEffectItem(Player player, EffectGroup group, int effect) {
        InfuseItem item = getInfuseItem(group, effect);
        if (item != null) {
            player.getInventory().addItem(infuseItems.getItem(item));
        }
    }

    private void dropEffectOnDeath(Player player, PlayerData data, int slot) {
        int effect = getSlotEffect(data, slot);
        if (effect == 0) {
            return;
        }
        InfuseItem item = getInfuseItem(getSlotGroup(data, slot), effect);
        if (item != null) {
            ItemStack stack = infuseItems.getItem(item);
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        setSlotEffect(data, slot, getSlotGroup(data, slot), 0);
    }

    private InfuseItem getInfuseItem(EffectGroup group, int effect) {
        if (group == EffectGroup.SUPPORT) {
            return switch (effect) {
                case 1 -> InfuseItem.SUPPORT_OCEAN;
                case 2 -> InfuseItem.SUPPORT_FIRE;
                case 3 -> InfuseItem.SUPPORT_EMERALD;
                case 4 -> InfuseItem.SUPPORT_SPEED;
                default -> null;
            };
        }
        return switch (effect) {
            case 1 -> InfuseItem.PRIMARY_STRENGTH;
            case 2 -> InfuseItem.PRIMARY_HEART;
            case 3 -> InfuseItem.PRIMARY_HASTE;
            case 4 -> InfuseItem.PRIMARY_INVISIBILITY;
            case 5 -> InfuseItem.PRIMARY_FEATHER;
            case 6 -> InfuseItem.PRIMARY_FROST;
            case 7 -> InfuseItem.PRIMARY_THUNDER;
            case 8 -> InfuseItem.PRIMARY_REGENERATION;
            case 9 -> InfuseItem.PRIMARY_PIGLIN;
            case 10 -> InfuseItem.PRIMARY_PIG;
            default -> null;
        };
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
                setSlotEffect(data, 2, EffectGroup.SUPPORT, random.nextInt(4) + 1);
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
        clearPiglinAttackerState(player.getUniqueId());
        clearPiglinTargetState(player.getUniqueId());
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
        EffectSelection selection = effectFromItem(type);
        int slot = getSlotEffect(data, 1) == 0 ? 1 : (getSlotEffect(data, 2) == 0 ? 2 : 2);
        int existingEffect = getSlotEffect(data, slot);
        if (existingEffect != 0) {
            giveEffectItem(player, getSlotGroup(data, slot), existingEffect);
        }
        setSlotEffect(data, slot, selection.group, selection.effectId);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, 1)));
        } else {
            Bukkit.getScheduler().runTask(this, () -> player.getInventory().removeItem(item));
        }
    }

    private EffectSelection effectFromItem(InfuseItem type) {
        return switch (type) {
            case PRIMARY_STRENGTH -> new EffectSelection(EffectGroup.PRIMARY, 1);
            case PRIMARY_HEART -> new EffectSelection(EffectGroup.PRIMARY, 2);
            case PRIMARY_HASTE -> new EffectSelection(EffectGroup.PRIMARY, 3);
            case PRIMARY_INVISIBILITY -> new EffectSelection(EffectGroup.PRIMARY, 4);
            case PRIMARY_FEATHER -> new EffectSelection(EffectGroup.PRIMARY, 5);
            case PRIMARY_FROST -> new EffectSelection(EffectGroup.PRIMARY, 6);
            case PRIMARY_THUNDER -> new EffectSelection(EffectGroup.PRIMARY, 7);
            case PRIMARY_REGENERATION -> new EffectSelection(EffectGroup.PRIMARY, 8);
            case PRIMARY_PIGLIN -> new EffectSelection(EffectGroup.PRIMARY, 9);
            case PRIMARY_PIG -> new EffectSelection(EffectGroup.PRIMARY, 10);
            case SUPPORT_OCEAN -> new EffectSelection(EffectGroup.SUPPORT, 1);
            case SUPPORT_FIRE -> new EffectSelection(EffectGroup.SUPPORT, 2);
            case SUPPORT_EMERALD -> new EffectSelection(EffectGroup.SUPPORT, 3);
            case SUPPORT_SPEED -> new EffectSelection(EffectGroup.SUPPORT, 4);
        };
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
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (hasEffect(data, EffectGroup.PRIMARY, 5) && !player.isSneaking()) {
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
        if (hasEffect(data, EffectGroup.PRIMARY, 5) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSupportFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (hasEffect(data, EffectGroup.SUPPORT, 2) && player.getFireTicks() > 0) {
            event.setCancelled(true);
            if (data.isFireSparkActive()) {
                applyPotion(player, PotionEffectType.REGENERATION, 1, 2, false, false);
            }
            applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER, FIRE_ATTACK_DAMAGE, 20);
            applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 1, 1, false, true);
        }
        if (hasEffect(data, EffectGroup.PRIMARY, 7) && event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        PlayerData data = getData(player);
        if (hasEffect(data, EffectGroup.PRIMARY, 6) && data.isFrostSparkActive() && event.getEntity() instanceof LivingEntity victim) {
            if (victim.getFreezeTicks() >= TICKS_PER_SECOND) {
                event.setDamage(event.getDamage() + 3);
            }
            victim.setFreezeTicks(TICKS_PER_SECOND * 30);
        }
        if (hasEffect(data, EffectGroup.PRIMARY, 7) && event.isCritical()) {
            Bukkit.getScheduler().runTask(this, () -> event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation()));
        }
        if (hasEffect(data, EffectGroup.PRIMARY, 8) && event.isCritical()) {
            applyPotion(player, PotionEffectType.REGENERATION, 2, 4, false, false);
        }
        if (hasEffect(data, EffectGroup.PRIMARY, 9)) {
            handlePiglinMarkAttack(player, data, event);
        }
    }

    @EventHandler
    public void onPiglinEffectHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        PlayerData data = getData(player);
        if (!hasEffect(data, EffectGroup.PRIMARY, 9)) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (data.getTrusted().contains(damager.getUniqueId())) {
            return;
        }
        int windowSeconds = data.isPiglinSparkActive() ? PIGLIN_BLOODMARK_WINDOW_SECONDS : PIGLIN_MARK_WINDOW_SECONDS;
        addPiglinMark(player.getUniqueId(), damager, windowSeconds);
    }

    @EventHandler
    public void onPigSparkLowHealth(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        PlayerData data = getData(player);
        if (!hasEffect(data, EffectGroup.PRIMARY, 10) || !isEffectActive(data, EffectGroup.PRIMARY, 10) || !data.isPigSparkPrimed()) {
            return;
        }
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 8.0) {
            return;
        }
        if (finalHealth <= 0) {
            event.setDamage(Math.max(0.0, player.getHealth() - 1.0));
        }
        int slot = getActiveSlotForEffect(data, EffectGroup.PRIMARY, 10);
        if (slot == 0) {
            return;
        }
        triggerPigSparkHeal(player, data, slot);
    }

    @EventHandler
    public void onPiglinEffectHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        PlayerData data = getData(player);
        if (!hasEffect(data, EffectGroup.PRIMARY, 10)) {
            return;
        }
        int hits = pigHitCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        if (hits >= 5) {
            pigHitCounts.put(player.getUniqueId(), 0);
            applyPotion(player, PotionEffectType.SPEED, 3, 3, false, false);
        } else {
            pigHitCounts.put(player.getUniqueId(), hits);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerData data = getData(player);
        if (!hasEffect(data, EffectGroup.PRIMARY, 3)) {
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
        dropEffectOnDeath(player, data, 2);
        dropEffectOnDeath(player, data, 1);
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
                        "pig", "piglin", "ocean", "fire", "emerald", "speed");
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
