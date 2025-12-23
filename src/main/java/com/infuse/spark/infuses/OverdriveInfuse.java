package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class OverdriveInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 11;

    private final Map<UUID, OverdriveState> states = new HashMap<>();

    public OverdriveInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "overdrive", InfuseItem.PRIMARY_OVERDRIVE);
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
        OverdriveState state = getState(player.getUniqueId());
        state.sparkActive = true;
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 10);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        if (state.sparkTask != null) {
            state.sparkTask.cancel();
        }
        state.sparkTask = context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            state.sparkActive = false;
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            clearState(player, context);
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!data.getUuid().equals(damager.getUniqueId())) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        OverdriveState state = getState(damager.getUniqueId());
        if (state.mirroring) {
            return;
        }
        boolean sparkActive = state.sparkActive;
        double chance = getDouble(context, sparkActive ? SPARK_SECTION : PASSIVE_SECTION, "mirror-chance", 0.0);
        if (chance <= 0.0) {
            return;
        }
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() > chance) {
            return;
        }
        double multiplier = getDouble(context, sparkActive ? SPARK_SECTION : PASSIVE_SECTION, "echo-damage-multiplier", 1.0);
        double mirrorDamage = Math.max(0.0, event.getFinalDamage() * multiplier);
        if (mirrorDamage <= 0.0) {
            return;
        }
        Player victim = (Player) event.getEntity();
        boolean critical = event.isCritical();
        boolean sweep = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean sprint = damager.isSprinting();
        boolean mistime = isMistimed(damager);
        state.mirroring = true;
        try {
            victim.damage(mirrorDamage, damager);
            applyMirrorKnockback(victim, damager, sweep, sprint);
            playMirrorSound(victim, critical, sweep, sprint, mistime);
        } finally {
            state.mirroring = false;
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        clearState(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        clearState(player, context);
    }

    private OverdriveState getState(UUID playerId) {
        return states.computeIfAbsent(playerId, key -> new OverdriveState());
    }

    private void clearState(Player player, InfuseContext context) {
        OverdriveState state = states.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (state.sparkTask != null) {
            state.sparkTask.cancel();
            state.sparkTask = null;
        }
    }

    private static class OverdriveState {
        private boolean sparkActive;
        private boolean mirroring;
        private BukkitTask sparkTask;
    }

    private boolean isMistimed(Player player) {
        return player.getAttackCooldown() < 0.9f;
    }

    private void applyMirrorKnockback(Player victim, Player damager, boolean sweep, boolean sprint) {
        Vector direction = victim.getLocation().toVector().subtract(damager.getLocation().toVector());
        if (direction.lengthSquared() == 0.0) {
            direction = damager.getLocation().getDirection();
        }
        direction = direction.normalize();
        double strength = 0.4;
        if (sweep) {
            strength = 0.2;
        } else if (sprint) {
            strength = 0.6;
        }
        victim.setVelocity(victim.getVelocity().add(direction.multiply(strength)));
    }

    private void playMirrorSound(Player victim, boolean critical, boolean sweep, boolean sprint, boolean mistime) {
        if (critical) {
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
        }
        if (sweep) {
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        }
        if (sprint) {
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.0f);
        }
        if (mistime) {
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0f, 1.0f);
        }
    }
}
