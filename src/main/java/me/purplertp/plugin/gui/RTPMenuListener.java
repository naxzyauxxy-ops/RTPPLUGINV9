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
import org.bukkit.event.inventory.InventoryType;

public class RTPMenuListener implements Listener {

    private final PurpleRTP plugin;
    private final RTPMenu menu;
    private String cachedMainTitle;
    // cached region titles keyed by dimensionKey
    private final java.util.Map<String, String> cachedRegionTitles = new java.util.HashMap<>();

    public RTPMenuListener(PurpleRTP plugin) {
        this.plugin = plugin;
        this.menu   = new RTPMenu(plugin);
    }

    private String getMainTitle() {
        if (cachedMainTitle == null) {
            cachedMainTitle = MessageUtils.format(
                    plugin.getConfig().getString("RTP-MENU.TITLE", "&8Random Teleport"));
        }
        return cachedMainTitle;
    }

    private String getRegionTitle(String dimensionKey) {
        return cachedRegionTitles.computeIfAbsent(dimensionKey, k ->
                MessageUtils.format(plugin.getConfig()
                        .getString("REGION-MENUS." + k + ".TITLE", "&8Region Select")));
    }

    public void invalidateCache() {
        cachedMainTitle = null;
        cachedRegionTitles.clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;

        String openTitle = event.getView().getTitle();

        // ── Main RTP menu ────────────────────────────────────────────────────
        if (openTitle.equals(getMainTitle())) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getCurrentItem() == null) return;

            FileConfiguration cfg = plugin.getConfig();
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
                    // Open the region sub-menu for this dimension key
                    menu.openRegionMenu(player, key);
                } else {
                    // Teleport directly (nether / end bypass region selection)
                    if (plugin.getRtpManager().isInRtp(player.getUniqueId())) {
                        sendActionBar(player, "&cYou are already teleporting!");
                        return;
                    }
                    plugin.getRtpManager().randomTeleport(player, worldName);
                }
                return;
            }
            return;
        }

        // ── Region sub-menus ─────────────────────────────────────────────────
        for (String dimensionKey : new String[]{"OVERWORLD", "NETHER", "THE_END"}) {
            if (!openTitle.equals(getRegionTitle(dimensionKey))) continue;

            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getCurrentItem() == null) return;

            FileConfiguration cfg = plugin.getConfig();
            String sectionPath = "REGION-MENUS." + dimensionKey + ".BUTTONS";
            ConfigurationSection buttons = cfg.getConfigurationSection(sectionPath);
            if (buttons == null) return;

            for (String regionKey : buttons.getKeys(false)) {
                String base = sectionPath + "." + regionKey + ".";
                if (cfg.getInt(base + "SLOT") != event.getRawSlot()) continue;

                player.closeInventory();

                if (plugin.getRtpManager().isInRtp(player.getUniqueId())) {
                    sendActionBar(player, "&cYou are already teleporting!");
                    return;
                }

                // Resolve world from the first server's TARGET-WORLD in SERVER-SETTINGS
                // For the region menu buttons, the server list tells us which server key to use
                java.util.List<String> servers = cfg.getStringList(base + "SERVERS");
                String serverKey = servers.isEmpty() ? null : servers.get(0);
                String worldName = serverKey != null
                        ? cfg.getString("SERVER-SETTINGS." + serverKey + ".TARGET-WORLD", "world")
                        : "world";

                plugin.getRtpManager().randomTeleport(player, worldName);
                return;
            }
            return;
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }
}
