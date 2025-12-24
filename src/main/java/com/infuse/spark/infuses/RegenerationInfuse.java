package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

public class RegenerationInfuse extends BaseInfuse {
    public RegenerationInfuse() {
        super(EffectGroup.PRIMARY, 8, "regeneration", InfuseItem.PRIMARY_REGENERATION);
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
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 8)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        applyHitRegeneration(player, context);
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (!SlotHelper.isEffectActive(data, EffectGroup.PRIMARY, 8)) {
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        applySparkHeal(player, target, event.getFinalDamage(), context);
    }

    private void applyHitRegeneration(Player player, InfuseContext context) {
        int level = getInt(context, PASSIVE_SECTION, "hit-potion-level", 0);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "hit-potion-duration-seconds", 0);
        if (level <= 0 || durationSeconds <= 0) {
            return;
        }
        boolean particles = getBoolean(context, PASSIVE_SECTION, "hit-potion-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "hit-potion-icon", false);
        context.applyPotion(player, PotionEffectType.REGENERATION, level, durationSeconds, particles, icon);
    }

    private void applySparkHeal(Player player, LivingEntity target, double finalDamage, InfuseContext context) {
        if (finalDamage <= 0.0) {
            return;
        }
        double healPercent = getDouble(context, SPARK_SECTION, "heal-percent", 0.25);
        if (healPercent <= 0.0) {
            return;
        }
        double initialHealth = target.getHealth();
        double healthLost = Math.min(initialHealth, finalDamage);
        if (healthLost <= 0.0) {
            return;
        }
        double healAmount = healthLost * healPercent;
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealthValue = maxHealth != null ? maxHealth.getValue() : player.getMaxHealth();
        double newHealth = Math.min(maxHealthValue, player.getHealth() + healAmount);
        player.setHealth(newHealth);
    }
}
