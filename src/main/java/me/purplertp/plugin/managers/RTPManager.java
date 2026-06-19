package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RTPManager {

    private final PurpleRTP plugin;
    private final Set<UUID> inRtp = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RTPManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public Set<UUID> getPlayersInRtp() { return inRtp; }
    public boolean isInRtp(UUID uuid)  { return inRtp.contains(uuid); }
    public void cancelRtp(UUID uuid)   { inRtp.remove(uuid); }

    /**
     * Initiates an RTP for the player into the given world.
     * worldName must match a WORLD-SETTINGS entry in config.
     */
    public void randomTeleport(Player player, String worldName) {
        FileConfiguration cfg = plugin.getConfig();

        // Global enabled check
        if (!cfg.getBoolean("ENABLED", true)) {
            actionbar(player, cfg.getString("MESSAGES.DISABLED", "&cRTP is disabled."));
            return;
        }

        // Max concurrent players
        int maxPlayers = cfg.getInt("SETTINGS.PLAYERS-IN-RTP", 150);
        if (inRtp.size() >= maxPlayers) {
            actionbar(player, cfg.getString("MESSAGES.MAX-PLAYERS", "&cToo many players using RTP."));
            return;
        }

        // Resolve world
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            actionbar(player, cfg.getString("MESSAGES.WORLD-NOT-EXIST", "&cWorld not found."));
            return;
        }

        // Cooldown
        if (!player.hasPermission("purplertp.bypass.cooldown")) {
            CooldownManager cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), worldName)) {
                long remaining = cm.getRemainingCooldown(player.getUniqueId(), worldName);
                String msg = cfg.getString("MESSAGES.COOLDOWN", "&cWait {remaining}s.")
                        .replace("{remaining}", String.valueOf(remaining));
                actionbar(player, msg);
                return;
            }
        }

        // Config path for this world
        String path     = "WORLD-SETTINGS." + worldName + ".";
        int cooldown    = cfg.getInt(path + "COOLDOWN", 0);
        int countdown   = cfg.getInt("SETTINGS.COUNTDOWN", 5);
        int centerX     = cfg.getInt(path + "CENTER-X", 0);
        int centerZ     = cfg.getInt(path + "CENTER-Z", 0);
        int minRadius   = cfg.getInt(path + "MIN-RADIUS", 500);
        int maxRadius   = cfg.getInt(path + "MAX-RADIUS", 5000);
        int maxAttempts = cfg.getInt("SETTINGS.MAX-ATTEMPTS", 25);

        inRtp.add(player.getUniqueId());

        // Try pool first
        Location poolLoc = plugin.getLocationPoolManager().pollLocation(worldName);

        if (poolLoc != null) {
            int startX = player.getLocation().getBlockX();
            int startZ = player.getLocation().getBlockZ();
            Location startLoc = player.getLocation();
            runCountdown(player, countdown, startX, startZ, startLoc, poolLoc, cooldown, worldName);
            return;
        }

        // Pool empty — warn and search async
        if (plugin.getLocationPoolManager().poolSize(worldName) == 0) {
            actionbar(player, cfg.getString("MESSAGES.POOL-WARMING",
                    "&eRTP pool is warming up, please try again in a moment."));
            inRtp.remove(player.getUniqueId());
            return;
        }

        // Fallback: search async
        actionbar(player, cfg.getString("MESSAGES.SEARCHING", "&dSearching..."));

        BukkitRunnable searchTicker = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) { cancel(); return; }
                actionbar(player, cfg.getString("MESSAGES.SEARCHING", "&dSearching..."));
            }
        };
        searchTicker.runTaskTimer(plugin, 0L, 20L);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location dest = findSafeLocation(world, centerX, centerZ, minRadius, maxRadius, maxAttempts);

            Bukkit.getScheduler().runTask(plugin, () -> {
                searchTicker.cancel();
                if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) return;

                if (dest == null) {
                    inRtp.remove(player.getUniqueId());
                    String msg = cfg.getString("MESSAGES.MAX-ATTEMPTS", "&cNo safe location found.")
                            .replace("{attempts}", String.valueOf(maxAttempts));
                    actionbar(player, msg);
                    return;
                }

                int startX = player.getLocation().getBlockX();
                int startZ = player.getLocation().getBlockZ();
                Location startLoc = player.getLocation();
                runCountdown(player, countdown, startX, startZ, startLoc, dest, cooldown, worldName);
            });
        });
    }

    // -----------------------------------------------------------------------

    private void runCountdown(Player player, int countdown, int startX, int startZ,
                               Location startLoc, Location dest, int cooldown, String worldName) {
        new BukkitRunnable() {
            int secondsLeft = countdown;

            @Override public void run() {
                if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) { cancel(); return; }

                // Movement check
                double dx = Math.abs(player.getLocation().getX() - startX);
                double dz = Math.abs(player.getLocation().getZ() - startZ);
                if (dx > 1 || dz > 1) {
                    inRtp.remove(player.getUniqueId());
                    cancel();
                    actionbar(player, "&cTeleport cancelled &7— &cdon't move!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (secondsLeft > 0) {
                    actionbar(player, "&fTeleporting in &b" + secondsLeft + "&f...");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
                    if (player.getWorld() != null) {
                        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                                player.getLocation().clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                    }
                    secondsLeft--;
                } else {
                    // Teleport
                    cancel();
                    inRtp.remove(player.getUniqueId());
                    player.teleport(dest);
                    player.playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
                    if (cooldown > 0) {
                        plugin.getCooldownManager().setCooldown(player.getUniqueId(), worldName, cooldown);
                    }
                    String found = plugin.getConfig().getString("MESSAGES.SAFE-LOCATION-FOUND", "");
                    if (found != null && !found.isEmpty()) actionbar(player, found);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private Location findSafeLocation(World world, int centerX, int centerZ,
                                       int minRadius, int maxRadius, int maxAttempts) {
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                double angle  = rng.nextDouble() * 2 * Math.PI;
                double radius = minRadius + Math.sqrt(rng.nextDouble()) * (maxRadius - minRadius);
                int x = centerX + (int)(Math.cos(angle) * radius);
                int z = centerZ + (int)(Math.sin(angle) * radius);

                world.getChunkAt(x >> 4, z >> 4); // ensure chunk is loaded

                Location candidate = getSafeY(world, x, z, isNether);
                if (candidate != null) {
                    candidate.setYaw(rng.nextFloat() * 360f);
                    return candidate;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Location getSafeY(World world, int x, int z, boolean isNether) {
        int topY = isNether ? 120 : world.getHighestBlockYAt(x, z);
        int minY = isNether ? world.getMinHeight() + 1 : world.getMinHeight();

        for (int y = topY; y > minY; y--) {
            var ground = new Location(world, x, y,     z).getBlock();
            var feet   = new Location(world, x, y + 1, z).getBlock();
            var head   = new Location(world, x, y + 2, z).getBlock();
            var type   = ground.getType();

            if (!type.isAir() && type.isSolid()
                    && type != org.bukkit.Material.WATER
                    && type != org.bukkit.Material.LAVA
                    && type != org.bukkit.Material.FIRE
                    && type != org.bukkit.Material.CACTUS
                    && feet.getType().isAir()
                    && head.getType().isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }

    private void actionbar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }
}
