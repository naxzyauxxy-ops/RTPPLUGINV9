package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;

public class NetworkManager {

    private final PurpleRTP plugin;

    public NetworkManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public String getLocalServer() {
        return plugin.getConfig().getString("NETWORK.LOCAL-SERVER", "").trim();
    }

    public boolean isSameRegion(String localServer, String targetServerKey) {
        // On a single-proxy setup, all servers are local
        // Just check if the target starts with the local server prefix
        // e.g. local="eu", target="eu_nether" -> true
        // e.g. local="na", target="eu" -> false
        if (localServer.isEmpty()) return false;
        return targetServerKey.equalsIgnoreCase(localServer) ||
               targetServerKey.toLowerCase().startsWith(localServer.toLowerCase() + "_");
    }
}
