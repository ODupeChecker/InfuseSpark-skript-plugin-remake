package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class PiglinInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 10;
    private final Map<UUID, Map<UUID, MarkState>> marksByPiglin = new HashMap<>();

    public PiglinInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "piglin", InfuseItem.PRIMARY_PIGLIN);
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
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            clearPiglinMarks(player.getUniqueId());
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        data.setPiglinSparkActive(true);
        float soundVolume = (float) getDouble(context, SPARK_SECTION, "sound-volume", 1.0);
        float soundPitch = (float) getDouble(context, SPARK_SECTION, "sound-pitch", 1.0);
        player.playSound(player.getLocation(), Sound.ENTITY_PIGLIN_ANGRY, soundVolume, soundPitch);
        int particleCount = getInt(context, SPARK_SECTION, "particle-count", 20);
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), particleCount, 0.3, 0.1, 0.3,
            Bukkit.createBlockData(Material.NETHERRACK));
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            data.setPiglinSparkActive(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (data.getUuid().equals(victim.getUniqueId())) {
            handlePiglinHit(victim, damager, data, context);
        }
        if (data.getUuid().equals(damager.getUniqueId())) {
            handlePiglinAttack(damager, victim, data, context, event);
        }
    }

    private void handlePiglinHit(Player piglin, Player attacker, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        Map<UUID, MarkState> marks = marksByPiglin.computeIfAbsent(piglin.getUniqueId(), key -> new HashMap<>());
        MarkState state = marks.get(attacker.getUniqueId());
        if (state != null && (state.marked || state.cooldown)) {
            return;
        }
        if (state == null) {
            state = new MarkState();
            marks.put(attacker.getUniqueId(), state);
        }
        applyMark(piglin, attacker, data, context, state);
    }

    private void applyMark(Player piglin, Player attacker, PlayerData data, InfuseContext context, MarkState state) {
        int windowTicks = getInt(context, PASSIVE_SECTION, "mark-window-ticks", 40);
        int windowSeconds = Math.max(1, (int) Math.ceil((double) windowTicks / context.ticksPerSecond()));
        context.applyPotion(attacker, PotionEffectType.GLOWING, 1, windowSeconds, false, false);
        state.marked = true;
        state.expireTask = context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            expireMark(piglin.getUniqueId(), attacker.getUniqueId(), data, context);
        }, windowTicks);
    }

    private void handlePiglinAttack(Player piglin, Player target, PlayerData data, InfuseContext context,
                                     EntityDamageByEntityEvent event) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        Map<UUID, MarkState> marks = marksByPiglin.get(piglin.getUniqueId());
        if (marks == null) {
            return;
        }
        MarkState state = marks.get(target.getUniqueId());
        if (state == null || !state.marked) {
            return;
        }
        double bonusDamage = data.isPiglinSparkActive()
            ? getDouble(context, SPARK_SECTION, "bonus-damage", 1.8)
            : getDouble(context, PASSIVE_SECTION, "bonus-damage", 1.25);
        event.setDamage(event.getDamage() + bonusDamage);
        consumeMark(piglin.getUniqueId(), target.getUniqueId(), data, context);
    }

    private void expireMark(UUID piglinId, UUID attackerId, PlayerData data, InfuseContext context) {
        Map<UUID, MarkState> marks = marksByPiglin.get(piglinId);
        if (marks == null) {
            return;
        }
        MarkState state = marks.get(attackerId);
        if (state == null || !state.marked) {
            return;
        }
        state.marked = false;
        removeGlowing(attackerId);
        startCooldown(piglinId, attackerId, data, context, state);
    }

    private void consumeMark(UUID piglinId, UUID attackerId, PlayerData data, InfuseContext context) {
        Map<UUID, MarkState> marks = marksByPiglin.get(piglinId);
        if (marks == null) {
            return;
        }
        MarkState state = marks.get(attackerId);
        if (state == null || !state.marked) {
            return;
        }
        state.marked = false;
        if (state.expireTask != null) {
            state.expireTask.cancel();
            state.expireTask = null;
        }
        removeGlowing(attackerId);
        startCooldown(piglinId, attackerId, data, context, state);
    }

    private void startCooldown(UUID piglinId, UUID attackerId, PlayerData data, InfuseContext context, MarkState state) {
        if (state.cooldown) {
            return;
        }
        int cooldownSeconds = data.isPiglinSparkActive()
            ? getInt(context, SPARK_SECTION, "mark-cooldown-seconds", 2)
            : getInt(context, PASSIVE_SECTION, "mark-cooldown-seconds", 4);
        state.cooldown = true;
        state.cooldownTask = context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            clearCooldown(piglinId, attackerId);
        }, (long) cooldownSeconds * context.ticksPerSecond());
    }

    private void clearCooldown(UUID piglinId, UUID attackerId) {
        Map<UUID, MarkState> marks = marksByPiglin.get(piglinId);
        if (marks == null) {
            return;
        }
        MarkState state = marks.get(attackerId);
        if (state == null) {
            return;
        }
        state.cooldown = false;
        state.cooldownTask = null;
        if (!state.marked) {
            marks.remove(attackerId);
        }
        if (marks.isEmpty()) {
            marksByPiglin.remove(piglinId);
        }
    }

    private void removeGlowing(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    private void clearPiglinMarks(UUID piglinId) {
        Map<UUID, MarkState> marks = marksByPiglin.remove(piglinId);
        if (marks == null) {
            return;
        }
        for (Map.Entry<UUID, MarkState> entry : marks.entrySet()) {
            MarkState state = entry.getValue();
            if (state.expireTask != null) {
                state.expireTask.cancel();
            }
            if (state.cooldownTask != null) {
                state.cooldownTask.cancel();
            }
            removeGlowing(entry.getKey());
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        UUID quitter = event.getPlayer().getUniqueId();
        clearPiglinMarks(quitter);
        Iterator<Map.Entry<UUID, Map<UUID, MarkState>>> iterator = marksByPiglin.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, MarkState>> entry = iterator.next();
            Map<UUID, MarkState> marks = entry.getValue();
            MarkState state = marks.remove(quitter);
            if (state != null) {
                if (state.expireTask != null) {
                    state.expireTask.cancel();
                }
                if (state.cooldownTask != null) {
                    state.cooldownTask.cancel();
                }
            }
            if (marks.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        clearPiglinMarks(player.getUniqueId());
    }

    private static class MarkState {
        private boolean marked;
        private boolean cooldown;
        private BukkitTask expireTask;
        private BukkitTask cooldownTask;
    }
}
