package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FeatherInfuse extends BaseInfuse {
    public FeatherInfuse() {
        super(EffectGroup.PRIMARY, 5, "feather", InfuseItem.PRIMARY_FEATHER);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE018" : "\uE006");
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&2&l" : "&f&l");
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 2);
        context.applyPotion(player, PotionEffectType.LEVITATION, 30, 2, false, false);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 0, 30);
        }, 2L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, PlayerData data, InfuseContext context) {
        Player player = event.getPlayer();
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 5) || player.isSneaking()) {
            return;
        }
        Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        Material below2 = player.getLocation().clone().subtract(0, 2, 0).getBlock().getType();
        Material below3 = player.getLocation().clone().subtract(0, 3, 0).getBlock().getType();
        boolean onLiquid = isWaterOrLava(below) || isWaterOrLava(below2) || isWaterOrLava(below3);
        boolean headLiquid = isWaterOrLava(player.getEyeLocation().getBlock().getType());
        if (onLiquid && !headLiquid) {
            List<org.bukkit.block.Block> blocks = new ArrayList<>();
            org.bukkit.block.Block center = player.getLocation().getBlock();
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -2; z <= 2; z++) {
                        org.bukkit.block.Block block = center.getRelative(x, y, z);
                        if (block.getType() == Material.WATER) {
                            blocks.add(block);
                            block.setType(Material.ICE);
                        } else if (block.getType() == Material.LAVA) {
                            blocks.add(block);
                            block.setType(Material.OBSIDIAN);
                        }
                    }
                }
            }
            Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
                for (org.bukkit.block.Block block : blocks) {
                    if (block.getLocation().equals(player.getLocation().clone().subtract(0, 1, 0).getBlock().getLocation())) {
                        waitForPlayerLeave(player, block, context);
                    } else {
                        revertBlock(block);
                    }
                }
            }, 3L * InfuseConstants.TICKS_PER_SECOND);
        }
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 5) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private void waitForPlayerLeave(Player player, org.bukkit.block.Block block, InfuseContext context) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    revertBlock(block);
                    cancel();
                    return;
                }
                org.bukkit.block.Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                if (!below.getLocation().equals(block.getLocation())) {
                    revertBlock(block);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), 1L, 1L);
    }

    private void revertBlock(org.bukkit.block.Block block) {
        if (block.getType() == Material.ICE) {
            block.setType(Material.WATER);
        } else if (block.getType() == Material.OBSIDIAN) {
            block.setType(Material.LAVA);
        }
    }

    private boolean isWaterOrLava(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }
}
