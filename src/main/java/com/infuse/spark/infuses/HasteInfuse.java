package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffectType;

public class HasteInfuse extends BaseInfuse {
    public HasteInfuse() {
        super(EffectGroup.PRIMARY, 3, "haste", InfuseItem.PRIMARY_HASTE);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE016" : "\uE004");
        context.applyPotion(player, PotionEffectType.HASTE, 2, 2, false, false);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&6&l" : "&f&l");
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 45);
        context.applyPotion(player, PotionEffectType.HASTE, 255, 45, false, false);
        org.bukkit.Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 1, 15);
        }, 45L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event, PlayerData data, InfuseContext context) {
        Player player = event.getPlayer();
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 3)) {
            return;
        }
        Material type = event.getBlock().getType();
        Set<Material> extraDrops = Set.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.AMETHYST_CLUSTER
        );
        if (!extraDrops.contains(type)) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        org.bukkit.inventory.ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getItemMeta() != null && tool.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.SILK_TOUCH)) {
            return;
        }
        event.getBlock().getDrops(tool, player).forEach(drop -> player.getWorld().dropItemNaturally(event.getBlock().getLocation(), drop));
    }
}
