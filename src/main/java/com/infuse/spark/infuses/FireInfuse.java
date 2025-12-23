package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class FireInfuse extends BaseInfuse {
    private static final UUID FIRE_ATTACK_MODIFIER = UUID.fromString("b7db23cc-fba1-4f7a-8896-9cf813f6a47b");

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
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.SUPPORT, 2) && player.getFireTicks() > 0) {
            event.setCancelled(true);
            if (data.isFireSparkActive()) {
                int regenLevel = getInt(context, SPARK_SECTION, "regen-level", 0);
                int regenDuration = getInt(context, SPARK_SECTION, "regen-duration-seconds", 0);
                boolean regenParticles = getBoolean(context, SPARK_SECTION, "regen-particles", false);
                boolean regenIcon = getBoolean(context, SPARK_SECTION, "regen-icon", false);
                context.applyPotion(player, PotionEffectType.REGENERATION, regenLevel, regenDuration, regenParticles, regenIcon);
            }
            double attackDamage = getDouble(context, PASSIVE_SECTION, "attack-damage", 0.0);
            int attackRefreshTicks = getInt(context, PASSIVE_SECTION, "attack-damage-refresh-ticks", 0);
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER, attackDamage, attackRefreshTicks);
            int fireResistLevel = getInt(context, PASSIVE_SECTION, "fire-resistance-level", 0);
            int fireResistDuration = getInt(context, PASSIVE_SECTION, "fire-resistance-duration-seconds", 0);
            boolean fireResistParticles = getBoolean(context, PASSIVE_SECTION, "fire-resistance-particles", false);
            boolean fireResistIcon = getBoolean(context, PASSIVE_SECTION, "fire-resistance-icon", false);
            context.applyPotion(player, PotionEffectType.FIRE_RESISTANCE, fireResistLevel, fireResistDuration, fireResistParticles, fireResistIcon);
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(event.getPlayer(), Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER);
    }
}
