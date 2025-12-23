package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ThunderInfuse extends BaseInfuse {
    public ThunderInfuse() {
        super(EffectGroup.PRIMARY, 7, "thunder", InfuseItem.PRIMARY_THUNDER);
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
        int radius = getInt(context, SPARK_SECTION, "lightning-radius", 0);
        int strikeCount = getInt(context, SPARK_SECTION, "strike-count", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (entity instanceof Player target && data.getTrusted().contains(target.getUniqueId())) {
                        continue;
                    }
                    entity.getWorld().strikeLightning(entity.getLocation());
                }
                count++;
                if (count >= strikeCount) {
                    SlotHelper.setSlotActive(data, slot, false);
                    SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        boolean critLightning = getBoolean(context, PASSIVE_SECTION, "crit-lightning", false);
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7) && event.isCritical() && critLightning) {
            Bukkit.getScheduler().runTask(context.getPlugin(), () -> event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation()));
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        boolean lightningImmunity = getBoolean(context, PASSIVE_SECTION, "lightning-immunity", false);
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7)
            && event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING
            && lightningImmunity) {
            event.setCancelled(true);
        }
    }
}
