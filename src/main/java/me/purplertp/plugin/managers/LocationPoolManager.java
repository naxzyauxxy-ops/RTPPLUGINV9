package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationPoolManager {

    private final PurpleRTP plugin;
    private final Map<String, ConcurrentLinkedQueue<Location>> pools = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> filling = new ConcurrentHashMap<>();
    private BukkitTask refillTask;

    public LocationPoolManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public void startPoolFilling() {
        int intervalTicks = plugin.getConfig().getInt("SETTINGS.POOL-REFILL-INTERVAL", 40);

        refillTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            ConfigurationSection worldSection = plugin.getConfig().getConfigurationSection("WORLD-SETTINGS");
            if (worldSection == null) return;

            int poolSize  = plugin.getConfig().getInt("SETTINGS.POOL-SIZE", 20);
            int batchSize = plugin.getConfig().getInt("SETTINGS.POOL-FILL-BATCH", 5);
            Set<String> worldKeys = worldSection.getKeys(false);

            for (String worldName : worldKeys) {
                ConcurrentLinkedQueue<Location> pool =
                        pools.computeIfAbsent(worldName, k -> new ConcurrentLinkedQueue<>());
                AtomicBoolean isFilling =
                        filling.computeIfAbsent(worldName, k -> new AtomicBoolean(false));

                if (pool.size() >= poolSize) continue;
                if (!isFilling.compareAndSet(false, true)) continue;

                String path = "WORLD-SETTINGS." + worldName + ".";
                int maxRadius  = plugin.getConfig().getInt(path + "MAX-RADIUS", 5000);
                int minRadius  = plugin.getConfig().getInt(path + "MIN-RADIUS", 500);
                int centerX    = plugin.getConfig().getInt(path + "CENTER-X", 0);
                int centerZ    = plugin.getConfig().getInt(path + "CENTER-Z", 0);
                int maxAttempts = plugin.getConfig().getInt("SETTINGS.MAX-ATTEMPTS", 25);

                World world = Bukkit.getWorld(worldName);
                if (world == null) { isFilling.set(false); continue; }

                int needed    = Math.min(batchSize, poolSize - pool.size());
                int generated = 0;

                for (int attempt = 0; attempt < maxAttempts * needed && generated < needed; attempt++) {
                    Location loc = tryFindSafeLocation(world, centerX, centerZ, minRadius, maxRadius, maxAttempts);
                    if (loc != null) {
                        pool.add(loc);
                        generated++;
                    }
                }
                isFilling.set(false);
            }
        }, 0L, intervalTicks);
    }

    /** Returns a pre-generated location from the pool, or null if pool is empty. */
    public Location pollLocation(String worldName) {
        ConcurrentLinkedQueue<Location> pool = pools.get(worldName);
        if (pool == null || pool.isEmpty()) return null;
        return pool.poll();
    }

    public int poolSize(String worldName) {
        ConcurrentLinkedQueue<Location> pool = pools.get(worldName);
        return pool == null ? 0 : pool.size();
    }

    public void shutdown() {
        if (refillTask != null) refillTask.cancel();
    }

    // -----------------------------------------------------------------------

    private Location tryFindSafeLocation(World world, int centerX, int centerZ,
                                          int minRadius, int maxRadius, int maxAttempts) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                double angle  = rng.nextDouble() * 2 * Math.PI;
                double radius = minRadius + Math.sqrt(rng.nextDouble()) * (maxRadius - minRadius);
                int x = centerX + (int)(Math.cos(angle) * radius);
                int z = centerZ + (int)(Math.sin(angle) * radius);

                // Load chunk sync on main thread via completable future
                org.bukkit.Chunk chunk = world.getChunkAtAsync(x >> 4, z >> 4).get();

                Location loc = getSafeY(world, x, z, isNether);
                if (loc != null) {
                    loc.setYaw(rng.nextFloat() * 360f);
                    return loc;
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.FINE,
                        "Pool: chunk failed at " + centerX + "," + centerZ, e);
            }
        }
        return null;
    }

    private Location getSafeY(World world, int x, int z, boolean isNether) {
        int minY = isNether ? world.getMinHeight() + 1 : world.getMinHeight();
        int topY = isNether ? 120 : world.getHighestBlockYAt(x, z);

        for (int y = topY; y > minY; y--) {
            Block ground = world.getBlock(x, y, z);
            Block feet   = world.getBlock(x, y + 1, z);
            Block head   = world.getBlock(x, y + 2, z);

            if (!ground.getType().isAir() && ground.getType().isSolid()
                    && ground.getType() != Material.WATER
                    && ground.getType() != Material.LAVA
                    && ground.getType() != Material.FIRE
                    && ground.getType() != Material.CACTUS
                    && feet.getType().isAir()
                    && head.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
}
