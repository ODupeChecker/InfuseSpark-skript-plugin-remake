package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

public class HeartInfuse extends BaseInfuse {
    private static final UUID HEART_EQUIP_MODIFIER = UUID.fromString("1f57d91f-1f48-4c91-bbb5-7e8d7a4b59a4");
    private static final UUID HEART_SPARK_MODIFIER = UUID.fromString("8f7625b1-2f43-4a28-9e1c-c5c1c4e5c169");
    private final Set<UUID> heartEquipApplied = new HashSet<>();

    public HeartInfuse() {
        super(EffectGroup.PRIMARY, 2, "heart", InfuseItem.PRIMARY_HEART);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE015" : "\uE003");
        applyHeartEquipped(player, context);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&5&l" : "&f&l");
        }
    }

    private void applyHeartEquipped(Player player, InfuseContext context) {
        if (heartEquipApplied.contains(player.getUniqueId())) {
            return;
        }
        context.applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER, 10.0);
        heartEquipApplied.add(player.getUniqueId());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 2) && heartEquipApplied.contains(player.getUniqueId())) {
            context.removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER);
            heartEquipApplied.remove(player.getUniqueId());
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 30);
        context.applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER, 10.0);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            context.removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 1, 0);
        }, 30L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(event.getPlayer(), Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
    }
}
