package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;

public class StrengthInfuse extends BaseInfuse {
    private static final double BELOW_SIX_HEARTS = 12.0;
    private static final double BELOW_FOUR_HEARTS = 8.0;
    private static final double BELOW_TWO_HEARTS = 4.0;

    public StrengthInfuse() {
        super(EffectGroup.PRIMARY, 1, "strength", InfuseItem.PRIMARY_STRENGTH);
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
        data.setStrengthSparkActive(true);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            data.setStrengthSparkActive(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 1)) {
            return;
        }
        applyLowHealthBonus(event, player, context);
        if (data.isStrengthSparkActive()) {
            applySparkCritical(event, player);
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
    }

    private void applyLowHealthBonus(EntityDamageByEntityEvent event, Player player, InfuseContext context) {
        double health = player.getHealth();
        double bonusDamage = 0.0;
        if (health < BELOW_TWO_HEARTS) {
            bonusDamage = getDouble(context, PASSIVE_SECTION, "bonus-damage-below-2-hearts", 0.0);
        } else if (health < BELOW_FOUR_HEARTS) {
            bonusDamage = getDouble(context, PASSIVE_SECTION, "bonus-damage-below-4-hearts", 0.0);
        } else if (health < BELOW_SIX_HEARTS) {
            bonusDamage = getDouble(context, PASSIVE_SECTION, "bonus-damage-below-6-hearts", 0.0);
        }
        if (bonusDamage > 0.0) {
            event.setDamage(event.getDamage() + bonusDamage);
        }
    }

    private void applySparkCritical(EntityDamageByEntityEvent event, Player player) {
        if (player.getAttackCooldown() < 0.9f) {
            return;
        }
        boolean critical = event.isCritical();
        if (!critical) {
            event.setDamage(event.getDamage() * 1.5);
        }
        player.getWorld().spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0.0, 1.0, 0.0),
            12, 0.2, 0.2, 0.2, 0.0);
        player.getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
    }
}
