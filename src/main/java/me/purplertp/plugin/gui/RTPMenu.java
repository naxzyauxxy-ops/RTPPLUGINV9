package me.purplertp.plugin.gui;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RTPMenu {

    private final PurpleRTP plugin;

    public RTPMenu(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    /** Opens the main RTP menu for the player. */
    public void open(Player player) {
        FileConfiguration cfg = plugin.getConfig();

        String title = MessageUtils.format(cfg.getString("RTP-MENU.TITLE", "&8Random Teleport"));
        int size     = cfg.getInt("RTP-MENU.SIZE", 27);
        boolean placeholder = cfg.getBoolean("RTP-MENU.PLACEHOLDER", true);

        Inventory inv = Bukkit.createInventory(null, size, title);

        if (placeholder) {
            fillPlaceholder(inv, size);
        }

        ConfigurationSection buttons = cfg.getConfigurationSection("RTP-MENU.BUTTONS");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                String base = "RTP-MENU.BUTTONS." + key + ".";
                if (!cfg.getBoolean(base + "ENABLED", true)) continue;

                Material mat = getMaterial(cfg.getString(base + "MATERIAL", "GRASS_BLOCK"));
                String displayName = MessageUtils.format(cfg.getString(base + "DISPLAY-NAME", key));
                String worldName   = cfg.getString(base + "WORLD", "world");
                int slot           = cfg.getInt(base + "SLOT", 13);

                int poolReady = plugin.getLocationPoolManager().poolSize(worldName);
                int ping      = player.getPing();
                long playersInWorld = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> {
                            var w = p.getWorld();
                            return w != null && w.getName().equals(worldName);
                        }).count();

                List<String> lore = new ArrayList<>();
                for (String line : cfg.getStringList(base + "LORE")) {
                    lore.add(MessageUtils.format(line
                            .replace("{players}", String.valueOf(playersInWorld))
                            .replace("{ping}", String.valueOf(ping))
                            .replace("{pool}", String.valueOf(poolReady))));
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(displayName);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
    }

    /**
     * Opens a region sub-menu for the given dimension key (OVERWORLD, NETHER, THE_END).
     * Returns false if region menus are disabled or no section exists.
     */
    public boolean openRegionMenu(Player player, String dimensionKey) {
        FileConfiguration cfg = plugin.getConfig();

        if (!cfg.getBoolean("REGION-MENUS.ENABLED", false)) return false;

        String sectionPath = "REGION-MENUS." + dimensionKey;
        ConfigurationSection section = cfg.getConfigurationSection(sectionPath);
        if (section == null) return false;

        String title    = MessageUtils.format(section.getString("TITLE", "&8Region Select"));
        int size        = section.getInt("SIZE", 27);
        boolean ph      = section.getBoolean("PLACEHOLDER", true);

        Inventory inv = Bukkit.createInventory(null, size, title);
        if (ph) fillPlaceholder(inv, size);

        ConfigurationSection buttons = section.getConfigurationSection("BUTTONS");
        if (buttons != null) {
            for (String key : buttons.getKeys(false)) {
                String base = sectionPath + ".BUTTONS." + key + ".";
                Material mat     = getMaterial(cfg.getString(base + "MATERIAL", "GRASS_BLOCK"));
                String dispName  = MessageUtils.format(cfg.getString(base + "DISPLAY-NAME", key));
                int slot         = cfg.getInt(base + "SLOT", 13);
                int ping         = player.getPing();

                List<String> lore = new ArrayList<>();
                for (String line : cfg.getStringList(base + "LORE")) {
                    lore.add(MessageUtils.format(line.replace("{ping}", String.valueOf(ping))));
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta  = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(dispName);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        player.openInventory(inv);
        return true;
    }

    // -----------------------------------------------------------------------

    private void fillPlaceholder(Inventory inv, int size) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) { paneMeta.setDisplayName(" "); pane.setItemMeta(paneMeta); }
        for (int i = 0; i < size; i++) inv.setItem(i, pane);
    }

    private Material getMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.GRASS_BLOCK; }
    }
}
