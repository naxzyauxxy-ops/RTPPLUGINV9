package me.purplertp.plugin;

import me.purplertp.plugin.commands.RTPAdminCommand;
import me.purplertp.plugin.commands.RTPCommand;
import me.purplertp.plugin.gui.RTPMenuListener;
import me.purplertp.plugin.managers.CooldownManager;
import me.purplertp.plugin.managers.LocationPoolManager;
import me.purplertp.plugin.managers.NetworkManager;
import me.purplertp.plugin.managers.RTPManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PurpleRTP extends JavaPlugin {

    private static PurpleRTP instance;

    private CooldownManager cooldownManager;
    private RTPManager rtpManager;
    private LocationPoolManager locationPoolManager;
    private NetworkManager networkManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        cooldownManager     = new CooldownManager(this);
        locationPoolManager = new LocationPoolManager(this);
        rtpManager          = new RTPManager(this);
        networkManager      = new NetworkManager(this);

        locationPoolManager.startPoolFilling();

        // Start TCP socket server to receive RTP triggers from other servers
        networkManager.startSocketServer();

        // Register outgoing BungeeCord channel for server transfers (works with Velocity)
        getServer().getMessenger().registerOutgoingPluginChannel(this, NetworkManager.BUNGEE_CHANNEL);

        RTPMenuListener menuListener = new RTPMenuListener(this);
        getCommand("rtp").setExecutor(new RTPCommand(this, menuListener));
        getCommand("rtpadmin").setExecutor(new RTPAdminCommand(this));
        getServer().getPluginManager().registerEvents(menuListener, this);

        getLogger().info("PurpleRTP enabled. LOCAL-SERVER="
                + getConfig().getString("NETWORK.LOCAL-SERVER", "(not set)"));
    }

    @Override
    public void onDisable() {
        cooldownManager.saveCooldowns();
        locationPoolManager.shutdown();
        networkManager.stopSocketServer();
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, NetworkManager.BUNGEE_CHANNEL);
        getLogger().info("PurpleRTP disabled.");
    }

    public static PurpleRTP getInstance()               { return instance; }
    public CooldownManager getCooldownManager()         { return cooldownManager; }
    public RTPManager getRtpManager()                   { return rtpManager; }
    public LocationPoolManager getLocationPoolManager() { return locationPoolManager; }
    public NetworkManager getNetworkManager()           { return networkManager; }
}
