package me.purplertp.plugin.managers;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.purplertp.plugin.PurpleRTP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

public class NetworkManager implements PluginMessageListener {

    private final PurpleRTP plugin;
    // Flat-file directory for pending RTP flags — survives cross-server transfer
    private final File pendingDir;

    public NetworkManager(PurpleRTP plugin) {
        this.plugin = plugin;
        this.pendingDir = new File(plugin.getDataFolder(), "pending");
        pendingDir.mkdirs();
    }

    // ── Sending side (player clicks EU/NA in region menu) ───────────────────

    /**
     * Writes a pending-RTP flag file for this player so the target server
     * knows to RTP them on join, then sends them to that BungeeCord server.
     */
    public void sendPlayerWithRtp(Player player, String serverKey) {
        writePendingFlag(player.getUniqueId(), serverKey);
        connectToServer(player, serverKey);
    }

    /** Sends the player to a BungeeCord server by name. */
    public void connectToServer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    // ── Receiving side (player arrives on target server) ────────────────────

    /**
     * Called on player join. Checks for a pending RTP flag file.
     * Returns the serverKey to RTP with, or null if no flag exists.
     */
    public String consumePendingFlag(UUID uuid) {
        File flag = flagFile(uuid);
        if (!flag.exists()) return null;
        try {
            String serverKey = Files.readString(flag.toPath()).trim();
            flag.delete();
            return serverKey.isEmpty() ? null : serverKey;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read pending RTP flag: " + e.getMessage());
            flag.delete();
            return null;
        }
    }

    // ── Plugin messaging channel (optional fallback / future use) ────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // Reserved for future BungeeCord plugin message handling
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Given a SERVER-SETTINGS key (e.g. "eu", "na_nether"), returns the BungeeCord
     * server name to connect to. The SERVERS list in NETWORK.REGIONS defines the
     * BungeeCord server name directly (e.g. "eu", "na").
     */
    public String resolveBungeeServer(String serverKey) {
        ConfigurationSection regions = plugin.getConfig().getConfigurationSection("NETWORK.REGIONS");
        if (regions == null) return serverKey;

        for (String regionName : regions.getKeys(false)) {
            List<String> servers = plugin.getConfig()
                    .getStringList("NETWORK.REGIONS." + regionName + ".SERVERS");
            if (servers.contains(serverKey)) {
                return serverKey; // The server key IS the BungeeCord server name
            }
        }
        return serverKey;
    }

    /** Returns the LOCAL-SERVER value from config, or empty string if not set. */
    public String getLocalServer() {
        return plugin.getConfig().getString("NETWORK.LOCAL-SERVER", "");
    }

    private void writePendingFlag(UUID uuid, String serverKey) {
        try (FileWriter fw = new FileWriter(flagFile(uuid))) {
            fw.write(serverKey);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write pending RTP flag: " + e.getMessage());
        }
    }

    private File flagFile(UUID uuid) {
        return new File(pendingDir, uuid.toString() + ".flag");
    }
}
