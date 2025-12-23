package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

public class FrostInfuse extends BaseInfuse {
    public FrostInfuse() {
        super(EffectGroup.PRIMARY, 6, "frost", InfuseItem.PRIMARY_FROST);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE019" : "\uE007");
        applyFrostEquipped(player, context);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&b&l" : "&f&l");
        }
    }

    private void applyFrostEquipped(Player player, InfuseContext context) {
        boolean hasIce = false;
        boolean hasSnow = false;
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Material material = player.getLocation().clone().add(x, y, z).getBlock().getType();
                    if (material == Material.ICE || material == Material.BLUE_ICE || material == Material.PACKED_ICE) {
                        hasIce = true;
                    }
                    if (material == Material.SNOW || material == Material.SNOW_BLOCK) {
                        hasSnow = true;
                    }
                }
            }
        }
        if (hasIce) {
            context.applyPotion(player, PotionEffectType.SPEED, 10, 2, false, false);
        } else if (hasSnow) {
            context.applyPotion(player, PotionEffectType.SPEED, 3, 2, false, false);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 30);
        data.setFrostSparkActive(true);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 1, 0);
        }, 30L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 6) && data.isFrostSparkActive() && event.getEntity() instanceof LivingEntity victim) {
            if (victim.getFreezeTicks() >= InfuseConstants.TICKS_PER_SECOND) {
                event.setDamage(event.getDamage() + 3);
            }
            victim.setFreezeTicks(InfuseConstants.TICKS_PER_SECOND * 30);
        }
    }
}
