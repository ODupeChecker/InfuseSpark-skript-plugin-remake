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
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class PigInfuse extends BaseInfuse {
    private static final UUID PIG_KNOCKBACK_MODIFIER = UUID.fromString("e3a44368-b83e-49a6-a79c-0d0c5978a9c8");
    private final Map<UUID, Integer> pigHitCounts = new HashMap<>();
    private final Set<UUID> pigKnockbackApplied = new HashSet<>();

    public PigInfuse() {
        super(EffectGroup.PRIMARY, 9, "pig", InfuseItem.PRIMARY_PIG);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        applyPigEquipped(player, context);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    private void applyPigEquipped(Player player, InfuseContext context) {
        if (pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        double knockbackResistance = getDouble(context, PASSIVE_SECTION, "knockback-resistance", 0.0);
        context.applyAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER, knockbackResistance);
        pigKnockbackApplied.add(player.getUniqueId());
    }

    private void removePigKnockback(Player player, InfuseContext context) {
        if (!pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        context.removeAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER);
        pigKnockbackApplied.remove(player.getUniqueId());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)) {
            pigHitCounts.remove(player.getUniqueId());
            removePigKnockback(player, context);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        data.setPigSparkPrimed(true);
        float soundVolume = (float) getDouble(context, SPARK_SECTION, "sound-volume", 0.0);
        float soundPitch = (float) getDouble(context, SPARK_SECTION, "sound-pitch", 0.0);
        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, soundVolume, soundPitch);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            if (!SlotHelper.isSlotActive(data, slot) || !data.isPigSparkPrimed()) {
                return;
            }
            data.setPigSparkPrimed(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)
            || !SlotHelper.isEffectActive(data, EffectGroup.PRIMARY, 9)
            || !data.isPigSparkPrimed()) {
            return;
        }
        double finalHealth = player.getHealth() - event.getFinalDamage();
        double triggerHealth = getDouble(context, SPARK_SECTION, "trigger-health", 0.0);
        if (finalHealth > triggerHealth) {
            return;
        }
        if (finalHealth <= 0) {
            double leaveHealth = getDouble(context, SPARK_SECTION, "leave-health", 0.0);
            event.setDamage(Math.max(0.0, player.getHealth() - leaveHealth));
        }
        int slot = SlotHelper.getActiveSlotForEffect(data, EffectGroup.PRIMARY, 9);
        if (slot == 0) {
            return;
        }
        triggerPigSparkHeal(player, data, slot, context);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)) {
            return;
        }
        int hits = pigHitCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        int hitThreshold = getInt(context, PASSIVE_SECTION, "speed-hit-threshold", 0);
        if (hits >= hitThreshold) {
            pigHitCounts.put(player.getUniqueId(), 0);
            int speedLevel = getInt(context, PASSIVE_SECTION, "speed-level", 0);
            int speedDuration = getInt(context, PASSIVE_SECTION, "speed-duration-seconds", 0);
            boolean particles = getBoolean(context, PASSIVE_SECTION, "speed-particles", false);
            boolean icon = getBoolean(context, PASSIVE_SECTION, "speed-icon", false);
            context.applyPotion(player, PotionEffectType.SPEED, speedLevel, speedDuration, particles, icon);
        } else {
            pigHitCounts.put(player.getUniqueId(), hits);
        }
    }

    private void triggerPigSparkHeal(Player player, PlayerData data, int slot, InfuseContext context) {
        if (!data.isPigSparkPrimed()) {
            return;
        }
        data.setPigSparkPrimed(false);
        SlotHelper.setSlotActive(data, slot, false);
        SlotHelper.setSlotCooldown(data, slot, 1, 20);
        context.getPlugin().getServer().getScheduler().runTask(context.getPlugin(), () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxHealth != null ? maxHealth.getValue() : 20.0;
            double healAmount = getDouble(context, SPARK_SECTION, "heal-amount", 0.0);
            player.setHealth(Math.min(max, player.getHealth() + healAmount));
        });
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        pigHitCounts.remove(event.getPlayer().getUniqueId());
        removePigKnockback(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        removePigKnockback(player, context);
    }
}
