package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkManager {

    private static final String SECRET = "purplertp-secret-changeme";

    private final PurpleRTP plugin;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public NetworkManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    // ── Socket server — receives RTP triggers from other servers ─────────────

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
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        executor.shutdownNow();
    }

    private void handleSocketClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream()))) {
            String line = reader.readLine();
            if (line == null) return;

            // Format: SECRET:UUID:serverKey
            String[] parts = line.split(":", 3);
            if (parts.length < 3 || !parts[0].equals(SECRET)) {
                plugin.getLogger().warning("[RTP] Invalid socket message ignored.");
                return;
            }

            UUID uuid = UUID.fromString(parts[1]);
            String serverKey = parts[2];
            String worldName = plugin.getConfig()
                    .getString("SERVER-SETTINGS." + serverKey + ".TARGET-WORLD", "world");

            plugin.getLogger().info("[RTP] Socket trigger received for " + uuid + " -> " + worldName);
            scheduleRtpOnJoin(uuid, worldName);

        } catch (Exception e) {
            plugin.getLogger().warning("[RTP] Socket handler error: " + e.getMessage());
        }
    }

    // Retry until player is online (up to ~10 seconds)
    private void scheduleRtpOnJoin(UUID uuid, String worldName) {
        ConcurrentHashMap<UUID, Integer> attempts = new ConcurrentHashMap<>();
        attempts.put(uuid, 0);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (attempts.merge(uuid, 1, Integer::sum) > 20) {
                task.cancel();
                plugin.getLogger().warning("[RTP] Player " + uuid + " never came online.");
                return;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) return;
            task.cancel();
            plugin.getRtpManager().randomTeleport(player, worldName);
        }, 10L, 10L);
    }

    // ── Send RTP trigger to target backend via TCP socket ────────────────────

    public void sendRtpTrigger(UUID uuid, String targetServerKey) {
        String proxyIp = null;
        int socketPort = plugin.getConfig().getInt("NETWORK.SOCKET-PORT", 25575);

        ConfigurationSection regions = plugin.getConfig().getConfigurationSection("NETWORK.REGIONS");
        if (regions != null) {
            for (String regionName : regions.getKeys(false)) {
                java.util.List<String> servers = plugin.getConfig()
                        .getStringList("NETWORK.REGIONS." + regionName + ".SERVERS");
                if (servers.contains(targetServerKey)) {
                    proxyIp    = plugin.getConfig().getString("NETWORK.REGIONS." + regionName + ".PROXY-IP");
                    socketPort = plugin.getConfig().getInt("NETWORK.REGIONS." + regionName + ".SOCKET-PORT", socketPort);
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
        final String msg  = SECRET + ":" + uuid + ":" + targetServerKey;

        plugin.getLogger().info("[RTP] Sending socket trigger to " + ip + ":" + port);
        executor.submit(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 3000);
                new PrintWriter(socket.getOutputStream(), true).println(msg);
                plugin.getLogger().info("[RTP] Socket trigger sent OK.");
            } catch (IOException e) {
                plugin.getLogger().warning("[RTP] Socket trigger failed: " + e.getMessage());
            }
        });
    }

    // ── Transfer player to another proxy using MC 1.20.5 Transfer packet ─────
    // Both your proxies have accepts-transfers = true so this works natively.

    public void transferToProxy(Player player, String targetServerKey) {
        String proxyIp   = null;
        int proxyPort    = 25565;

        ConfigurationSection regions = plugin.getConfig().getConfigurationSection("NETWORK.REGIONS");
        if (regions != null) {
            for (String regionName : regions.getKeys(false)) {
                java.util.List<String> servers = plugin.getConfig()
                        .getStringList("NETWORK.REGIONS." + regionName + ".SERVERS");
                if (servers.contains(targetServerKey)) {
                    proxyIp   = plugin.getConfig().getString("NETWORK.REGIONS." + regionName + ".PROXY-IP");
                    proxyPort = plugin.getConfig().getInt("NETWORK.REGIONS." + regionName + ".PROXY-PORT", 25565);
                    break;
                }
            }
        }

        if (proxyIp == null) {
            plugin.getLogger().warning("[RTP] No PROXY-IP found for transfer to: " + targetServerKey);
            return;
        }

        final String ip  = proxyIp;
        final int port   = proxyPort;

        // 1-tick delay required before sending transfer packet
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.getLogger().info("[RTP] Transferring " + player.getName()
                    + " to proxy " + ip + ":" + port);
            // Use Bukkit's built-in transfer (1.20.5+)
            player.transfer(ip, port);
        }, 1L);
    }

    public String getLocalServer() {
        return plugin.getConfig().getString("NETWORK.LOCAL-SERVER", "");
    }
}
