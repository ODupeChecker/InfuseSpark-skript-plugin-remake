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
                "&9Effect",
                "&7Grants &oHero of the Village III",
                "",
                "&9Spark Ability",
                "&7Upgrades &oHero of the Village III &r&7to &o Hero of the Village ∞",
                "",
                "&8Duration: &390s &8Cooldown: &3300s"
            ),
            InfuseItem.SUPPORT_EMERALD
        ));

        items.put(InfuseItem.SUPPORT_FIRE, createPotion(
            "&6Fire Effect",
            Color.ORANGE,
            List.of(
                "&9Effect",
                "&7Every &o10 hits &7ignite enemies for &o4s",
                "&7Deal &c+0.5❤ true damage &7to ignited enemies",
                "",
                "&9Spark Ability",
                "&7Shoot a fireball that deals &c3.5❤ true damage",
                "&7Ignites enemies within &o5 blocks &7for &o4s",
                "",
                "&8Duration: &31s &8Cooldown: &360s"
            ),
            InfuseItem.SUPPORT_FIRE
        ));

        items.put(InfuseItem.SUPPORT_OCEAN, createPotion(
            "&3Ocean Effect",
            Color.BLUE,
            List.of(
                "&9Effect",
                "&7Grants &oDolphin's Grace",
                "&7Grants &oConduit Power",
                "&7&o+2 Attack Damage while &9in water",
                "",
                "&9Spark Ability",
                "&7Regenerate health while &9in water",
                "",
                "&8Duration: &330s &8Cooldown: &360s"
            ),
            InfuseItem.SUPPORT_OCEAN
        ));

        items.put(InfuseItem.SUPPORT_SPEED, createPotion(
            "&bSpeed Effect",
            Color.AQUA,
            List.of(
                "&9Effect",
                "&7Grants &oSpeed II",
                "",
                "&9Spark Ability",
                "&7Dash forward",
                "",
                "&8Duration: &31s &8Cooldown: &315s"
            ),
            InfuseItem.SUPPORT_SPEED
        ));

        items.put(InfuseItem.PRIMARY_FEATHER, createPotion(
            "&7Feather Effect",
            Color.fromRGB(192, 192, 192),
            List.of(
                "&9Effect",
                "&75% chance to inflict &oLevitation (amp 2) &7for &o2s on hit",
                "",
                "&9Spark Ability",
                "&7Enemies within &o8 blocks &7enter a",
                "&73x levitation cycle (&o2s up, 0.7s down)",
                "",
                "&8Duration: &39s &8Cooldown: &330s"
            ),
            InfuseItem.PRIMARY_FEATHER
        ));

        items.put(InfuseItem.PRIMARY_FROST, createPotion(
            "&3Frost Effect",
            Color.AQUA,
            List.of(
                "&9Effect",
                "&7&oSpeed X &7on &3Ice &7& &oSpeed III &7on &bSnow",
                "",
                "&9Spark Ability",
                "&7Inflict &b&oFrostbite &7on attacked enemies",
                "&7&o+3 Attack damage on frosted enemies",
                "",
                "&8Duration: &330s &8Cooldown: &390s"
            ),
            InfuseItem.PRIMARY_FROST
        ));

        items.put(InfuseItem.PRIMARY_HASTE, createPotion(
            "&6Haste Effect",
            Color.YELLOW,
            List.of(
                "&9Effect",
                "&7Grants &oHaste II &7and &7&oFortune IV",
                "",
                "&9Spark Ability",
                "&7Upgrades &oHaste II &7to &oInstamine",
                "",
                "&8Duration: &345s &8Cooldown: &375s"
            ),
            InfuseItem.PRIMARY_HASTE
        ));

        items.put(InfuseItem.PRIMARY_HEART, createPotion(
            "&4Heart Effect",
            Color.RED,
            List.of(
                "&9Effect",
                "&7Gain &c+3 extra hearts",
                "",
                "&9Spark Ability",
                "&7Gain &c+3 more hearts &7and heal fully",
                "",
                "&8Duration: &31m20s &8Cooldown: &32m"
            ),
            InfuseItem.PRIMARY_HEART
        ));

        items.put(InfuseItem.PRIMARY_NINJA, createPotion(
            "&7Ninja Effect",
            Color.PURPLE,
            List.of(
                "&9Effect",
                "&7Stand still &o5s &7to gain &oTRUE invisibility",
                "&7Timer starts when you move; hits deal &c+30% damage",
                "",
                "&9Spark Ability",
                "&715s of TRUE invisibility with &c+50% damage",
                "&7Takes &c+90% &7damage; leaves iron dust trail",
                "",
                "&8Passive CD: &330s &8Spark CD: &31m30s"
            ),
            InfuseItem.PRIMARY_NINJA
        ));

        items.put(InfuseItem.PRIMARY_REGENERATION, createPotion(
            "&4Regeneration Effect",
            Color.RED,
            List.of(
                "&9Effect",
                "&7Hitting enemies grants &oRegeneration II &7for &o1s",
                "",
                "&9Spark Ability",
                "&7Heal for &o25% &7of the health lost by enemies you hit",
                "&7Lasts &o20s",
                "",
                "&8Duration: &320s &8Cooldown: &31m10s"
            ),
            InfuseItem.PRIMARY_REGENERATION
        ));

        items.put(InfuseItem.PRIMARY_STRENGTH, createPotion(
            "&cStrength Effect",
            Color.fromRGB(139, 0, 0),
            List.of(
                "&9Effect",
                "&7Deal &o+0.5 hearts &7damage below &c6 hearts",
                "&7Deal &o+2 hearts &7damage below &c4 hearts",
                "&7Deal &o+3 hearts &7damage below &c2 hearts",
                "",
                "&9Spark Ability",
                "&7All properly timed hits become &oCritical Hits",
                "",
                "&8Duration: &310s &8Cooldown: &360s"
            ),
            InfuseItem.PRIMARY_STRENGTH
        ));

        items.put(InfuseItem.PRIMARY_THUNDER, createPotion(
            "&3Thunder Effect",
            Color.YELLOW,
            List.of(
                "&9Effect",
                "&7Strike enemies with &6Lightning &7every &610th &7hit",
                "&7Deal &60.5 hearts &7of true damage with the strike",
                "",
                "&9Spark Ability",
                "&7Smite enemies within an &68 block radius &7once",
                "&7Deal &63 hearts &7of true damage and camera lock for &65s",
                "",
                "&8Duration: &310s &8Cooldown: &3120s"
            ),
            InfuseItem.PRIMARY_THUNDER
        ));

        items.put(InfuseItem.PRIMARY_PIG, createPotion(
            "&d&lPig Effect",
            Color.fromRGB(255, 105, 180),
            List.of(
                "&9Effect",
                "&7After being hit 5 times gain &oSpeed III &7for 5s",
                "&7Take 5% less knockback",
                "",
                "&9Spark Ability",
                "&7Send out a barrage of exploding baby pigs",
                "",
                "&8Duration: &31s &8Cooldown: &330s"
            ),
            InfuseItem.PRIMARY_PIG
        ));

        items.put(InfuseItem.PRIMARY_PIGLIN, createPotion(
            "&4&lPiglin Effect",
            Color.fromRGB(139, 0, 0),
            List.of(
                "&9Effect",
                "&7Mark attackers for 2s",
                "&7&o+1.25 Attack Damage on marked targets",
                "",
                "&9Spark Ability",
                "&7&o+1.8 Attack Damage on marked targets",
                "",
                "&8Duration: &330s &8Cooldown: &330s"
            ),
            InfuseItem.PRIMARY_PIGLIN
        ));

        items.put(InfuseItem.PRIMARY_OVERDRIVE, createPotion(
            "&6Overdrive Effect",
            Color.fromRGB(255, 140, 0),
            List.of(
                "&9Effect",
                "&740% chance to mirror hits",
                "&7Echoed hits deal &o-20% damage",
                "",
                "&9Spark Ability",
                "&7100% chance to mirror hits",
                "&7Echoed hits deal &o-10% damage",
                "",
                "&8Duration: &36s &8Cooldown: &360s"
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
