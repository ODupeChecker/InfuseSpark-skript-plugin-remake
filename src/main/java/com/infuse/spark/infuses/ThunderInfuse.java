package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ThunderInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 7;
    private static final double PASSIVE_TRUE_DAMAGE_HEARTS = 0.5;
    private static final double SPARK_TRUE_DAMAGE_HEARTS = 3.0;

    public ThunderInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "thunder", InfuseItem.PRIMARY_THUNDER);
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
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        strikeNearby(player, data, radius, context);
        long activeTicks = Math.max(0L, ((startMinutes * 60L) + startSeconds) * context.ticksPerSecond());
        if (activeTicks <= 0L) {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                SlotHelper.setSlotActive(data, slot, false);
                SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
            }
        }.runTaskLater(context.getPlugin(), activeTicks);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!data.getUuid().equals(player.getUniqueId())) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target instanceof Player targetPlayer && data.getTrusted().contains(targetPlayer.getUniqueId())) {
            return;
        }
        int hitsToStrike = getInt(context, PASSIVE_SECTION, "hits-to-strike", 10);
        int hitCount = data.getThunderHitCount() + 1;
        if (hitsToStrike > 0 && hitCount >= hitsToStrike) {
            triggerLightningStrike(target, context, PASSIVE_TRUE_DAMAGE_HEARTS);
            hitCount = 0;
        }
        data.setThunderHitCount(hitCount);
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        data.setThunderHitCount(0);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        data.setThunderHitCount(0);
    }

    private void strikeNearby(Player player, PlayerData data, int radius, InfuseContext context) {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (entity instanceof Player target && data.getTrusted().contains(target.getUniqueId())) {
                continue;
            }
            triggerLightningStrike(living, context, SPARK_TRUE_DAMAGE_HEARTS);
            applyCameraLock(entity, context);
        }
    }

    private void triggerLightningStrike(LivingEntity target, InfuseContext context, double trueDamageHearts) {
        Location strikeLocation = target.getLocation();
        strikeLocation.getWorld().strikeLightningEffect(strikeLocation);
        applyTrueDamage(target, trueDamageHearts);
    }

    private void applyTrueDamage(LivingEntity target, double hearts) {
        if (hearts <= 0) {
            return;
        }
        double healthLoss = hearts * 2.0;
        double health = target.getHealth();
        target.setHealth(Math.max(0.0, health - healthLoss));
    }

    private void applyCameraLock(Entity entity, InfuseContext context) {
        if (!(entity instanceof Player target)) {
            return;
        }
        float yaw = target.getLocation().getYaw();
        float pitch = target.getLocation().getPitch();
        int durationTicks = 5 * context.ticksPerSecond();
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isOnline() || target.isDead() || ticks >= durationTicks) {
                    cancel();
                    return;
                }
                target.setRotation(yaw, pitch);
                ticks++;
            }
        }.runTaskTimer(context.getPlugin(), 0L, 1L);
    }
}
