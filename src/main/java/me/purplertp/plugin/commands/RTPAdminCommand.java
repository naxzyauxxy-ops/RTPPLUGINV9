package me.purplertp.plugin.commands;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class RTPAdminCommand implements CommandExecutor {

    private static final String PREFIX = "&5&l[&dRTP Admin&5&l] &r";
    private final PurpleRTP plugin;

    public RTPAdminCommand(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("purplertp.admin")) {
            sender.sendMessage(MessageUtils.format(PREFIX + "&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reloadConfig();
                plugin.getLocationPoolManager().shutdown();
                plugin.getLocationPoolManager().startPoolFilling();
                sender.sendMessage(MessageUtils.format(PREFIX + "&dConfig reloaded! Pool refilling..."));
            }

            case "clearcooldown" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.format(PREFIX + "&cUsage: /rtpadmin clearcooldown <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtils.format(PREFIX + "&cPlayer not found."));
                    return true;
                }
                plugin.getCooldownManager().removeCooldown(target.getUniqueId());
                sender.sendMessage(MessageUtils.format(PREFIX + "&dCleared all cooldowns for &5" + target.getName()));
            }

            case "forcertp" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtils.format(PREFIX + "&cUsage: /rtpadmin forcertp <player> [world]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(MessageUtils.format(PREFIX + "&cPlayer not found."));
                    return true;
                }
                String worldName = args.length >= 3 ? args[2]
                        : (target.getWorld() != null ? target.getWorld().getName() : "world");
                sender.sendMessage(MessageUtils.format(PREFIX + "&dForce-RTPing &5" + target.getName() + " &din &5" + worldName));
                plugin.getRtpManager().randomTeleport(target, worldName);
            }

            case "pool" -> {
                sender.sendMessage(MessageUtils.format(PREFIX + "&dLocation Pool Status:"));
                ConfigurationSection ws = plugin.getConfig().getConfigurationSection("WORLD-SETTINGS");
                if (ws == null) return true;
                int poolSize = plugin.getConfig().getInt("SETTINGS.POOL-SIZE", 20);
                for (String worldName : ws.getKeys(false)) {
                    int size = plugin.getLocationPoolManager().poolSize(worldName);
                    String bar = buildBar(size, poolSize);
                    sender.sendMessage(MessageUtils.format("  &7" + worldName + ": &b" + size + "&7/" + poolSize + " " + bar));
                }
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtils.format("&5&l--- &dPurpleRTP Admin &5&l---"));
        sender.sendMessage(MessageUtils.format("&5/rtpadmin reload &d- Reload config"));
        sender.sendMessage(MessageUtils.format("&5/rtpadmin clearcooldown <player> &d- Clear all cooldowns"));
        sender.sendMessage(MessageUtils.format("&5/rtpadmin forcertp <player> [world] &d- Force RTP"));
        sender.sendMessage(MessageUtils.format("&5/rtpadmin pool &d- Show pre-generated location pool status"));
    }

    private String buildBar(int current, int max) {
        StringBuilder filled = new StringBuilder("&7[");
        int bars = 20;
        int fill = max > 0 ? (int)((current / (double) max) * bars) : 0;
        filled.append("&a|".repeat(fill));
        filled.append("&8|".repeat(bars - fill));
        filled.append("&7]");
        return filled.toString();
    }
}
