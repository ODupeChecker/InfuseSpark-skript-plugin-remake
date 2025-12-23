package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

public class FrostInfuse extends BaseInfuse {
    public FrostInfuse() {
        super(EffectGroup.PRIMARY, 6, "frost", InfuseItem.PRIMARY_FROST);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        applyFrostEquipped(player, context);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    private void applyFrostEquipped(Player player, InfuseContext context) {
        boolean hasIce = false;
        boolean hasSnow = false;
        int radius = getInt(context, PASSIVE_SECTION, "scan-radius-xz", 0);
        int radiusY = getInt(context, PASSIVE_SECTION, "scan-radius-y", 0);
        java.util.Set<Material> iceMaterials = getMaterials(context, PASSIVE_SECTION, "ice-materials");
        java.util.Set<Material> snowMaterials = getMaterials(context, PASSIVE_SECTION, "snow-materials");
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material material = player.getLocation().clone().add(x, y, z).getBlock().getType();
                    if (iceMaterials.contains(material)) {
                        hasIce = true;
                    }
                    if (snowMaterials.contains(material)) {
                        hasSnow = true;
                    }
                }
            }
        }
        if (hasIce) {
            int level = getInt(context, PASSIVE_SECTION, "ice-speed-level", 0);
            int durationSeconds = getInt(context, PASSIVE_SECTION, "ice-speed-duration-seconds", 0);
            boolean particles = getBoolean(context, PASSIVE_SECTION, "ice-speed-particles", false);
            boolean icon = getBoolean(context, PASSIVE_SECTION, "ice-speed-icon", false);
            context.applyPotion(player, PotionEffectType.SPEED, level, durationSeconds, particles, icon);
        } else if (hasSnow) {
            int level = getInt(context, PASSIVE_SECTION, "snow-speed-level", 0);
            int durationSeconds = getInt(context, PASSIVE_SECTION, "snow-speed-duration-seconds", 0);
            boolean particles = getBoolean(context, PASSIVE_SECTION, "snow-speed-particles", false);
            boolean icon = getBoolean(context, PASSIVE_SECTION, "snow-speed-icon", false);
            context.applyPotion(player, PotionEffectType.SPEED, level, durationSeconds, particles, icon);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        data.setFrostSparkActive(true);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 6) && data.isFrostSparkActive() && event.getEntity() instanceof LivingEntity victim) {
            int bonusThresholdTicks = getInt(context, SPARK_SECTION, "bonus-damage-freeze-threshold-ticks", 0);
            if (victim.getFreezeTicks() >= bonusThresholdTicks) {
                double bonusDamage = getDouble(context, SPARK_SECTION, "bonus-damage", 0.0);
                event.setDamage(event.getDamage() + bonusDamage);
            }
            int freezeTicks = getInt(context, SPARK_SECTION, "freeze-ticks", 0);
            victim.setFreezeTicks(freezeTicks);
        }
    }

    private java.util.Set<Material> getMaterials(InfuseContext context, String section, String key) {
        java.util.Set<Material> materials = new java.util.HashSet<>();
        for (String value : getStringList(context, section, key, java.util.List.of())) {
            Material material = Material.matchMaterial(value);
            if (material == null) {
                context.getPlugin().getLogger().warning("Unknown material in frost." + key + ": " + value);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }
}
