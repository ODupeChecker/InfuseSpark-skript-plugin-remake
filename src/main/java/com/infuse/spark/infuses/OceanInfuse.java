package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.UUID;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class OceanInfuse extends BaseInfuse {
    private static final UUID OCEAN_ATTACK_MODIFIER = UUID.fromString("1a91d0a7-4f22-4a7a-9dfe-f2b7d8b5c934");

    public OceanInfuse() {
        super(EffectGroup.SUPPORT, 1, "ocean", InfuseItem.SUPPORT_OCEAN);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
        applyOceanEquipped(player, context);
    }

    private void applyOceanEquipped(Player player, InfuseContext context) {
        int dolphinsLevel = getInt(context, PASSIVE_SECTION, "dolphins-grace-level", 0);
        int dolphinsDuration = getInt(context, PASSIVE_SECTION, "dolphins-grace-duration-seconds", 0);
        boolean dolphinsParticles = getBoolean(context, PASSIVE_SECTION, "dolphins-grace-particles", false);
        boolean dolphinsIcon = getBoolean(context, PASSIVE_SECTION, "dolphins-grace-icon", false);
        context.applyPotion(player, PotionEffectType.DOLPHINS_GRACE, dolphinsLevel, dolphinsDuration, dolphinsParticles, dolphinsIcon);
        int conduitLevel = getInt(context, PASSIVE_SECTION, "conduit-power-level", 0);
        int conduitDuration = getInt(context, PASSIVE_SECTION, "conduit-power-duration-seconds", 0);
        boolean conduitParticles = getBoolean(context, PASSIVE_SECTION, "conduit-power-particles", false);
        boolean conduitIcon = getBoolean(context, PASSIVE_SECTION, "conduit-power-icon", false);
        context.applyPotion(player, PotionEffectType.CONDUIT_POWER, conduitLevel, conduitDuration, conduitParticles, conduitIcon);
        if (player.getLocation().getBlock().isLiquid()) {
            double attackDamage = getDouble(context, PASSIVE_SECTION, "attack-damage", 0.0);
            int refreshTicks = getInt(context, PASSIVE_SECTION, "attack-damage-refresh-ticks", 0);
            context.applyTemporaryAttributeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, OCEAN_ATTACK_MODIFIER,
                attackDamage, refreshTicks);
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);
        int tickCount = getInt(context, SPARK_SECTION, "tick-count", 0);
        int tickInterval = Math.max(1, getInt(context, SPARK_SECTION, "tick-interval-ticks", 0));
        int regenLevel = getInt(context, SPARK_SECTION, "regen-level", 0);
        int regenDuration = getInt(context, SPARK_SECTION, "regen-duration-seconds", 0);
        boolean regenParticles = getBoolean(context, SPARK_SECTION, "regen-particles", false);
        boolean regenIcon = getBoolean(context, SPARK_SECTION, "regen-icon", false);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getLocation().getBlock().isLiquid()) {
                    context.applyPotion(player, PotionEffectType.REGENERATION, regenLevel, regenDuration, regenParticles, regenIcon);
                }
                count++;
                if (count >= tickCount) {
                    SlotHelper.setSlotActive(data, slot, false);
                    SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
                    cancel();
                }
            }
        }.runTaskTimer(context.getPlugin(), 0L, tickInterval);
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
