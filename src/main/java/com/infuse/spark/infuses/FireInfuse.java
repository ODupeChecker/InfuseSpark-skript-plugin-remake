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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class FireInfuse extends BaseInfuse {
    private static final double FIRE_ATTACK_DAMAGE = 1.0;
    private static final UUID FIRE_ATTACK_MODIFIER = UUID.fromString("b7db23cc-fba1-4f7a-8896-9cf813f6a47b");

    public FireInfuse() {
        super(EffectGroup.SUPPORT, 2, "fire", InfuseItem.SUPPORT_FIRE);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE023&6&l" : "\uE011&f&l");
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 30);
        data.setFireSparkActive(true);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            data.setFireSparkActive(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 1, 0);
        }, 30L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (SlotHelper.hasEffect(data, EffectGroup.SUPPORT, 2) && player.getFireTicks() > 0) {
            event.setCancelled(true);
            if (data.isFireSparkActive()) {
                context.applyPotion(player, PotionEffectType.REGENERATION, 1, 2, false, false);
            }
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER, FIRE_ATTACK_DAMAGE, 20);
            context.applyPotion(player, PotionEffectType.FIRE_RESISTANCE, 1, 1, false, true);
        }
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(event.getPlayer(), Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        context.removeAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, FIRE_ATTACK_MODIFIER);
    }
}
