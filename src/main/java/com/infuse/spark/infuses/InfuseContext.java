package com.infuse.spark.infuses;

import com.infuse.spark.InfuseConfigManager;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class InfuseContext {
    private final Plugin plugin;
    private final InfuseItems infuseItems;
    private final InfuseConfigManager configManager;

    public InfuseContext(Plugin plugin, InfuseItems infuseItems, InfuseConfigManager configManager) {
        this.plugin = plugin;
        this.infuseItems = infuseItems;
        this.configManager = configManager;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public InfuseItems getInfuseItems() {
        return infuseItems;
    }

    public InfuseConfigManager getConfigManager() {
        return configManager;
    }

    public int ticksPerSecond() {
        return InfuseConstants.TICKS_PER_SECOND;
    }

    public void applyPotion(Player player, PotionEffectType type, int level, int seconds, boolean particles, boolean icon) {
        int amplifier = Math.max(0, level - 1);
        PotionEffect effect = new PotionEffect(type, seconds * InfuseConstants.TICKS_PER_SECOND, amplifier, false, particles, icon);
        player.addPotionEffect(effect, true);
    }

    public void applyAttributeModifier(Player player, Attribute attribute, UUID uuid, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeAttributeModifier(player, attribute, uuid);
        AttributeModifier modifier = new AttributeModifier(uuid, "infuse" + attribute.name(), amount, AttributeModifier.Operation.ADD_NUMBER);
        instance.addModifier(modifier);
    }

    public void applyTemporaryAttributeModifier(Player player, Attribute attribute, UUID uuid, double amount, int ticks) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeAttributeModifier(player, attribute, uuid);
        AttributeModifier modifier = new AttributeModifier(uuid, "infuse_temp_" + attribute.name(), amount, AttributeModifier.Operation.ADD_NUMBER);
        instance.addModifier(modifier);
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeAttributeModifier(player, attribute, uuid), ticks);
    }

    public void removeAttributeModifier(Player player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
            .filter(mod -> mod.getUniqueId().equals(uuid))
            .findFirst()
            .ifPresent(instance::removeModifier);
    }
}
