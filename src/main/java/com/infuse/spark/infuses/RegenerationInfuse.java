package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class RegenerationInfuse extends BaseInfuse {
    public RegenerationInfuse() {
        super(EffectGroup.PRIMARY, 8, "regeneration", InfuseItem.PRIMARY_REGENERATION);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE021" : "\uE009");
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&c&l" : "&f&l");
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 15);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                context.applyPotion(player, PotionEffectType.REGENERATION, 2, 3, false, false);
                for (Player nearby : player.getWorld().getPlayers()) {
                    if (nearby.getLocation().distance(player.getLocation()) > 16) {
                        continue;
                    }
                    if (!nearby.equals(player) && data.getTrusted().contains(nearby.getUniqueId())) {
                        context.applyPotion(player, PotionEffectType.REGENERATION, 2, 3, false, false);
                    }
                }
                count++;
                if (count >= 15) {
                    SlotHelper.setSlotActive(data, slot, false);
                    SlotHelper.setSlotCooldown(data, slot, 1, 20);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 8) && event.isCritical()) {
            context.applyPotion(player, PotionEffectType.REGENERATION, 2, 4, false, false);
        }
    }
}
