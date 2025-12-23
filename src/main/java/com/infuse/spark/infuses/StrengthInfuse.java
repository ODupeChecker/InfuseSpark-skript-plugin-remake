package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class StrengthInfuse extends BaseInfuse {
    private static final UUID STRENGTH_MODIFIER = UUID.fromString("f7d5d5c4-5d1b-4b92-9ee4-0c8c293b5f5a");
    private static final UUID STRENGTH_SPARK_MODIFIER = UUID.fromString("6b0e9d41-90d8-447f-b6ab-3000f84509ee");

    public StrengthInfuse() {
        super(EffectGroup.PRIMARY, 1, "strength", InfuseItem.PRIMARY_STRENGTH);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        applyStrengthEquipped(player, data, context);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    private void applyStrengthEquipped(Player player, PlayerData data, InfuseContext context) {
        double baseDamage = getDouble(context, PASSIVE_SECTION, "attack-damage", 0.0);
        int refreshTicks = getInt(context, PASSIVE_SECTION, "refresh-ticks", 0);
        context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_MODIFIER,
            baseDamage, refreshTicks);
        if (data.isStrengthSparkActive()) {
            double sparkDamage = getDouble(context, SPARK_SECTION, "attack-damage", 0.0);
            int sparkRefreshTicks = getInt(context, SPARK_SECTION, "refresh-ticks", 0);
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_SPARK_MODIFIER,
                sparkDamage, sparkRefreshTicks);
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
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        removeModifiers(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        removeModifiers(player, context);
    }

    private void removeModifiers(Player player, InfuseContext context) {
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_MODIFIER);
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_SPARK_MODIFIER);
    }
}
