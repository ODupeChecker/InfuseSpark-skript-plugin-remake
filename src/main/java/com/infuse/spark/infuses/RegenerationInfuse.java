package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenerationInfuse extends BaseInfuse {
    public RegenerationInfuse() {
        super(EffectGroup.PRIMARY, 8, "regeneration", InfuseItem.PRIMARY_REGENERATION);
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
        int tickDurationSeconds = getInt(context, SPARK_SECTION, "tick-potion-duration-seconds", 0);
        int tickLevel = getInt(context, SPARK_SECTION, "tick-potion-level", 0);
        boolean tickParticles = getBoolean(context, SPARK_SECTION, "tick-potion-particles", false);
        boolean tickIcon = getBoolean(context, SPARK_SECTION, "tick-potion-icon", false);
        int radius = getInt(context, SPARK_SECTION, "trusted-radius", 0);
        int totalTicks = getInt(context, SPARK_SECTION, "tick-count", 0);
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
                context.applyPotion(player, PotionEffectType.REGENERATION, tickLevel, tickDurationSeconds, tickParticles, tickIcon);
                for (Player nearby : player.getWorld().getPlayers()) {
                    if (nearby.getLocation().distance(player.getLocation()) > radius) {
                        continue;
                    }
                    if (!nearby.equals(player) && data.getTrusted().contains(nearby.getUniqueId())) {
                        context.applyPotion(player, PotionEffectType.REGENERATION, tickLevel, tickDurationSeconds, tickParticles, tickIcon);
                    }
                }
                count++;
                if (count >= totalTicks) {
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
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 8) && event.isCritical()) {
            int level = getInt(context, PASSIVE_SECTION, "crit-potion-level", 0);
            int durationSeconds = getInt(context, PASSIVE_SECTION, "crit-potion-duration-seconds", 0);
            boolean particles = getBoolean(context, PASSIVE_SECTION, "crit-potion-particles", false);
            boolean icon = getBoolean(context, PASSIVE_SECTION, "crit-potion-icon", false);
            context.applyPotion(player, PotionEffectType.REGENERATION, level, durationSeconds, particles, icon);
        }
    }
}
