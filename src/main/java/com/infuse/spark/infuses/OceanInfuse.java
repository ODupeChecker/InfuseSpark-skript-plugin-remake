package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;
import com.infuse.spark.PlayerData;
import com.infuse.spark.SlotHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class OceanInfuse extends BaseInfuse {
    private static final String SALTED_METADATA = "infuse-spark-ocean-salted";
    private static final double WAVE_RING_THICKNESS = 0.75;

    private final Map<UUID, BukkitTask> saltedTasks = new HashMap<>();
    private final Map<UUID, WaveTask> activeWaves = new HashMap<>();
    private boolean listenersRegistered;
    private InfuseContext sharedContext;

    public OceanInfuse() {
        super(EffectGroup.SUPPORT, 1, "ocean", InfuseItem.SUPPORT_OCEAN);
    }

    @Override
    public void updateSlot(Player player, PlayerData data, int slot, boolean active, InfuseContext context) {
        ensureListeners(context);
        String activeIcon = getString(context, PASSIVE_SECTION, "action-bar-active", "");
        String inactiveIcon = getString(context, PASSIVE_SECTION, "action-bar-inactive", "");
        SlotHelper.setSlotActionBar(data, slot, active ? activeIcon : inactiveIcon);
    }

    @Override
    public void activate(Player player, PlayerData data, int slot, InfuseContext context) {
        ensureListeners(context);
        SlotHelper.setSlotActive(data, slot, true);
        int startMinutes = getInt(context, SPARK_SECTION, "cooldown-start-minutes", 0);
        int startSeconds = getInt(context, SPARK_SECTION, "cooldown-start-seconds", 0);
        SlotHelper.setSlotCooldown(data, slot, startMinutes, startSeconds);

        cancelActiveWave(player.getUniqueId());

        double maxRadius = getDouble(context, SPARK_SECTION, "wave-max-radius", 9.0);
        int maxHeight = getInt(context, SPARK_SECTION, "wave-max-height", 8);
        double expansionStep = getDouble(context, SPARK_SECTION, "wave-expansion-step", 1.0);
        int tickInterval = Math.max(1, getInt(context, SPARK_SECTION, "wave-tick-interval-ticks", 2));
        double knockbackHorizontal = getDouble(context, SPARK_SECTION, "wave-knockback-horizontal", 1.25);
        double knockbackVertical = getDouble(context, SPARK_SECTION, "wave-knockback-vertical", 0.35);
        int endMinutes = getInt(context, SPARK_SECTION, "cooldown-end-minutes", 0);
        int endSeconds = getInt(context, SPARK_SECTION, "cooldown-end-seconds", 0);

        WaveTask task = new WaveTask(player, data, slot, context, maxRadius, maxHeight, expansionStep, knockbackHorizontal,
            knockbackVertical, endMinutes, endSeconds);
        activeWaves.put(player.getUniqueId(), task);
        task.runTaskTimer(context.getPlugin(), 0L, tickInterval);
    }

    @Override
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event, PlayerData data, InfuseContext context) {
        ensureListeners(context);
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || !SlotHelper.hasEffect(data, EffectGroup.SUPPORT, 1)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        applySalted(target, context);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event, PlayerData data, InfuseContext context) {
        ensureListeners(context);
        cancelActiveWave(event.getPlayer().getUniqueId());
        removeSalted(event.getPlayer());
    }

    @Override
    public void onEntityDeath(EntityDeathEvent event, PlayerData data, InfuseContext context) {
        ensureListeners(context);
        if (event.getEntity() instanceof Player player) {
            cancelActiveWave(player.getUniqueId());
        }
        removeSalted(event.getEntity());
    }

    @Override
    public void onDisable(Player player, PlayerData data, InfuseContext context) {
        ensureListeners(context);
        cancelActiveWave(player.getUniqueId());
        removeSalted(player);
    }

    private void ensureListeners(InfuseContext context) {
        if (listenersRegistered) {
            return;
        }
        listenersRegistered = true;
        sharedContext = context;
        Bukkit.getPluginManager().registerEvents(new OceanListener(), context.getPlugin());
    }

    private void applySalted(LivingEntity target, InfuseContext context) {
        int durationSeconds = getInt(context, PASSIVE_SECTION, "salted-duration-seconds", 3);
        if (durationSeconds <= 0) {
            return;
        }
        removeSalted(target);
        target.setMetadata(SALTED_METADATA, new FixedMetadataValue(context.getPlugin(), Boolean.TRUE));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(context.getPlugin(), () -> removeSalted(target),
            durationSeconds * context.ticksPerSecond());
        saltedTasks.put(target.getUniqueId(), task);
    }

    private boolean isSalted(LivingEntity entity) {
        return entity.hasMetadata(SALTED_METADATA);
    }

    private void removeSalted(LivingEntity entity) {
        BukkitTask task = saltedTasks.remove(entity.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        if (sharedContext != null) {
            entity.removeMetadata(SALTED_METADATA, sharedContext.getPlugin());
        }
    }

    private void cancelActiveWave(UUID uuid) {
        WaveTask task = activeWaves.remove(uuid);
        if (task != null) {
            task.cancelWave();
        }
    }

    private final class OceanListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onAnyDamage(EntityDamageEvent event) {
            if (sharedContext == null) {
                return;
            }
            if (!(event.getEntity() instanceof LivingEntity entity)) {
                return;
            }
            if (!isSalted(entity)) {
                return;
            }
            double damageBoostPercent = getDouble(sharedContext, PASSIVE_SECTION, "salted-damage-increase-percent", 5.0);
            double multiplier = 1.0 + (Math.max(0.0, damageBoostPercent) / 100.0);
            event.setDamage(event.getDamage() * multiplier);
        }

        @EventHandler
        public void onTeleport(PlayerTeleportEvent event) {
            cancelActiveWave(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onWorldChange(PlayerChangedWorldEvent event) {
            cancelActiveWave(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            cancelActiveWave(event.getPlayer().getUniqueId());
            removeSalted(event.getPlayer());
        }
    }

    private final class WaveTask extends BukkitRunnable {
        private final Player player;
        private final PlayerData data;
        private final int slot;
        private final InfuseContext context;
        private final World world;
        private final Location center;
        private final double maxRadius;
        private final int maxHeight;
        private final double expansionStep;
        private final double knockbackHorizontal;
        private final double knockbackVertical;
        private final int endMinutes;
        private final int endSeconds;
        private final BlockData waterData;
        private final Map<Block, BlockData> replacedBlocks = new HashMap<>();
        private final Set<Block> waveBlocks = new HashSet<>();
        private final Set<UUID> knockedPlayers = new HashSet<>();

        private double radius;

        WaveTask(Player player, PlayerData data, int slot, InfuseContext context, double maxRadius, int maxHeight,
            double expansionStep, double knockbackHorizontal, double knockbackVertical, int endMinutes, int endSeconds) {
            this.player = player;
            this.data = data;
            this.slot = slot;
            this.context = context;
            this.maxRadius = maxRadius;
            this.maxHeight = maxHeight;
            this.expansionStep = Math.max(0.25, expansionStep);
            this.knockbackHorizontal = knockbackHorizontal;
            this.knockbackVertical = knockbackVertical;
            this.endMinutes = endMinutes;
            this.endSeconds = endSeconds;
            this.world = player.getWorld();
            this.center = player.getLocation().clone();
            this.waterData = Bukkit.createBlockData(Material.WATER);
        }

        @Override
        public void run() {
            if (!player.isOnline() || player.isDead() || !player.getWorld().equals(world)) {
                cancelWave();
                return;
            }

            radius += expansionStep;
            updateWaveBlocks();
            knockbackPlayers();

            if (radius >= maxRadius) {
                completeWave();
            }
        }

        private void updateWaveBlocks() {
            int radiusCeil = (int) Math.ceil(radius);
            double innerRadius = Math.max(0.0, radius - WAVE_RING_THICKNESS);
            double outerRadius = radius + WAVE_RING_THICKNESS;
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();

            for (int x = -radiusCeil; x <= radiusCeil; x++) {
                for (int z = -radiusCeil; z <= radiusCeil; z++) {
                    double distance = Math.sqrt((x * x) + (z * z));
                    if (distance < innerRadius || distance > outerRadius) {
                        continue;
                    }
                    for (int y = 0; y < maxHeight; y++) {
                        Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                        placeWater(block);
                    }
                }
            }
        }

        private void placeWater(Block block) {
            if (block.getType() == Material.WATER) {
                waveBlocks.add(block);
                return;
            }
            if (block.getType() != Material.AIR && !replacedBlocks.containsKey(block)) {
                return;
            }
            if (!replacedBlocks.containsKey(block)) {
                replacedBlocks.put(block, block.getBlockData());
            }
            block.setBlockData(waterData, false);
            waveBlocks.add(block);
        }

        private void knockbackPlayers() {
            for (Player target : world.getPlayers()) {
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                if (knockedPlayers.contains(target.getUniqueId())) {
                    continue;
                }
                Location location = target.getLocation();
                if (!isWithinHeight(location.getBlockY())) {
                    continue;
                }
                if (!isInWaveBlocks(location)) {
                    continue;
                }
                applyKnockback(target);
                knockedPlayers.add(target.getUniqueId());
            }
        }

        private boolean isWithinHeight(int y) {
            int baseY = center.getBlockY();
            return y >= baseY && y < baseY + maxHeight;
        }

        private boolean isInWaveBlocks(Location location) {
            Block feet = location.getBlock();
            Block head = location.clone().add(0, 1, 0).getBlock();
            return waveBlocks.contains(feet) || waveBlocks.contains(head);
        }

        private void applyKnockback(Player target) {
            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            direction.setY(0);
            if (direction.lengthSquared() == 0) {
                direction = new Vector(1, 0, 0);
            }
            direction.normalize().multiply(knockbackHorizontal);
            direction.setY(Math.min(knockbackVertical, 1.0));
            target.setVelocity(direction);
        }

        private void completeWave() {
            cancel();
            clearWave();
            activeWaves.remove(player.getUniqueId());
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }

        void cancelWave() {
            cancel();
            clearWave();
            activeWaves.remove(player.getUniqueId());
            SlotHelper.setSlotActive(data, slot, false);
            SlotHelper.setSlotCooldown(data, slot, endMinutes, endSeconds);
        }

        private void clearWave() {
            replacedBlocks.forEach((block, originalData) -> block.setBlockData(originalData, false));
            replacedBlocks.clear();
            waveBlocks.clear();
        }
    }
}
