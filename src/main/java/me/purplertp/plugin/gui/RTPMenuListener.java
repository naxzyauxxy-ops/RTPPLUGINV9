package me.purplertp.plugin.gui;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RTPMenuListener implements Listener {

    private final Map<UUID, String> openMenus = new HashMap<>();
    private final PurpleRTP plugin;
    private final RTPMenu menu;

    public RTPMenuListener(PurpleRTP plugin) {
        this.plugin = plugin;
        this.menu   = new RTPMenu(plugin);
    }

    public void trackMain(Player player) {
        openMenus.put(player.getUniqueId(), "MAIN");
    }

    public void trackRegion(Player player, String dimensionKey) {
        openMenus.put(player.getUniqueId(), dimensionKey);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;

        String menuKey = openMenus.get(player.getUniqueId());
        if (menuKey == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null) return;

        FileConfiguration cfg = plugin.getConfig();

        // ── Main menu ────────────────────────────────────────────────────────
        if (menuKey.equals("MAIN")) {
            ConfigurationSection buttons = cfg.getConfigurationSection("RTP-MENU.BUTTONS");
            if (buttons == null) return;

            for (String key : buttons.getKeys(false)) {
                String base = "RTP-MENU.BUTTONS." + key + ".";
                if (!cfg.getBoolean(base + "ENABLED", true)) continue;
                if (cfg.getInt(base + "SLOT") != event.getRawSlot()) continue;

                boolean regionEnabled = cfg.getBoolean(base + "ENABLED-REGION", false);
                String worldName = cfg.getString(base + "WORLD", "world");

                player.closeInventory();

                if (regionEnabled) {
                    menu.openRegionMenu(player, key, this);
                } else {
                    if (plugin.getRtpManager().isInRtp(player.getUniqueId())) {
                        sendActionBar(player, "&#f40d0d(!) &7You are already teleporting!");
                        return;
                    }
                    plugin.getRtpManager().randomTeleport(player, worldName);
                }
                return;
            }
            return;
        }

        // ── Region sub-menu ──────────────────────────────────────────────────
        String sectionPath = "REGION-MENUS." + menuKey + ".BUTTONS";
        ConfigurationSection buttons = cfg.getConfigurationSection(sectionPath);
        if (buttons == null) return;

        for (String regionKey : buttons.getKeys(false)) {
            String base = sectionPath + "." + regionKey + ".";
            if (cfg.getInt(base + "SLOT") != event.getRawSlot()) continue;

            player.closeInventory();

            if (plugin.getRtpManager().isInRtp(player.getUniqueId())) {
                sendActionBar(player, "&#f40d0d(!) &7You are already teleporting!");
                return;
            }

            List<String> servers = cfg.getStringList(base + "SERVERS");
            String serverKey = servers.isEmpty() ? null : servers.get(0);
            if (serverKey == null) return;

            String localServer = plugin.getNetworkManager().getLocalServer();

            if (!localServer.isEmpty() && localServer.equalsIgnoreCase(serverKey)) {
                // Already on this server — RTP locally
                String worldName = cfg.getString("SERVER-SETTINGS." + serverKey + ".TARGET-WORLD", "world");
                plugin.getRtpManager().randomTeleport(player, worldName);
            } else {
                // 1) Send socket message to target server so it knows to RTP this player on join
                plugin.getNetworkManager().sendRtpTrigger(player.getUniqueId(), serverKey);
                // 2) Transfer player via BungeeCord channel (Velocity handles this natively)
                sendActionBar(player, "&8(&#f40d0d!&8) &7Connecting to &#f40d0d" + serverKey.toUpperCase() + "&7...");
                plugin.getNetworkManager().connectToServer(player, serverKey);
            }
            return;
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }
}
