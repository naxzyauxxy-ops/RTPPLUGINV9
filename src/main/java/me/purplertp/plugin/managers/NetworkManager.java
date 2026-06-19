package me.purplertp.plugin.managers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.purplertp.plugin.PurpleRTP;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

public class NetworkManager {

    private final PurpleRTP plugin;
    private final File pendingDir;

    public NetworkManager(PurpleRTP plugin) {
        this.plugin = plugin;
        this.pendingDir = new File(plugin.getDataFolder(), "pending");
        pendingDir.mkdirs();
    }

    /**
     * Writes a pending RTP flag then sends the player cross-server on the next tick.
     * The 1-tick delay is required — sendPluginMessage is silently dropped if called
     * during an inventory event before the packet queue flushes.
     */
    public void sendPlayerWithRtp(Player player, String serverKey) {
        writePendingFlag(player.getUniqueId(), serverKey);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverKey);
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }, 1L);
    }

    /**
     * Checks for a pending RTP flag on join. Returns the serverKey or null.
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
