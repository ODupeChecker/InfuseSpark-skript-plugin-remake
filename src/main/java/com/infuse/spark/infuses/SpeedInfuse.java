package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class SpeedInfuse extends BaseInfuse {
    public SpeedInfuse() {
        super(EffectGroup.SUPPORT, 4, "speed", InfuseItem.SUPPORT_SPEED);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE025&e&l" : "\uE013&f&l");
        context.applyPotion(player, PotionEffectType.SPEED, 2, 2, false, false);
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 1);
        Vector direction = player.getLocation().getDirection().normalize().multiply(2);
        player.setVelocity(direction);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 0, 15);
        }, InfuseConstants.TICKS_PER_SECOND);
    }
}
