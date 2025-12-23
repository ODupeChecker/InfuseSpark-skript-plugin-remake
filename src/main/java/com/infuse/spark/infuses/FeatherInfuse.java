package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FeatherInfuse extends BaseInfuse {
    public FeatherInfuse() {
        super(EffectGroup.PRIMARY, 5, "feather", InfuseItem.PRIMARY_FEATHER);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        int level = getInt(context, SPARK_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, SPARK_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, SPARK_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, SPARK_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.LEVITATION, level, durationSeconds, particles, icon);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        int sparkDurationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) sparkDurationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, PlayerData data, InfuseContext context) {
        Player player = event.getPlayer();
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 5) || player.isSneaking()) {
            return;
        }
        java.util.Set<Material> liquidMaterials = getLiquidMaterials(context);
        boolean onLiquid = false;
        for (int depth : getDepths(context)) {
            Material below = player.getLocation().clone().subtract(0, depth, 0).getBlock().getType();
            if (liquidMaterials.contains(below)) {
                onLiquid = true;
                break;
            }
        }
        boolean requireHeadClear = getBoolean(context, PASSIVE_SECTION, "require-head-clear", false);
        boolean headLiquid = liquidMaterials.contains(player.getEyeLocation().getBlock().getType());
        if (onLiquid && (!requireHeadClear || !headLiquid)) {
            List<org.bukkit.block.Block> blocks = new ArrayList<>();
            org.bukkit.block.Block center = player.getLocation().getBlock();
            int radius = getInt(context, PASSIVE_SECTION, "freeze-radius-xz", 0);
            int radiusY = getInt(context, PASSIVE_SECTION, "freeze-radius-y", 0);
            Material waterReplace = getMaterial(context, PASSIVE_SECTION, "water-replace", null);
            Material lavaReplace = getMaterial(context, PASSIVE_SECTION, "lava-replace", null);
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radiusY; y <= radiusY; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        org.bukkit.block.Block block = center.getRelative(x, y, z);
                        if (block.getType() == Material.WATER) {
                            blocks.add(block);
                            if (waterReplace != null) {
                                block.setType(waterReplace);
                            }
                        } else if (block.getType() == Material.LAVA) {
                            blocks.add(block);
                            if (lavaReplace != null) {
                                block.setType(lavaReplace);
                            }
                        }
                    }
                }
            }
            int revertDelaySeconds = getInt(context, PASSIVE_SECTION, "freeze-revert-delay-seconds", 0);
            context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
                for (org.bukkit.block.Block block : blocks) {
                    if (block.getLocation().equals(player.getLocation().clone().subtract(0, 1, 0).getBlock().getLocation())) {
                        waitForPlayerLeave(player, block, context);
                    } else {
                        revertBlock(block, context);
                    }
                }
            }, (long) revertDelaySeconds * context.ticksPerSecond());
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 5) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private void waitForPlayerLeave(Player player, org.bukkit.block.Block block, InfuseContext context) {
        int intervalTicks = getInt(context, PASSIVE_SECTION, "stand-check-interval-ticks", 0);
        int safeInterval = Math.max(1, intervalTicks);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    revertBlock(block, context);
                    cancel();
                    return;
                }
                org.bukkit.block.Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                if (!below.getLocation().equals(block.getLocation())) {
                    revertBlock(block, context);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), safeInterval, safeInterval);
    }

    private void revertBlock(org.bukkit.block.Block block, InfuseContext context) {
        Material waterReplace = getMaterial(context, PASSIVE_SECTION, "water-replace", null);
        Material lavaReplace = getMaterial(context, PASSIVE_SECTION, "lava-replace", null);
        if (waterReplace != null && block.getType() == waterReplace) {
            block.setType(Material.WATER);
        } else if (lavaReplace != null && block.getType() == lavaReplace) {
            block.setType(Material.LAVA);
        }
    }

    private java.util.Set<Material> getLiquidMaterials(InfuseContext context) {
        java.util.Set<Material> materials = new java.util.HashSet<>();
        for (String key : getStringList(context, PASSIVE_SECTION, "liquid-materials", java.util.List.of())) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                context.getPlugin().getLogger().warning("Unknown material in feather.liquid-materials: " + key);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }

    private java.util.List<Integer> getDepths(InfuseContext context) {
        java.util.List<Integer> depths = new java.util.ArrayList<>();
        for (String entry : getStringList(context, PASSIVE_SECTION, "liquid-check-depths", java.util.List.of())) {
            try {
                depths.add(Integer.parseInt(entry));
            } catch (NumberFormatException ex) {
                context.getPlugin().getLogger().warning("Invalid feather.liquid-check-depths entry: " + entry);
            }
        }
        return depths;
    }

    private Material getMaterial(InfuseContext context, String section, String key, Material defaultValue) {
        String value = getString(context, section, key, "");
        if (value.isBlank()) {
            return defaultValue;
        }
        Material material = Material.matchMaterial(value);
        if (material == null) {
            context.getPlugin().getLogger().warning("Unknown material in feather." + key + ": " + value);
            return defaultValue;
        }
        return material;
    }
}
