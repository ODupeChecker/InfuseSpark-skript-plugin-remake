package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class NinjaInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 4;

    private final Map<UUID, NinjaState> states = new HashMap<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public NinjaInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "ninja", InfuseItem.PRIMARY_INVISIBILITY);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        NinjaState state = getState(player.getUniqueId());
        boolean trulyInvisible = state.isTrulyInvisible();
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, (active || trulyInvisible) ? activeIcon : inactiveIcon);
        int level = getInt(context, PASSIVE_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, PASSIVE_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.INVISIBILITY, level, durationSeconds, particles, icon);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode((active || trulyInvisible) ? activeColor : inactiveColor);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        NinjaState state = getState(player.getUniqueId());
        state.sparkActive = true;
        state.lastMovementMs = System.currentTimeMillis();
        cancelPassiveInvisibility(player, state, context, true);
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        applyVisibility(player, state, context);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        cancelTask(state.sparkEndTask);
        state.sparkEndTask = Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> endSpark(player, data, slot, context, state, endMinutes, endSeconds),
            (long) durationSeconds * context.ticksPerSecond());
        startSparkTrail(player, context, state);
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            clearState(player, context);
            return;
        }
        NinjaState state = getState(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (state.lastMovementMs == 0L) {
            state.lastMovementMs = now;
        }
        if (!state.sparkActive && !state.passiveTrueInvisibility && now >= state.passiveCooldownEndMs) {
            int stillMs = getInt(context, PASSIVE_SECTION, "stand-still-seconds", 0) * 1000;
            if (stillMs > 0 && now - state.lastMovementMs >= stillMs) {
                beginPassiveInvisibility(player, state, context);
            }
        }
        applyVisibility(player, state, context);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        if (!hasMoved(event)) {
            return;
        }
        Player player = event.getPlayer();
        NinjaState state = getState(player.getUniqueId());
        state.lastMovementMs = System.currentTimeMillis();
        if (state.passiveTrueInvisibility && state.waitingForMovement) {
            startPassiveCountdown(player, state, context);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        NinjaState state = states.get(player.getUniqueId());
        if (state == null || !state.sparkActive) {
            return;
        }
        double multiplier = getDouble(context, SPARK_SECTION, "damage-taken-multiplier", 1.0);
        if (multiplier > 0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (event.isCancelled()) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player damager && damager.getUniqueId().equals(data.getUuid())) {
            attacker = damager;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter
            && shooter.getUniqueId().equals(data.getUuid())) {
            attacker = shooter;
        }
        if (attacker == null) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        NinjaState state = states.get(data.getUuid());
        if (state == null || !state.isTrulyInvisible()) {
            return;
        }
        double multiplier = state.sparkActive
            ? getDouble(context, SPARK_SECTION, "damage-dealt-multiplier", 1.0)
            : getDouble(context, PASSIVE_SECTION, "passive-damage-multiplier", 1.0);
        if (multiplier > 0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        clearState(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        clearState(player, context);
    }

    private void beginPassiveInvisibility(Player player, NinjaState state, InfuseContext context) {
        if (state.sparkActive || state.passiveTrueInvisibility) {
            return;
        }
        state.passiveTrueInvisibility = true;
        state.waitingForMovement = true;
        applyVisibility(player, state, context);
    }

    private void startPassiveCountdown(Player player, NinjaState state, InfuseContext context) {
        state.waitingForMovement = false;
        cancelTask(state.passiveTimeoutTask);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "true-invisibility-duration-seconds", 0);
        if (durationSeconds <= 0) {
            cancelPassiveInvisibility(player, state, context, true);
            return;
        }
        state.passiveTimeoutTask = Bukkit.getScheduler().runTaskLater(context.getPlugin(),
            () -> cancelPassiveInvisibility(player, state, context, true),
            (long) durationSeconds * context.ticksPerSecond());
    }

    private void cancelPassiveInvisibility(Player player, NinjaState state, InfuseContext context, boolean startCooldown) {
        if (!state.passiveTrueInvisibility) {
            return;
        }
        state.passiveTrueInvisibility = false;
        state.waitingForMovement = false;
        cancelTask(state.passiveTimeoutTask);
        state.passiveTimeoutTask = null;
        if (startCooldown) {
            int cooldownSeconds = getInt(context, PASSIVE_SECTION, "passive-cooldown-seconds", 0);
            state.passiveCooldownEndMs = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        }
        applyVisibility(player, state, context);
    }

    private void endSpark(Player player, PlayerData data, int slot, InfuseContext context, NinjaState state, int endMinutes, int endSeconds) {
        state.sparkActive = false;
        cancelTask(state.sparkEndTask);
        state.sparkEndTask = null;
        cancelTask(state.sparkTrailTask);
        state.sparkTrailTask = null;
        SlotHelper.setSlotActive(data, slot, false);
        SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        applyVisibility(player, state, context);
    }

    private void startSparkTrail(Player player, InfuseContext context, NinjaState state) {
        cancelTask(state.sparkTrailTask);
        int interval = Math.max(1, getInt(context, SPARK_SECTION, "trail-interval-ticks", 4));
        int count = getInt(context, SPARK_SECTION, "trail-particle-count", 8);
        state.sparkTrailTask = Bukkit.getScheduler().runTaskTimer(context.getPlugin(), () -> {
            if (!state.sparkActive) {
                cancelTask(state.sparkTrailTask);
                state.sparkTrailTask = null;
                return;
            }
            player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), count, 0.25, 0.25, 0.25,
                Bukkit.createBlockData(Material.IRON_BLOCK));
        }, 0L, interval);
    }

    private void applyVisibility(Player player, NinjaState state, InfuseContext context) {
        boolean shouldHide = state.isTrulyInvisible();
        if (shouldHide) {
            hidePlayerFromAll(player, context);
        } else {
            revealPlayerToAll(player, context);
        }
    }

    private NinjaState getState(UUID playerId) {
        return states.computeIfAbsent(playerId, key -> new NinjaState());
    }

    private void clearState(Player player, InfuseContext context) {
        NinjaState state = states.remove(player.getUniqueId());
        if (state != null) {
            cancelTask(state.passiveTimeoutTask);
            cancelTask(state.sparkEndTask);
            cancelTask(state.sparkTrailTask);
        }
        revealPlayerToAll(player, context);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private boolean hasMoved(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return false;
        }
        return event.getFrom().getX() != event.getTo().getX()
            || event.getFrom().getY() != event.getTo().getY()
            || event.getFrom().getZ() != event.getTo().getZ();
    }

    private void hidePlayerFromAll(Player player, InfuseContext context) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.hidePlayer(context.getPlugin(), player);
        }
        hiddenPlayers.add(player.getUniqueId());
    }

    private void revealPlayerToAll(Player player, InfuseContext context) {
        if (!hiddenPlayers.contains(player.getUniqueId())) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.showPlayer(context.getPlugin(), player);
        }
        hiddenPlayers.remove(player.getUniqueId());
    }

    private static class NinjaState {
        private long lastMovementMs = System.currentTimeMillis();
        private boolean passiveTrueInvisibility;
        private boolean waitingForMovement;
        private boolean sparkActive;
        private long passiveCooldownEndMs;
        private BukkitTask passiveTimeoutTask;
        private BukkitTask sparkEndTask;
        private BukkitTask sparkTrailTask;

        private boolean isTrulyInvisible() {
            return passiveTrueInvisibility || sparkActive;
        }
    }
}
