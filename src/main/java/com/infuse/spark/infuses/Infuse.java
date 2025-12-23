package com.infuse.spark.infuses;

import com.infuse.spark.PlayerData;
import com.infuse.spark.EffectGroup;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public interface Infuse {
    EffectGroup getGroup();

    int getEffectId();

    String getKey();

    com.infuse.spark.InfuseItems.InfuseItem getItem();

    void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context);

    void activate(Player player, PlayerData data, int slot, InfuseContext context);

    default void onTick(Player player, PlayerData data, InfuseContext context) {
    }

    default void onPlayerMove(PlayerMoveEvent event, PlayerData data, InfuseContext context) {
    }

    default void onEntityDamage(EntityDamageEvent event, PlayerData data, InfuseContext context) {
    }

    default void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
    }

    default void onBlockBreak(BlockBreakEvent event, PlayerData data, InfuseContext context) {
    }

    default void onEntityDeath(EntityDeathEvent event, PlayerData data, InfuseContext context) {
    }

    default void onPlayerQuit(PlayerQuitEvent event, PlayerData data, InfuseContext context) {
    }

    default void onDisable(Player player, PlayerData data, InfuseContext context) {
    }
}
