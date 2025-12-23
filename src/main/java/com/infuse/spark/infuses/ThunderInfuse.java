package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ThunderInfuse extends BaseInfuse {
    public ThunderInfuse() {
        super(EffectGroup.PRIMARY, 7, "thunder", InfuseItem.PRIMARY_THUNDER);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE020" : "\uE008");
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&9&l" : "&f&l");
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 10);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (entity instanceof Player target && data.getTrusted().contains(target.getUniqueId())) {
                        continue;
                    }
                    entity.getWorld().strikeLightning(entity.getLocation());
                }
                count++;
                if (count >= 10) {
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
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7) && event.isCritical()) {
            Bukkit.getScheduler().runTask(context.getPlugin(), () -> event.getEntity().getWorld().strikeLightning(event.getEntity().getLocation()));
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 7) && event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            event.setCancelled(true);
        }
    }
}
