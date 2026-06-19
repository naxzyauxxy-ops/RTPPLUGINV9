package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

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

    public boolean isInRtp(UUID uuid) { return inRtp.contains(uuid); }
    public void cancelRtp(UUID uuid)  { inRtp.remove(uuid); }
    public Set<UUID> getPlayersInRtp() { return inRtp; }

    public void randomTeleport(Player player, String worldName) {
        FileConfiguration cfg = plugin.getConfig();

        if (!cfg.getBoolean("ENABLED", true)) {
            actionbar(player, cfg.getString("MESSAGES.DISABLED"));
            return;
        }

        if (inRtp.size() >= cfg.getInt("SETTINGS.PLAYERS-IN-RTP", 100)) {
            actionbar(player, cfg.getString("MESSAGES.MAX-PLAYERS"));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            actionbar(player, cfg.getString("MESSAGES.WORLD-NOT-EXIST"));
            return;
        }

        if (!player.hasPermission("purplertp.bypass.cooldown")) {
            CooldownManager cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), worldName)) {
                long remaining = cm.getRemainingCooldown(player.getUniqueId(), worldName);
                actionbar(player, cfg.getString("MESSAGES.COOLDOWN")
                        .replace("{remaining}", String.valueOf(remaining)));
                return;
            }
        }

        if (inRtp.contains(player.getUniqueId())) {
            actionbar(player, "&8(&#f40d0d!&8) &7You are already teleporting!");
            return;
        }

        inRtp.add(player.getUniqueId());

        String path     = "WORLD-SETTINGS." + worldName + ".";
        int cooldown    = cfg.getInt(path + "COOLDOWN", 0);
        int centerX     = cfg.getInt(path + "CENTER-X", 0);
        int centerZ     = cfg.getInt(path + "CENTER-Z", 0);
        int minRadius   = cfg.getInt(path + "MIN-RADIUS", 500);
        int maxRadius   = cfg.getInt(path + "MAX-RADIUS", 5000);
        int maxAttempts = cfg.getInt(path + "MAX-ATTEMPTS",
                          cfg.getInt("SETTINGS.MAX-ATTEMPTS", 25));

        // ── Pool hit: instant teleport ───────────────────────────────────────
        Location poolLoc = plugin.getLocationPoolManager().pollLocation(worldName);
        if (poolLoc != null) {
            doTeleport(player, poolLoc, cooldown, worldName);
            return;
        }

        // ── Pool empty: search async ─────────────────────────────────────────
        actionbar(player, cfg.getString("MESSAGES.SEARCHING",
                "&8(&#f40d0d!&8) &7Searching for a safe location..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location dest = findSafeLocation(world, centerX, centerZ, minRadius, maxRadius, maxAttempts);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) return;

                if (dest == null) {
                    inRtp.remove(player.getUniqueId());
                    actionbar(player, cfg.getString("MESSAGES.MAX-ATTEMPTS",
                            "&8(&#f40d0d!&8) &7No safe location found.")
                            .replace("{attempts}", String.valueOf(maxAttempts)));
                    return;
                }
                doTeleport(player, dest, cooldown, worldName);
            });
        });
    }

    // ── Instant teleport, no countdown ──────────────────────────────────────

    private void doTeleport(Player player, Location dest, int cooldown, String worldName) {
        inRtp.remove(player.getUniqueId());
        player.teleport(dest);
        player.playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);

        if (cooldown > 0) {
            plugin.getCooldownManager().setCooldown(player.getUniqueId(), worldName, cooldown);
        }

        String found = plugin.getConfig().getString("MESSAGES.SAFE-LOCATION-FOUND", "");
        if (found != null && !found.isEmpty()) actionbar(player, found);
    }

    // ── Location search (async) ──────────────────────────────────────────────

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

                world.getChunkAt(x >> 4, z >> 4);

                Location loc = getSafeY(world, x, z, isNether);
                if (loc != null) {
                    loc.setYaw(rng.nextFloat() * 360f);
                    return loc;
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
        if (message == null || message.isEmpty()) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }
}
