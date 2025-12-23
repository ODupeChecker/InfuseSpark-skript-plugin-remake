package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class StrengthInfuse extends BaseInfuse {
    private static final double STRENGTH_DAMAGE_BASE = 2.0;
    private static final double STRENGTH_DAMAGE_SPARK = 1.5;
    private static final UUID STRENGTH_MODIFIER = UUID.fromString("f7d5d5c4-5d1b-4b92-9ee4-0c8c293b5f5a");
    private static final UUID STRENGTH_SPARK_MODIFIER = UUID.fromString("6b0e9d41-90d8-447f-b6ab-3000f84509ee");

    public StrengthInfuse() {
        super(EffectGroup.PRIMARY, 1, "strength", InfuseItem.PRIMARY_STRENGTH);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE014" : "\uE002");
        applyStrengthEquipped(player, data, context);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&4&l" : "&f&l");
        }
    }

    private void applyStrengthEquipped(Player player, PlayerData data, InfuseContext context) {
        context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_MODIFIER,
            STRENGTH_DAMAGE_BASE, 4);
        if (data.isStrengthSparkActive()) {
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, STRENGTH_SPARK_MODIFIER,
                STRENGTH_DAMAGE_SPARK, 4);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 30);
        data.setStrengthSparkActive(true);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            data.setStrengthSparkActive(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 2, 0);
        }, 30L * InfuseConstants.TICKS_PER_SECOND);
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
