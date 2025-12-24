package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseConstants;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class InfuseRegistry {
    private final InfuseContext context;
    private final List<Infuse> infuses = new ArrayList<>();
    private final Map<String, Infuse> byKey = new HashMap<>();
    private final Map<InfuseItem, Infuse> byItem = new HashMap<>();
    private final Map<EffectGroup, Map<Integer, Infuse>> byEffect = new HashMap<>();
    private FeatherInfuse featherInfuse;
    private FireInfuse fireInfuse;
    private ThunderInfuse thunderInfuse;
    private PigInfuse pigInfuse;
    private PiglinInfuse piglinInfuse;
    private OverdriveInfuse overdriveInfuse;

    public InfuseRegistry(InfuseContext context) {
        this.context = context;
        registerAll();
    }

    private void registerAll() {
        register(new StrengthInfuse());
        register(new HeartInfuse());
        register(new HasteInfuse());
        register(new NinjaInfuse());
        register(new FeatherInfuse());
        register(new FrostInfuse());
        register(new ThunderInfuse());
        register(new RegenerationInfuse());
        register(new PigInfuse());
        register(new PiglinInfuse());
        register(new OverdriveInfuse());
        register(new OceanInfuse());
        register(new FireInfuse());
        register(new EmeraldInfuse());
        register(new SpeedInfuse());
    }

    private void register(Infuse infuse) {
        infuses.add(infuse);
        if (infuse.getKey() != null && !infuse.getKey().isBlank()) {
            byKey.put(infuse.getKey().toLowerCase(), infuse);
        }
        if (infuse.getItem() != null) {
            byItem.put(infuse.getItem(), infuse);
        }
        byEffect.computeIfAbsent(infuse.getGroup(), key -> new HashMap<>())
            .put(infuse.getEffectId(), infuse);
        if (infuse instanceof FeatherInfuse feather) {
            featherInfuse = feather;
        } else if (infuse instanceof FireInfuse fire) {
            fireInfuse = fire;
        } else if (infuse instanceof ThunderInfuse thunder) {
            thunderInfuse = thunder;
        } else if (infuse instanceof PigInfuse pig) {
            pigInfuse = pig;
        } else if (infuse instanceof PiglinInfuse piglin) {
            piglinInfuse = piglin;
        } else if (infuse instanceof OverdriveInfuse overdrive) {
            overdriveInfuse = overdrive;
        }
    }

    public EffectSelection getSelectionByKey(String key) {
        if (key == null) {
            return new EffectSelection(EffectGroup.PRIMARY, 0);
        }
        if ("empty".equalsIgnoreCase(key)) {
            return new EffectSelection(EffectGroup.PRIMARY, 0);
        }
        Infuse infuse = byKey.get(key.toLowerCase());
        if (infuse == null) {
            return new EffectSelection(EffectGroup.PRIMARY, 0);
        }
        return new EffectSelection(infuse.getGroup(), infuse.getEffectId());
    }

    public EffectSelection getSelectionByItem(InfuseItem item) {
        if (item == null) {
            return null;
        }
        Infuse infuse = byItem.get(item);
        if (infuse == null) {
            return null;
        }
        return new EffectSelection(infuse.getGroup(), infuse.getEffectId());
    }

    public Infuse getInfuse(EffectGroup group, int effectId) {
        Map<Integer, Infuse> groupMap = byEffect.get(group);
        if (groupMap == null) {
            return null;
        }
        return groupMap.get(effectId);
    }

    public void updateSlot(Player player, PlayerData data, int slot) {
        EffectGroup group = SlotHelper.getSlotGroup(data, slot);
        int effect = SlotHelper.getSlotEffect(data, slot);
        boolean active = SlotHelper.isSlotActive(data, slot);
        if (effect == 0) {
            if (group == EffectGroup.PRIMARY) {
                SlotHelper.setSlotActionBar(data, slot, "\uE001");
                if (slot == 1) {
                    data.setPrimaryColorCode("&f&l");
                }
            } else {
                SlotHelper.setSlotActionBar(data, slot, active ? "\uE022&f&l" : "\uE001&f&l");
                if (slot == 1) {
                    data.setPrimaryColorCode("&f&l");
                }
            }
            return;
        }
        Infuse infuse = getInfuse(group, effect);
        if (infuse != null) {
            infuse.updateSlot(player, data, slot, active, context);
        }
        if (group == EffectGroup.SUPPORT && slot == 1) {
            data.setPrimaryColorCode("&f&l");
        }
    }

    public void tickPlayer(Player player, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onTick(player, data, context);
        }
    }

    public void activateSlot(Player player, PlayerData data, int slot) {
        int effect = SlotHelper.getSlotEffect(data, slot);
        if (effect == 0) {
            return;
        }
        if (!InfuseConstants.READY_SPACES.equals(SlotHelper.getSlotShow(data, slot))) {
            return;
        }
        EffectGroup group = SlotHelper.getSlotGroup(data, slot);
        Infuse infuse = getInfuse(group, effect);
        if (infuse == null || !(infuse instanceof OverdriveInfuse)) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        }
        if (infuse != null) {
            infuse.activate(player, data, slot, context);
        }
    }

    public void onPlayerMove(PlayerMoveEvent event, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onPlayerMove(event, data, context);
        }
    }

    public void onEntityDamage(EntityDamageEvent event, PlayerData data) {
        if (featherInfuse != null) {
            featherInfuse.onEntityDamage(event, data, context);
        }
        if (fireInfuse != null) {
            fireInfuse.onEntityDamage(event, data, context);
        }
        if (thunderInfuse != null) {
            thunderInfuse.onEntityDamage(event, data, context);
        }
        if (pigInfuse != null) {
            pigInfuse.onEntityDamage(event, data, context);
        }
        if (overdriveInfuse != null) {
            overdriveInfuse.onEntityDamage(event, data, context);
        }
    }

    public void onEntityDamageByDamager(EntityDamageByEntityEvent event, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onEntityDamageByEntity(event, data, context);
        }
    }

    public void onEntityDamageByVictim(EntityDamageByEntityEvent event, PlayerData data) {
        if (pigInfuse != null) {
            pigInfuse.onEntityDamageByEntity(event, data, context);
        }
        if (piglinInfuse != null) {
            piglinInfuse.onEntityDamageByEntity(event, data, context);
        }
    }

    public void onBlockBreak(BlockBreakEvent event, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onBlockBreak(event, data, context);
        }
    }

    public void onEntityDeath(EntityDeathEvent event, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onEntityDeath(event, data, context);
        }
    }

    public void onProjectileHit(ProjectileHitEvent event, PlayerData data) {
        if (fireInfuse != null) {
            fireInfuse.onProjectileHit(event, data, context);
        }
    }

    public void onPlayerQuit(PlayerQuitEvent event, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onPlayerQuit(event, data, context);
        }
    }

    public void onDisable(Player player, PlayerData data) {
        for (Infuse infuse : infuses) {
            infuse.onDisable(player, data, context);
        }
    }

    public void giveEffectItem(Player player, EffectGroup group, int effect) {
        Infuse infuse = getInfuse(group, effect);
        if (infuse == null) {
            return;
        }
        InfuseItem item = infuse.getItem();
        if (item != null) {
            player.getInventory().addItem(context.getInfuseItems().getItem(item));
        }
    }

    public void dropEffectOnDeath(Player player, PlayerData data, int slot) {
        int effect = SlotHelper.getSlotEffect(data, slot);
        if (effect == 0) {
            return;
        }
        EffectGroup group = SlotHelper.getSlotGroup(data, slot);
        Infuse infuse = getInfuse(group, effect);
        if (infuse == null) {
            return;
        }
        InfuseItem item = infuse.getItem();
        if (item != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), context.getInfuseItems().getItem(item));
        }
        SlotHelper.setSlotEffect(data, slot, group, 0);
    }
}
