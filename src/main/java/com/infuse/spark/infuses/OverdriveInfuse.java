package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

public class OverdriveInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 11;
    private static final int MAX_STACKS = 5;
    private static final double STACK_BONUS = 0.10;
    private static final int INACTIVITY_SECONDS = 5;
    private static final UUID OVERDRIVE_MODIFIER = UUID.fromString("c4e2b7ed-4e24-4a0a-9b8f-7bc2249c2c01");

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
        OverdriveState state = states.get(player.getUniqueId());
        if (state != null) {
            applyAttackSpeed(player, state.stacks, context);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        OverdriveState state = getState(player.getUniqueId());
        state.unbreakable = true;
        setStacks(player, state, MAX_STACKS, context);
        clearInactivity(state);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 10);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        if (state.unbreakableTask != null) {
            state.unbreakableTask.cancel();
        }
        state.unbreakableTask = context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            state.unbreakable = false;
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
            scheduleInactivityReset(player, state, context);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            clearState(player, context);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!data.getUuid().equals(player.getUniqueId())) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, EFFECT_ID)) {
            return;
        }
        OverdriveState state = states.get(player.getUniqueId());
        if (state == null || state.unbreakable) {
            return;
        }
        resetStacks(player, state, context);
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
        if (state.unbreakable) {
            return;
        }
        int nextStacks = Math.min(MAX_STACKS, state.stacks + 1);
        if (nextStacks != state.stacks) {
            setStacks(damager, state, nextStacks, context);
        }
        scheduleInactivityReset(damager, state, context);
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

    private void scheduleInactivityReset(Player player, OverdriveState state, InfuseContext context) {
        if (state.unbreakable) {
            return;
        }
        clearInactivity(state);
        state.inactivityTask = context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            if (state.unbreakable) {
                return;
            }
            resetStacks(player, state, context);
        }, (long) INACTIVITY_SECONDS * context.ticksPerSecond());
    }

    private void clearInactivity(OverdriveState state) {
        if (state.inactivityTask != null) {
            state.inactivityTask.cancel();
            state.inactivityTask = null;
        }
    }

    private void setStacks(Player player, OverdriveState state, int stacks, InfuseContext context) {
        state.stacks = stacks;
        applyAttackSpeed(player, stacks, context);
    }

    private void resetStacks(Player player, OverdriveState state, InfuseContext context) {
        clearInactivity(state);
        state.stacks = 0;
        applyAttackSpeed(player, 0, context);
    }

    private void applyAttackSpeed(Player player, int stacks, InfuseContext context) {
        if (stacks <= 0) {
            context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_SPEED, OVERDRIVE_MODIFIER);
            return;
        }
        double multiplier = STACK_BONUS * stacks;
        context.applyMultiplicativeAttributeModifier(player, Attribute.GENERIC_ATTACK_SPEED, OVERDRIVE_MODIFIER, multiplier);
    }

    private void clearState(Player player, InfuseContext context) {
        OverdriveState state = states.remove(player.getUniqueId());
        if (state == null) {
            context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_SPEED, OVERDRIVE_MODIFIER);
            return;
        }
        clearInactivity(state);
        if (state.unbreakableTask != null) {
            state.unbreakableTask.cancel();
            state.unbreakableTask = null;
        }
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_SPEED, OVERDRIVE_MODIFIER);
    }

    private static class OverdriveState {
        private int stacks;
        private boolean unbreakable;
        private BukkitTask inactivityTask;
        private BukkitTask unbreakableTask;
    }
}
