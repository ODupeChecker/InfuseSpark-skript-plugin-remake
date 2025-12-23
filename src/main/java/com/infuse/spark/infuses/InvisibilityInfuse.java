package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
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
        SlotHelper.setSlotActionBar(data, slot, active ? "\uE017" : "\uE005");
        context.applyPotion(player, PotionEffectType.INVISIBILITY, 1, 2, true, true);
        if (slot == 1) {
            data.setPrimaryColorCode(active ? "&5&l" : "&f&l");
        }
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        SlotHelper.setSlotActive(data, slot, true);
        SlotHelper.setSlotCooldown(data, slot, 0, 20);
        hidePlayerFromAll(player, context);
        Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> {
            revealPlayerToAll(player, context);
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, 0, 45);
        }, 20L * InfuseConstants.TICKS_PER_SECOND);
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
