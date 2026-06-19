package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final PurpleRTP plugin;
    /** uuid -> (worldName -> expiryMs) */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final File dataFile;

    public CooldownManager(PurpleRTP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        loadCooldowns();
    }

    public boolean isOnCooldown(UUID uuid, String worldName) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return false;
        Long expiry = map.get(worldName);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            map.remove(worldName);
            return false;
        }
        return true;
    }

    public long getRemainingCooldown(UUID uuid, String worldName) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0;
        Long expiry = map.get(worldName);
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void setCooldown(UUID uuid, String worldName, int seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(worldName, System.currentTimeMillis() + seconds * 1000L);
    }

    public void removeCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public void saveCooldowns() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<String, Long> worldEntry : entry.getValue().entrySet()) {
                yaml.set(entry.getKey().toString() + "." + worldEntry.getKey(), worldEntry.getValue());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save cooldowns: " + e.getMessage());
        }
    }

    private void loadCooldowns() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : yaml.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
            var section = yaml.getConfigurationSection(uuidStr);
            if (section == null) continue;
            Map<String, Long> map = cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            for (String world : section.getKeys(false)) {
                map.put(world, yaml.getLong(uuidStr + "." + world));
            }
        }
    }
}
