package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class SpeedInfuse extends BaseInfuse {
    public SpeedInfuse() {
        super(EffectGroup.SUPPORT, 4, "speed", InfuseItem.SUPPORT_SPEED);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        int level = getInt(context, PASSIVE_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, PASSIVE_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.SPEED, level, durationSeconds, particles, icon);
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        double speedMultiplier = getDouble(context, SPARK_SECTION, "velocity-multiplier", 0.0);
        Vector direction = player.getLocation().getDirection().normalize().multiply(speedMultiplier);
        player.setVelocity(direction);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        context.getPlugin().getServer().getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }
}
