package me.purplertp.plugin.managers;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.purplertp.plugin.PurpleRTP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager implements PluginMessageListener {

    public static final String BUNGEE_CHANNEL = "BungeeCord";
    public static final String RTP_CHANNEL    = "purplertp:transfer";

    // Secret shared between all servers to verify socket messages
    private static final String SECRET = "purplertp-secret-changeme";

    private final PurpleRTP plugin;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private boolean running = false;

    public NetworkManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    // ── Socket server (receives RTP triggers from other servers) ─────────────

    public void startSocketServer() {
        int port = plugin.getConfig().getInt("NETWORK.SOCKET-PORT", 25575);
        executor.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                plugin.getLogger().info("[RTP] Socket server listening on port " + port);
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        executor.submit(() -> handleSocketClient(client));
                    } catch (IOException e) {
                        if (running) plugin.getLogger().warning("[RTP] Socket accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("[RTP] Could not start socket server on port " + port + ": " + e.getMessage());
            }
        });
    }

    public void stopSocketServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        executor.shutdownNow();
    }

    private void handleSocketClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {
            String line = reader.readLine();
            if (line == null) return;

            // Format: SECRET:UUID:serverKey
            String[] parts = line.split(":");
            if (parts.length < 3) return;
            if (!parts[0].equals(SECRET)) {
                plugin.getLogger().warning("[RTP] Socket message with wrong secret ignored.");
                return;
            }

            UUID uuid;
            try { uuid = UUID.fromString(parts[1]); }
            catch (IllegalArgumentException e) { return; }

            String serverKey = parts[2];
            String worldName = plugin.getConfig()
                    .getString("SERVER-SETTINGS." + serverKey + ".TARGET-WORLD", "world");

            plugin.getLogger().info("[RTP] Socket: received RTP trigger for " + uuid + " -> " + worldName);

            // Schedule RTP on main thread — player may not be online yet, retry for up to 10s
            scheduleRtpOnJoin(uuid, worldName);

        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Socket handler error: " + e.getMessage());
        }
    }

    // ── RTP on join (retry until player is online, up to 10 seconds) ─────────

    private void scheduleRtpOnJoin(UUID uuid, String worldName) {
        ConcurrentHashMap<UUID, Integer> attempts = new ConcurrentHashMap<>();
        attempts.put(uuid, 0);

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int count = attempts.merge(uuid, 1, Integer::sum);
            if (count > 20) { // 20 attempts * 10 ticks = ~10 seconds
                task.cancel();
                plugin.getLogger().warning("[RTP] Player " + uuid + " never came online for RTP.");
                return;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;

            task.cancel();
            plugin.getRtpManager().randomTeleport(player, worldName);
        }, 10L, 10L);
    }

    // ── Send RTP trigger to target server via direct TCP socket ──────────────

    public void sendRtpTrigger(UUID uuid, String targetServerKey) {
        // Find the proxy IP/port for the region that owns this server
        String proxyIp   = null;
        int proxyPort    = 25575;
        int socketPort   = plugin.getConfig().getInt("NETWORK.SOCKET-PORT", 25575);

        ConfigurationSection regions = plugin.getConfig().getConfigurationSection("NETWORK.REGIONS");
        if (regions != null) {
            for (String regionName : regions.getKeys(false)) {
                java.util.List<String> servers = plugin.getConfig()
                        .getStringList("NETWORK.REGIONS." + regionName + ".SERVERS");
                if (servers.contains(targetServerKey)) {
                    proxyIp   = plugin.getConfig().getString(
                            "NETWORK.REGIONS." + regionName + ".PROXY-IP");
                    socketPort = plugin.getConfig().getInt(
                            "NETWORK.REGIONS." + regionName + ".SOCKET-PORT", socketPort);
                    break;
                }
            }
        }

        if (proxyIp == null) {
            plugin.getLogger().warning("[RTP] No PROXY-IP found for server: " + targetServerKey);
            return;
        }

        final String ip   = proxyIp;
        final int port    = socketPort;
        final String msg  = SECRET + ":" + uuid.toString() + ":" + targetServerKey;

        plugin.getLogger().info("[RTP] Sending socket trigger to " + ip + ":" + port + " for " + uuid);

        executor.submit(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 3000);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(msg);
                plugin.getLogger().info("[RTP] Socket trigger sent successfully.");
            } catch (IOException e) {
                plugin.getLogger().warning("[RTP] Failed to send socket trigger to "
                        + ip + ":" + port + " — " + e.getMessage());
            }
        });
    }

    // ── BungeeCord channel transfer (works with Velocity natively) ───────────

    public void connectToServer(Player player, String serverName) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            plugin.getLogger().info("[RTP] BungeeCord Connect -> " + serverName
                    + " for " + player.getName());
        }, 1L);
    }

    // ── Incoming plugin message (unused, kept for future) ────────────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {}

    public String getLocalServer() {
        return plugin.getConfig().getString("NETWORK.LOCAL-SERVER", "");
    }
}
