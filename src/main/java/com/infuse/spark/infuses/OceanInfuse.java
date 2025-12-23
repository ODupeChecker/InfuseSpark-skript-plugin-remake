package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class OceanInfuse extends BaseInfuse {
    private static final double OCEAN_ATTACK_DAMAGE = 2.0;
    private static final UUID OCEAN_ATTACK_MODIFIER = UUID.fromString("1a91d0a7-4f22-4a7a-9dfe-f2b7d8b5c934");

    public OceanInfuse() {
        super(EffectGroup.SUPPORT, 1, "ocean", InfuseItem.SUPPORT_OCEAN);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE022&9&l" : "\uE010&f&l");
        applyOceanEquipped(player, context);
    }

    private void applyOceanEquipped(Player player, InfuseContext context) {
        context.applyPotion(player, PotionEffectType.DOLPHINS_GRACE, 1, 2, false, false);
        context.applyPotion(player, PotionEffectType.CONDUIT_POWER, 1, 2, false, false);
        if (player.getLocation().getBlock().isLiquid()) {
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER,
                OCEAN_ATTACK_DAMAGE, 4);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 30);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().getBlock().isLiquid()) {
                    context.applyPotion(player, PotionEffectType.REGENERATION, 1, 2, false, false);
                }
                count++;
                if (count >= 60) {
                    SlotHelper.setSlotActive(data, slot, false);
                    SlotHelper.setSlotCooldown(data, slot, 1, 0);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, 10L);
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(event.getPlayer(), Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER);
    }
}
