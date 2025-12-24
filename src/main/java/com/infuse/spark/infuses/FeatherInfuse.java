package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FeatherInfuse extends BaseInfuse {
    private static final int EFFECT_ID = 5;
    private static final int DEFAULT_LEVITATION_CYCLES = 3;

    public FeatherInfuse() {
        super(EffectGroup.PRIMARY, EFFECT_ID, "feather", InfuseItem.PRIMARY_FEATHER);
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

        double radius = getDouble(context, SPARK_SECTION, "levitation-radius", 8.0);
        int cycles = getInt(context, SPARK_SECTION, "cycles", DEFAULT_LEVITATION_CYCLES);
        double levitationDurationSeconds = getDouble(context, SPARK_SECTION, "levitation-duration-seconds", 2.0);
        double dropDurationSeconds = getDouble(context, SPARK_SECTION, "drop-duration-seconds", 0.7);
        int amplifier = getInt(context, SPARK_SECTION, "levitation-amplifier", 2);
        boolean particles = getBoolean(context, SPARK_SECTION, "levitation-particles", false);
        boolean icon = getBoolean(context, SPARK_SECTION, "levitation-icon", false);

        applyLevitationCycles(player, data, radius, cycles, levitationDurationSeconds, dropDurationSeconds, amplifier, particles, icon, context);

        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        double sparkDurationSeconds = getDouble(context, SPARK_SECTION, "duration-seconds",
            cycles * (levitationDurationSeconds + dropDurationSeconds));
        long sparkDurationTicks = Math.max(1L, Math.round(sparkDurationSeconds * context.ticksPerSecond()));
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, sparkDurationTicks);
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
        double chance = getDouble(context, PASSIVE_SECTION, "levitation-chance", 0.05);
        if (chance <= 0.0) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        double durationSeconds = getDouble(context, PASSIVE_SECTION, "levitation-duration-seconds", 2.0);
        int amplifier = getInt(context, PASSIVE_SECTION, "levitation-amplifier", 2);
        boolean particles = getBoolean(context, PASSIVE_SECTION, "levitation-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "levitation-icon", false);
        applyLevitationEffect(target, durationSeconds, amplifier, particles, icon, context);
    }

    private void applyLevitationCycles(Player player, PlayerData data, double radius, int cycles, double levitationDurationSeconds,
        double dropDurationSeconds, int amplifier, boolean particles, boolean icon, InfuseContext context) {
        if (cycles <= 0) {
            return;
        }
        long levitationTicks = Math.max(1L, Math.round(levitationDurationSeconds * context.ticksPerSecond()));
        long dropTicks = Math.max(0L, Math.round(dropDurationSeconds * context.ticksPerSecond()));
        long intervalTicks = levitationTicks + dropTicks;
        if (intervalTicks <= 0L) {
            return;
        }

        List<LivingEntity> targets = getNearbyEnemies(player, data, radius);
        for (LivingEntity target : targets) {
            for (int cycle = 0; cycle < cycles; cycle++) {
                long delay = intervalTicks * cycle;
                context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
                    if (!target.isValid() || target.isDead()) {
                        return;
                    }
                    applyLevitationEffect(target, levitationDurationSeconds, amplifier, particles, icon, context);
                }, delay);
            }
        }
    }

    private List<LivingEntity> getNearbyEnemies(Player player, PlayerData data, double radius) {
        List<LivingEntity> targets = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (entity instanceof Player target && data.getTrusted().contains(target.getUniqueId())) {
                continue;
            }
            targets.add(living);
        }
        return targets;
    }

    private void applyLevitationEffect(LivingEntity target, double durationSeconds, int amplifier, boolean particles, boolean icon,
        InfuseContext context) {
        long durationTicks = Math.max(1L, Math.round(durationSeconds * context.ticksPerSecond()));
        PotionEffect effect = new PotionEffect(PotionEffectType.LEVITATION, (int) durationTicks, amplifier, false, particles, icon);
        target.addPotionEffect(effect, true);
    }
}
