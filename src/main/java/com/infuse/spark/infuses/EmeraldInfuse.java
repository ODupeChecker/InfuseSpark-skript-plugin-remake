package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class EmeraldInfuse extends BaseInfuse {
    public EmeraldInfuse() {
        super(EffectGroup.SUPPORT, 3, "emerald", InfuseItem.SUPPORT_EMERALD);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE024&a&l" : "\uE012&f&l");
        context.applyPotion(player, PotionEffectType.HERO_OF_THE_VILLAGE, 3, 2, false, false);
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 1, 30);
        context.applyPotion(player, PotionEffectType.HERO_OF_THE_VILLAGE, 200, 90, false, false);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 5, 0);
        }, 90L * InfuseConstants.TICKS_PER_SECOND);
    }
}
