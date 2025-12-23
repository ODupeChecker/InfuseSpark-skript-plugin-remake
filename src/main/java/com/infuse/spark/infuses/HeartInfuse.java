package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        applyHeartEquipped(player, context);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    private void applyHeartEquipped(Player player, InfuseContext context) {
        if (heartEquipApplied.contains(player.getUniqueId())) {
            return;
        }
        double equipHealth = getDouble(context, PASSIVE_SECTION, "max-health", 0.0);
        context.applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_EQUIP_MODIFIER, equipHealth);
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
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        double sparkHealth = getDouble(context, SPARK_SECTION, "max-health", 0.0);
        context.applyAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER, sparkHealth);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            context.removeAttributeModifier(player, Attribute.GENERIC_MAX_HEALTH, HEART_SPARK_MODIFIER);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
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
