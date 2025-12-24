package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class FireInfuse extends BaseInfuse {
    private static final String FIREBALL_TAG = "infuse-spark-fireball";
    private static final String FIREBALL_PROCESSED_TAG = "infuse-spark-fireball-processed";

    public FireInfuse() {
        super(EffectGroup.SUPPORT, 2, "fire", InfuseItem.SUPPORT_FIRE);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        data.setFireSparkActive(true);
        launchFireball(player, context);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            data.setFireSparkActive(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.SUPPORT, 2)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (event.getDamager() instanceof Player damager && damager.getUniqueId().equals(data.getUuid())) {
            handlePlayerHit(event, data, context);
            return;
        }
        if (event.getDamager() instanceof Fireball fireball && fireball.getShooter() instanceof Player shooter
            && shooter.getUniqueId().equals(data.getUuid())) {
            handleFireballImpact(event, context, shooter, fireball);
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        data.setFireHitCount(0);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        data.setFireHitCount(0);
    }

    private void handlePlayerHit(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int hitsToIgnite = getInt(context, PASSIVE_SECTION, "hits-to-ignite", 10);
        int hitCount = data.getFireHitCount() + 1;
        if (hitsToIgnite > 0 && hitCount >= hitsToIgnite) {
            applyIgnite(target, context);
            hitCount = 0;
        }
        data.setFireHitCount(hitCount);
        applyIgnitedBonus(target, context);
    }

    public void onProjectileHit(ProjectileHitEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Fireball fireball)) {
            return;
        }
        if (!fireball.hasMetadata(FIREBALL_TAG) || !(fireball.getShooter() instanceof Player shooter)
            || !shooter.getUniqueId().equals(data.getUuid())) {
            return;
        }
        if (!markFireballProcessed(fireball, context)) {
            return;
        }
        Location impactLocation;
        if (event.getHitEntity() != null) {
            impactLocation = event.getHitEntity().getLocation();
        } else if (event.getHitBlock() != null) {
            impactLocation = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        } else {
            impactLocation = fireball.getLocation();
        }
        applyFireballEffects(impactLocation, shooter, context);
    }

    private void handleFireballImpact(EntityDamageByEntityEvent event, InfuseContext context, Player shooter,
        Fireball fireball) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        if (!fireball.hasMetadata(FIREBALL_TAG) || !markFireballProcessed(fireball, context)) {
            return;
        }
        applyFireballEffects(event.getEntity().getLocation(), shooter, context);
    }

    private void launchFireball(Player player, InfuseContext context) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setMetadata(FIREBALL_TAG, new FixedMetadataValue(context.getPlugin(), Boolean.TRUE));
        double speed = getDouble(context, SPARK_SECTION, "fireball-speed", 1.0);
        fireball.setYield(0f);
        fireball.setIsIncendiary(false);
        fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(speed));
    }

    private void applyIgnite(LivingEntity target, InfuseContext context) {
        int seconds = getInt(context, PASSIVE_SECTION, "ignite-duration-seconds", 4);
        int ticks = seconds * context.ticksPerSecond();
        target.setFireTicks(ticks);
    }

    private void applyIgnitedBonus(LivingEntity target, InfuseContext context) {
        if (target.getFireTicks() <= 0) {
            return;
        }
        double bonusHearts = getDouble(context, PASSIVE_SECTION, "ignited-true-damage-hearts", 0.5);
        applyTrueDamage(target, bonusHearts);
    }

    private void applyTrueDamage(LivingEntity target, double hearts) {
        if (hearts <= 0) {
            return;
        }
        double healthLoss = hearts * 2.0;
        double health = target.getHealth();
        target.setHealth(Math.max(0.0, health - healthLoss));
    }

    private void applyFireballEffects(Location center, Player shooter, InfuseContext context) {
        double fireballDamageHearts = getDouble(context, SPARK_SECTION, "fireball-true-damage-hearts", 3.5);
        double radius = getDouble(context, SPARK_SECTION, "fireball-ignite-radius", 4.5);
        Set<LivingEntity> affectedEntities = new HashSet<>();
        center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
            .filter(entity -> entity instanceof LivingEntity)
            .map(entity -> (LivingEntity) entity)
            .forEach(affectedEntities::add);
        affectedEntities.stream()
            .filter(entity -> !entity.getUniqueId().equals(shooter.getUniqueId()))
            .forEach(entity -> {
                applyTrueDamage(entity, fireballDamageHearts);
                applyIgnite(entity, context);
                applyIgnitedBonus(entity, context);
            });
    }

    private boolean markFireballProcessed(Fireball fireball, InfuseContext context) {
        if (fireball.hasMetadata(FIREBALL_PROCESSED_TAG)) {
            return false;
        }
        fireball.setMetadata(FIREBALL_PROCESSED_TAG, new FixedMetadataValue(context.getPlugin(), Boolean.TRUE));
        return true;
    }
}
