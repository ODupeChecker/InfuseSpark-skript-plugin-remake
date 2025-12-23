package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

public class InvisibilityInfuse extends BaseInfuse {
    private final Set<UUID> invisibilityHidden = new HashSet<>();

    public InvisibilityInfuse() {
        super(EffectGroup.PRIMARY, 4, "invisibility", InfuseItem.PRIMARY_INVISIBILITY);
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
        context.applyPotion(player, PotionEffectType.INVISIBILITY, level, durationSeconds, particles, icon);
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
        hidePlayerFromAll(player, context);
        int durationSeconds = getInt(context, SPARK_SECTION, "duration-seconds", 0);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            revealPlayerToAll(player, context);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }, (long) durationSeconds * context.ticksPerSecond());
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        revealPlayerToAll(event.getPlayer(), context);
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        revealPlayerToAll(player, context);
    }

    private void hidePlayerFromAll(Player player, InfuseContext context) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.hidePlayer(context.getPlugin(), player);
        }
        invisibilityHidden.add(player.getUniqueId());
    }

    private void revealPlayerToAll(Player player, InfuseContext context) {
        if (!invisibilityHidden.contains(player.getUniqueId())) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) {
                continue;
            }
            target.showPlayer(context.getPlugin(), player);
        }
        invisibilityHidden.remove(player.getUniqueId());
    }
}
