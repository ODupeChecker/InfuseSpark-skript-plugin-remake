package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

public class PigInfuse extends BaseInfuse {
    private static final UUID PIG_KNOCKBACK_MODIFIER = UUID.fromString("e3a44368-b83e-49a6-a79c-0d0c5978a9c8");
    private final Map<UUID, Integer> pigHitCounts = new HashMap<>();
    private final Set<UUID> pigKnockbackApplied = new HashSet<>();

    public PigInfuse() {
        super(EffectGroup.PRIMARY, 9, "pig", InfuseItem.PRIMARY_PIG);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE027" : "\uE026");
        applyPigEquipped(player, context);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&d&l" : "&f&l");
        }
    }

    private void applyPigEquipped(Player player, InfuseContext context) {
        if (pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        context.applyAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER, 0.05);
        pigKnockbackApplied.add(player.getUniqueId());
    }

    private void removePigKnockback(Player player, InfuseContext context) {
        if (!pigKnockbackApplied.contains(player.getUniqueId())) {
            return;
        }
        context.removeAttributeModifier(player, Attribute.GENERIC_KNOCKBACK_RESISTANCE, PIG_KNOCKBACK_MODIFIER);
        pigKnockbackApplied.remove(player.getUniqueId());
    }

    @Override
    public void onTick(Player player, PlayerData data, InfuseContext context) {
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)) {
            pigHitCounts.remove(player.getUniqueId());
            removePigKnockback(player, context);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 50);
        data.setPigSparkPrimed(true);
        player.playSound(player.getLocation(), Sound.ENTITY_PIG_AMBIENT, 1f, 1.2f);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            if (!SlotHelper.isSlotActive(data, slot) || !data.isPigSparkPrimed()) {
                return;
            }
            data.setPigSparkPrimed(false);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 1, 20);
        }, 50L * InfuseConstants.TICKS_PER_SECOND);
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)
            || !SlotHelper.isEffectActive(data, EffectGroup.PRIMARY, 9)
            || !data.isPigSparkPrimed()) {
            return;
        }
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth > 8.0) {
            return;
        }
        if (finalHealth <= 0) {
            event.setDamage(Math.max(0.0, player.getHealth() - 1.0));
        }
        int slot = SlotHelper.getActiveSlotForEffect(data, EffectGroup.PRIMARY, 9);
        if (slot == 0) {
            return;
        }
        triggerPigSparkHeal(player, data, slot, context);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (!SlotHelper.hasEffect(data, EffectGroup.PRIMARY, 9)) {
            return;
        }
        int hits = pigHitCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        if (hits >= 5) {
            pigHitCounts.put(player.getUniqueId(), 0);
            context.applyPotion(player, PotionEffectType.SPEED, 3, 5, false, false);
        } else {
            pigHitCounts.put(player.getUniqueId(), hits);
        }
    }

    private void triggerPigSparkHeal(Player player, PlayerData data, int slot, InfuseContext context) {
        if (!data.isPigSparkPrimed()) {
            return;
        }
        data.setPigSparkPrimed(false);
        SlotHelper.setSlotActive(data, slot, false);
        SlotHelper.setSlotCooldown(data, slot, 1, 20);
        Bukkit.getScheduler().runTask(context.getPlugin(), () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = maxHealth != null ? maxHealth.getValue() : 20.0;
            player.setHealth(Math.min(max, player.getHealth() + 10.0));
        });
    }

    @Override
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        pigHitCounts.remove(event.getPlayer().getUniqueId());
        removePigKnockback(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        removePigKnockback(player, context);
    }
}
