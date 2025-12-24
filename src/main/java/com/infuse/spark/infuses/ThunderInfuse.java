package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class ThunderInfuse extends BaseInfuse {
    private final Map<UUID, Integer> hitCounts = new HashMap<>();

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
        double trueDamage = getDouble(context, SPARK_SECTION, "lightning-true-damage-hearts", 0.0);
        int cameraLockSeconds = getInt(context, SPARK_SECTION, "camera-lock-duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        strikeNearby(player, data, radius, trueDamage, cameraLockSeconds, context);
        SlotHelper.setSlotActive(data, slot, false);
        SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int hitsToStrike = getInt(context, PASSIVE_SECTION, "hits-to-strike", 10);
        if (hitsToStrike <= 0) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int hits = hitCounts.getOrDefault(playerId, 0) + 1;
        if (hits >= hitsToStrike) {
            double trueDamage = getDouble(context, PASSIVE_SECTION, "lightning-true-damage-hearts", 0.0);
            Bukkit.getScheduler().runTask(context.getPlugin(), () -> strikeTarget(target, trueDamage));
            hits = 0;
        }
        hitCounts.put(playerId, hits);
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

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7)) {
            hitCounts.remove(player.getUniqueId());
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        hitCounts.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        hitCounts.remove(player.getUniqueId());
    }

    private void strikeNearby(Player player, PlayerData data, int radius, double trueDamage, int cameraLockSeconds, InfuseContext context) {
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            if (entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (target instanceof Player targetPlayer && data.getTrusted().contains(targetPlayer.getUniqueId())) {
                continue;
            }
            strikeTarget(target, trueDamage);
            if (target instanceof Player targetPlayer) {
                applyCameraLock(targetPlayer, cameraLockSeconds, context);
            }
        }
    }

    private void strikeTarget(LivingEntity target, double trueDamageHearts) {
        Location location = target.getLocation();
        location.getWorld().strikeLightningEffect(location);
        applyTrueDamage(target, trueDamageHearts);
    }

    private void applyTrueDamage(LivingEntity target, double hearts) {
        if (hearts <= 0.0 || target.isDead()) {
            return;
        }
        double damage = hearts * 2.0;
        double newHealth = Math.max(0.0, target.getHealth() - damage);
        target.setHealth(newHealth);
    }

    private void applyCameraLock(Player target, int seconds, InfuseContext context) {
        if (seconds <= 0) {
            return;
        }
        int ticks = seconds * context.ticksPerSecond();
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 10, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 5, false, false, false));
        target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + ticks));
        float lockedYaw = target.getLocation().getYaw();
        float lockedPitch = target.getLocation().getPitch();
        new BukkitRunnable() {
            int remaining = ticks;

            @Override
            public void run() {
                if (remaining-- <= 0 || !target.isOnline()) {
                    cancel();
                    return;
                }
                Location location = target.getLocation();
                location.setYaw(lockedYaw);
                location.setPitch(lockedPitch);
                target.teleport(location);
            }
        }.runTaskTimer(context.getPlugin(), 0L, 1L);
    }
}
