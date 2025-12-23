package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
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
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        int level = getInt(context, PASSIVE_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, PASSIVE_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, PASSIVE_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, PASSIVE_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.HASTE, level, durationSeconds, particles, icon);
        if (slot == 1) {
            String activeColor = getString(context, PASSIVE_SECTION, "primary-color-active", "");
            String inactiveColor = getString(context, PASSIVE_SECTION, "primary-color-inactive", "");
            data.setPrimaryColorCode(active ? activeColor : inactiveColor);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        int level = getInt(context, SPARK_SECTION, "potion-level", 0);
        int durationSeconds = getInt(context, SPARK_SECTION, "potion-duration-seconds", 0);
        boolean particles = getBoolean(context, SPARK_SECTION, "potion-particles", false);
        boolean icon = getBoolean(context, SPARK_SECTION, "potion-icon", false);
        context.applyPotion(player, PotionEffectType.HASTE, level, durationSeconds, particles, icon);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        int sparkDurationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        org.bukkit.Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) sparkDurationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event, PlayerData data, InfuseContext context) {
        Player player = event.getPlayer();
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 3)) {
            return;
        }
        Material type = event.getBlock().getType();
        if (!getExtraDrops(context).contains(type)) {
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

    private java.util.Set<Material> getExtraDrops(InfuseContext context) {
        java.util.List<String> materialKeys = getStringList(context, PASSIVE_SECTION, "extra-drop-materials", java.util.List.of());
        java.util.Set<Material> materials = new java.util.HashSet<>();
        for (String key : materialKeys) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                context.getPlugin().getLogger().warning("Unknown material in haste.extra-drop-materials: " + key);
                continue;
            }
            materials.add(material);
        }
        return materials;
    }
}
