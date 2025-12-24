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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

public class NinjaInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 4;

    private final Map<UUID, Long> lastMovement = new HashMap<>();
    private final Map<UUID, PassiveState> passiveStates = new HashMap<>();
    private final Map<UUID, Long> passiveCooldownEnd = new HashMap<>();
    private final Map<UUID, Long> sparkEndTimes = new HashMap<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public NinjaInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "ninja", InfuseItem.PRIMARY_NINJA);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        int level = getInt(context, PASSIVE_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, PASSIVE_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.INVISIBILITY, level, durationSeconds, particles, icon);
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
        long now = System.currentTimeMillis();
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        sparkEndTimes.put(player.getUniqueId(), now + durationSeconds * 1000L);
        clearPassiveState(player, context);
        hidePlayerFromAll(player, context);
        applySpeed(player, context, SPARK_SECTION, durationSeconds);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            sparkEndTimes.remove(player.getUniqueId());
            revealIfIdle(player, context);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            clearState(player, context);
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastMovement.putIfAbsent(playerId, now);
        boolean sparkActive = isSparkActive(playerId, now);
        PassiveState passiveState = passiveStates.get(playerId);
        if (passiveState != null && passiveState.timerStarted && passiveState.invisibleUntilMs <= now) {
            passiveStates.remove(playerId);
            passiveState = null;
        }
        if (sparkActive) {
            clearPassiveState(player, context);
        } else if (passiveState == null && !isPassiveOnCooldown(playerId, now)) {
            long stillForMs = now - lastMovement.getOrDefault(playerId, now);
            int stillSeconds = getInt(context, PASSIVE_SECTION, "still-seconds", 5);
            if (stillForMs >= stillSeconds * 1000L) {
                startPassiveInvisibility(player, context, now);
                passiveState = passiveStates.get(playerId);
            }
        }
        if (passiveState != null && passiveState.timerStarted && passiveState.invisibleUntilMs <= now) {
            passiveStates.remove(playerId);
            passiveState = null;
        }
        if (sparkActive) {
            hidePlayerFromAll(player, context);
            spawnSparkParticles(player, context);
        } else if (passiveState != null) {
            hidePlayerFromAll(player, context);
        } else {
            revealPlayerToAll(player, context);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, PlayerData data, InfuseContext context) {
        Player player = event.getPlayer();
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        if (!hasPositionChanged(event)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastMovement.put(playerId, now);
        PassiveState passiveState = passiveStates.get(playerId);
        if (passiveState != null && !passiveState.timerStarted) {
            int durationSeconds = getInt(context, PASSIVE_SECTION, "true-duration-seconds", 4);
            passiveState.invisibleUntilMs = now + durationSeconds * 1000L;
            passiveState.timerStarted = true;
            applySpeed(player, context, PASSIVE_SECTION, durationSeconds);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        if (isSparkActive(player.getUniqueId(), System.currentTimeMillis())) {
            double incomingBonus = getDouble(context, SPARK_SECTION, "incoming-damage-bonus", 0.0);
            event.setDamage(event.getDamage() * (1.0 + incomingBonus));
        }
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        boolean sparkActive = isSparkActive(playerId, now);
        boolean passiveActive = isPassiveActive(playerId, now);
        if (!sparkActive && !passiveActive) {
            return;
        }
        double damage = event.getDamage();
        if (sparkActive) {
            double outgoingBonus = getDouble(context, SPARK_SECTION, "outgoing-damage-bonus", 0.0);
            damage *= 1.0 + outgoingBonus;
        } else if (passiveActive) {
            double bonus = getDouble(context, PASSIVE_SECTION, "passive-damage-bonus", 0.0);
            damage *= 1.0 + bonus;
        }
        event.setDamage(damage);
    }

    @Override
    public void onEntityDeath(EntityDeathEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        clearState(player, context);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        clearState(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        clearState(player, context);
    }

    private void startPassiveInvisibility(Player player, InfuseContext context, long now) {
        UUID playerId = player.getUniqueId();
        PassiveState state = new PassiveState();
        passiveStates.put(playerId, state);
        int cooldownSeconds = getInt(context, PASSIVE_SECTION, "cooldown-seconds", 30);
        passiveCooldownEnd.put(playerId, now + cooldownSeconds * 1000L);
        hidePlayerFromAll(player, context);
        playPassiveSound(player, context);
    }

    private void spawnSparkParticles(Player player, InfuseContext context) {
        int count = getInt(context, SPARK_SECTION, "particle-count", 6);
        double offset = getDouble(context, SPARK_SECTION, "particle-offset", 0.15);
        player.getWorld().spawnParticle(
            Particle.BLOCK,
            player.getLocation().add(0.0, 0.1, 0.0),
            Math.max(1, count),
            offset,
            0.0,
            offset,
            0.0,
            Material.IRON_BLOCK.createBlockData()
        );
    }

    private boolean hasPositionChanged(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return true;
        }
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return true;
        }
        return event.getFrom().getX() != event.getTo().getX()
            || event.getFrom().getY() != event.getTo().getY()
            || event.getFrom().getZ() != event.getTo().getZ();
    }

    private boolean isPassiveOnCooldown(UUID playerId, long now) {
        Long end = passiveCooldownEnd.get(playerId);
        if (end == null) {
            return false;
        }
        if (end <= now) {
            passiveCooldownEnd.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean isPassiveActive(UUID playerId, long now) {
        PassiveState state = passiveStates.get(playerId);
        if (state == null) {
            return false;
        }
        if (state.timerStarted && state.invisibleUntilMs <= now) {
            passiveStates.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean isSparkActive(UUID playerId, long now) {
        Long end = sparkEndTimes.get(playerId);
        if (end == null) {
            return false;
        }
        if (end <= now) {
            sparkEndTimes.remove(playerId);
            return false;
        }
        return true;
    }

    private void clearState(Player player, InfuseContext context) {
        UUID playerId = player.getUniqueId();
        passiveStates.remove(playerId);
        sparkEndTimes.remove(playerId);
        passiveCooldownEnd.remove(playerId);
        lastMovement.remove(playerId);
        revealPlayerToAll(player, context);
    }

    private void clearPassiveState(Player player, InfuseContext context) {
        passiveStates.remove(player.getUniqueId());
        revealIfIdle(player, context);
    }

    private void hidePlayerFromAll(Player player, InfuseContext context) {
        UUID playerId = player.getUniqueId();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.hidePlayer(context.getPlugin(), player);
        }
        hiddenPlayers.add(playerId);
    }

    private void revealIfIdle(Player player, InfuseContext context) {
        UUID playerId = player.getUniqueId();
        if (isSparkActive(playerId, System.currentTimeMillis())) {
            return;
        }
        if (isPassiveActive(playerId, System.currentTimeMillis())) {
            return;
        }
        revealPlayerToAll(player, context);
    }

    private void revealPlayerToAll(Player player, InfuseContext context) {
        UUID playerId = player.getUniqueId();
        if (!hiddenPlayers.contains(playerId)) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.showPlayer(context.getPlugin(), player);
        }
        hiddenPlayers.remove(playerId);
    }

    private void playPassiveSound(Player player, InfuseContext context) {
        String soundKey = getString(context, PASSIVE_SECTION, "activation-sound", Sound.BLOCK_AMETHYST_BLOCK_CHIME.name());
        float volume = (float) getDouble(context, PASSIVE_SECTION, "activation-sound-volume", 1.0);
        float pitch = (float) getDouble(context, PASSIVE_SECTION, "activation-sound-pitch", 1.2);
        Sound sound;
        try {
            sound = Sound.valueOf(soundKey);
        } catch (IllegalArgumentException ex) {
            sound = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void applySpeed(Player player, InfuseContext context, String section, int durationSeconds) {
        int level = getInt(context, section, "speed-level", 3);
        boolean particles = getBoolean(context, section, "speed-particles", false);
        boolean icon = getBoolean(context, section, "speed-icon", true);
        context.applyPotion(player, PotionEffectType.SPEED, level, durationSeconds, particles, icon);
    }

    private static class PassiveState {
        private boolean timerStarted;
        private long invisibleUntilMs;
    }
}
