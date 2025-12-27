package com.infuse.spark;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

public class InfuseItems {
    public enum InfuseItem {
        SUPPORT_EMERALD,
        SUPPORT_FIRE,
        SUPPORT_OCEAN,
        SUPPORT_SPEED,
        PRIMARY_FEATHER,
        PRIMARY_FROST,
        PRIMARY_HASTE,
        PRIMARY_HEART,
        PRIMARY_NINJA,
        PRIMARY_REGENERATION,
        PRIMARY_STRENGTH,
        PRIMARY_THUNDER,
        PRIMARY_PIG,
        PRIMARY_PIGLIN,
        PRIMARY_OVERDRIVE
    }

    private final InfuseSparkPlugin plugin;
    private final Map<InfuseItem, ItemStack> items = new EnumMap<>(InfuseItem.class);

    public InfuseItems(InfuseSparkPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerItems() {
        items.put(InfuseItem.SUPPORT_EMERALD, createPotion(
            "&aEmerald Effect",
            Color.fromRGB(144, 238, 144),
            List.of(
                "&aEmerald Effect",
                "&8No Effects",
                "",
                "&a&lPassive effects:",
                "&a✧ Grants Hero of the Village III",
                "",
                "&a&lSpark effect:",
                "&a✧ Upgrades Hero of the Village to ∞",
                "",
                "&7duration: &a90s &7cooldown: &a300s"
            ),
            InfuseItem.SUPPORT_EMERALD
        ));

        items.put(InfuseItem.SUPPORT_FIRE, createPotion(
            "&6Fire Effect",
            Color.ORANGE,
            List.of(
                "&6Fire Effect",
                "&8No Effects",
                "",
                "&6&lPassive effects:",
                "&6🔥 Ignite enemies every 10 hits for 4s",
                "&6🔥 Deal &c+0.5❤ &6true damage to ignited enemies",
                "",
                "&6&lSpark effect:",
                "&6🔥 Shoot a fireball dealing &c3.5❤ &6true damage",
                "&6🔥 Ignite enemies within 5 blocks for 4s",
                "",
                "&7duration: &61s &7cooldown: &660s"
            ),
            InfuseItem.SUPPORT_FIRE
        ));

        items.put(InfuseItem.SUPPORT_OCEAN, createPotion(
            "&3Ocean Effect",
            Color.BLUE,
            List.of(
                "&3Ocean Effect",
                "&8No Effects",
                "",
                "&3&lPassive effects:",
                "&3🌊 Grants Dolphin's Grace",
                "&3🌊 Grants Conduit Power",
                "&3🌊 +2 Attack Damage while in water",
                "",
                "&3&lSpark effect:",
                "&3🌊 Regenerate health while in water",
                "",
                "&7duration: &330s &7cooldown: &360s"
            ),
            InfuseItem.SUPPORT_OCEAN
        ));

        items.put(InfuseItem.SUPPORT_SPEED, createPotion(
            "&bSpeed Effect",
            Color.AQUA,
            List.of(
                "&bSpeed Effect",
                "&8No Effects",
                "",
                "&b&lPassive effects:",
                "&b💨 Grants Speed II",
                "",
                "&b&lSpark effect:",
                "&b💨 Dash forward",
                "",
                "&7duration: &b1s &7cooldown: &b15s"
            ),
            InfuseItem.SUPPORT_SPEED
        ));

        items.put(InfuseItem.PRIMARY_FEATHER, createPotion(
            "&7Feather Effect",
            Color.fromRGB(192, 192, 192),
            List.of(
                "&7Feather Effect",
                "&8No Effects",
                "",
                "&7&lPassive effects:",
                "&7✧ 5% chance to inflict Levitation II for 2s on hit",
                "",
                "&7&lSpark effect:",
                "&7✧ Enemies within 8 blocks enter a levitation cycle",
                "&7✧ 2s up, 0.7s down (3 times)",
                "",
                "&7duration: &79s &7cooldown: &730s"
            ),
            InfuseItem.PRIMARY_FEATHER
        ));

        items.put(InfuseItem.PRIMARY_FROST, createPotion(
            "&3Frost Effect",
            Color.AQUA,
            List.of(
                "&3Frost Effect",
                "&8No Effects",
                "",
                "&3&lPassive effects:",
                "&3❄ Speed X on Ice",
                "&3❄ Speed III on Snow",
                "",
                "&3&lSpark effect:",
                "&3❄ Inflict Frostbite on attacked enemies",
                "&3❄ Deal &c+3❤ &3damage to frosted enemies",
                "",
                "&7duration: &330s &7cooldown: &390s"
            ),
            InfuseItem.PRIMARY_FROST
        ));

        items.put(InfuseItem.PRIMARY_HASTE, createPotion(
            "&6Haste Effect",
            Color.YELLOW,
            List.of(
                "&6Haste Effect",
                "&8No Effects",
                "",
                "&6&lPassive effects:",
                "&6⛏ Grants Haste II",
                "&6⛏ Grants Fortune IV",
                "",
                "&6&lSpark effect:",
                "&6⛏ Upgrades Haste II to Instamine",
                "",
                "&7duration: &645s &7cooldown: &675s"
            ),
            InfuseItem.PRIMARY_HASTE
        ));

        items.put(InfuseItem.PRIMARY_HEART, createPotion(
            "&4Heart Effect",
            Color.RED,
            List.of(
                "&4Heart Effect",
                "&8No Effects",
                "",
                "&4&lPassive effects:",
                "&4❤ Gain &c+3❤ &4extra hearts",
                "",
                "&4&lSpark effect:",
                "&4❤ Gain &c+3❤ &4more hearts and heal fully",
                "",
                "&7duration: &41m20s &7cooldown: &42m"
            ),
            InfuseItem.PRIMARY_HEART
        ));

        items.put(InfuseItem.PRIMARY_NINJA, createPotion(
            "&7Ninja Effect",
            Color.PURPLE,
            List.of(
                "&7Ninja Effect",
                "&8No Effects",
                "",
                "&5&lPassive effects:",
                "&5✦ Stand still 5s to gain TRUE invisibility",
                "&5✦ Hits while invisible deal &c+30% &5damage",
                "",
                "&5&lSpark effect:",
                "&5✦ Gain TRUE invisibility for 15s",
                "&5✦ Deal &c+50% &5damage",
                "&5✦ Take &c+90% &5damage",
                "",
                "&7Passive CD: &530s &7Spark CD: &51m30s"
            ),
            InfuseItem.PRIMARY_NINJA
        ));

        items.put(InfuseItem.PRIMARY_REGENERATION, createPotion(
            "&4Regeneration Effect",
            Color.RED,
            List.of(
                "&4Regeneration Effect",
                "&8No Effects",
                "",
                "&4&lPassive effects:",
                "&4✚ Hitting enemies grants Regeneration II for 1s",
                "",
                "&4&lSpark effect:",
                "&4✚ Heal for &c25% &4of health lost by enemies you hit",
                "",
                "&7duration: &420s &7cooldown: &41m10s"
            ),
            InfuseItem.PRIMARY_REGENERATION
        ));

        items.put(InfuseItem.PRIMARY_STRENGTH, createPotion(
            "&cStrength Effect",
            Color.fromRGB(139, 0, 0),
            List.of(
                "&cStrength Effect",
                "&8No Effects",
                "",
                "&c&lPassive effects:",
                "&c💪 Deal &c+0.5❤ &cbelow 6❤",
                "&c💪 Deal &c+2❤ &cbelow 4❤",
                "&c💪 Deal &c+3❤ &cbelow 2❤",
                "",
                "&c&lSpark effect:",
                "&c💪 All properly timed hits become Critical Hits",
                "",
                "&7duration: &c10s &7cooldown: &c60s"
            ),
            InfuseItem.PRIMARY_STRENGTH
        ));

        items.put(InfuseItem.PRIMARY_THUNDER, createPotion(
            "&3Thunder Effect",
            Color.YELLOW,
            List.of(
                "&3Thunder Effect",
                "&8No Effects",
                "",
                "&e&lPassive effects:",
                "&e⚡ Strike enemies with Lightning every 10th hit",
                "&e⚡ Deal &c0.5❤ &etrue damage",
                "",
                "&e&lSpark effect:",
                "&e⚡ Smite enemies within an 8 block radius",
                "&e⚡ Deal &c3❤ &etrue damage and camera lock for 5s",
                "",
                "&7duration: &310s &7cooldown: &3120s"
            ),
            InfuseItem.PRIMARY_THUNDER
        ));

        items.put(InfuseItem.PRIMARY_PIG, createPotion(
            "&d&lPig Effect",
            Color.fromRGB(255, 105, 180),
            List.of(
                "&dPig Effect",
                "&8No Effects",
                "",
                "&d&lPassive effects:",
                "&d🐷 After being hit 5 times gain Speed III for 5s",
                "&d🐷 Take 5% less knockback",
                "",
                "&d&lSpark effect:",
                "&d🐷 Send out a barrage of exploding baby pigs",
                "",
                "&7duration: &d1s &7cooldown: &d30s"
            ),
            InfuseItem.PRIMARY_PIG
        ));

        items.put(InfuseItem.PRIMARY_PIGLIN, createPotion(
            "&4&lPiglin Effect",
            Color.fromRGB(139, 0, 0),
            List.of(
                "&4Piglin Effect",
                "&8No Effects",
                "",
                "&4&lPassive effects:",
                "&4⚔ Mark attackers for 2s",
                "&4⚔ Deal &c+1.25❤ &4damage to marked targets",
                "",
                "&4&lSpark effect:",
                "&4⚔ Deal &c+1.8❤ &4damage to marked targets",
                "",
                "&7duration: &430s &7cooldown: &430s"
            ),
            InfuseItem.PRIMARY_PIGLIN
        ));

        items.put(InfuseItem.PRIMARY_OVERDRIVE, createPotion(
            "&6Overdrive Effect",
            Color.fromRGB(255, 140, 0),
            List.of(
                "&6Overdrive Effect",
                "&8No Effects",
                "",
                "&6&lPassive effects:",
                "&6🔁 40% chance to mirror hits",
                "&6🔁 Echoed hits deal -20% damage",
                "",
                "&6&lSpark effect:",
                "&6🔁 100% chance to mirror hits",
                "&6🔁 Echoed hits deal -10% damage",
                "",
                "&7duration: &66s &7cooldown: &660s"
            ),
            InfuseItem.PRIMARY_OVERDRIVE
        ));
    }

    public void registerRecipes() {
        registerRecipe("emerald_effect", items.get(InfuseItem.SUPPORT_EMERALD),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.EMERALD_BLOCK),
            new Ingredient('b', Material.BELL),
            new Ingredient('c', Material.END_CRYSTAL)
        );

        registerRecipe("fire_effect", items.get(InfuseItem.SUPPORT_FIRE),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.MAGMA_CREAM),
            new Ingredient('b', Material.FIRE_CHARGE),
            new Ingredient('c', Material.CRYING_OBSIDIAN)
        );

        registerRecipe("ocean_effect", items.get(InfuseItem.SUPPORT_OCEAN),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.PRISMARINE_SHARD),
            new Ingredient('b', Material.SEA_LANTERN),
            new Ingredient('c', Material.HEART_OF_THE_SEA)
        );

        registerRecipe("speed_effect", items.get(InfuseItem.SUPPORT_SPEED),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.SUGAR),
            new Ingredient('b', Material.AMETHYST_SHARD),
            new Ingredient('c', Material.RABBIT_FOOT)
        );

        registerRecipe("feather_effect", items.get(InfuseItem.PRIMARY_FEATHER),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.FEATHER),
            new Ingredient('b', Material.GOAT_HORN),
            new Ingredient('c', Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE),
            new Ingredient('d', Material.TRIDENT)
        );

        registerRecipe("frost_effect", items.get(InfuseItem.PRIMARY_FROST),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.BLUE_ICE),
            new Ingredient('b', Material.POWDER_SNOW_BUCKET),
            new Ingredient('c', Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new Ingredient('d', Material.CONDUIT)
        );

        registerRecipe("haste_effect", items.get(InfuseItem.PRIMARY_HASTE),
            "aba", "cdc", "aea",
            new Ingredient('a', Material.DIAMOND_BLOCK),
            new Ingredient('b', Material.NETHERITE_PICKAXE),
            new Ingredient('c', Material.GOLD_BLOCK),
            new Ingredient('d', Material.BEACON),
            new Ingredient('e', Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE)
        );

        registerRecipe("heart_effect", items.get(InfuseItem.PRIMARY_HEART),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.TOTEM_OF_UNDYING),
            new Ingredient('b', Material.DIAMOND_BLOCK),
            new Ingredient('c', Material.ENCHANTED_GOLDEN_APPLE)
        );

        registerRecipe("ninja_effect", items.get(InfuseItem.PRIMARY_NINJA),
            "aba", "bcb", "aba",
            new Ingredient('a', Material.ENDER_EYE),
            new Ingredient('b', Material.SCULK_SHRIEKER),
            new Ingredient('c', Material.RECOVERY_COMPASS)
        );

        registerRecipe("regeneration_effect", items.get(InfuseItem.PRIMARY_REGENERATION),
            "abc", "ded", "cba",
            new Ingredient('a', Material.GOLDEN_APPLE),
            new Ingredient('b', Material.HONEY_BOTTLE),
            new Ingredient('c', Material.TOTEM_OF_UNDYING),
            new Ingredient('d', Material.SUSPICIOUS_STEW),
            new Ingredient('e', Material.CAKE)
        );

        registerRecipe("strength_effect", items.get(InfuseItem.PRIMARY_STRENGTH),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.NETHER_WART),
            new Ingredient('b', Material.WITHER_SKELETON_SKULL),
            new Ingredient('c', Material.NETHERITE_SWORD),
            new Ingredient('d', Material.LODESTONE)
        );

        registerRecipe("thunder_effect", items.get(InfuseItem.PRIMARY_THUNDER),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.LIGHTNING_ROD),
            new Ingredient('b', Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new Ingredient('c', Material.TRIDENT),
            new Ingredient('d', Material.CREEPER_HEAD)
        );

        registerRecipe("pig_effect", items.get(InfuseItem.PRIMARY_PIG),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.PORKCHOP),
            new Ingredient('b', Material.GOLDEN_CARROT),
            new Ingredient('c', Material.SADDLE),
            new Ingredient('d', Material.PIGLIN_HEAD)
        );

        registerRecipe("piglin_effect", items.get(InfuseItem.PRIMARY_PIGLIN),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.GOLD_INGOT),
            new Ingredient('b', Material.NETHERITE_SCRAP),
            new Ingredient('c', Material.PIGLIN_HEAD),
            new Ingredient('d', Material.CRYING_OBSIDIAN)
        );

        registerRecipe("overdrive_effect", items.get(InfuseItem.PRIMARY_OVERDRIVE),
            "aba", "cdc", "aba",
            new Ingredient('a', Material.BLAZE_POWDER),
            new Ingredient('b', Material.NETHER_STAR),
            new Ingredient('c', Material.RABBIT_FOOT),
            new Ingredient('d', Material.NETHERITE_INGOT)
        );
    }

    public ItemStack getItem(InfuseItem item) {
        ItemStack stack = items.get(item);
        return stack == null ? null : stack.clone();
    }

    public InfuseItem getItemType(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String value = meta.getPersistentDataContainer().get(plugin.getInfuseItemKey(), PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return InfuseItem.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ItemStack createPotion(String name, Color color, List<String> lore, InfuseItem type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(colorize(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(colorize(line));
        }
        meta.setLore(colored);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.setColor(color);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(plugin.getInfuseItemKey(), PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private void registerRecipe(String keyName, ItemStack result, String row1, String row2, String row3, Ingredient... ingredients) {
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(row1, row2, row3);
        for (Ingredient ingredient : ingredients) {
            recipe.setIngredient(ingredient.symbol, ingredient.material);
        }
        plugin.getServer().addRecipe(recipe);
    }

    private record Ingredient(char symbol, Material material) {
    }
}
