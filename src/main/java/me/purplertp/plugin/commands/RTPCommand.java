package me.purplertp.plugin.commands;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.gui.RTPMenu;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class RTPCommand implements CommandExecutor {

    private final PurpleRTP plugin;
    private final RTPMenu menu;
    private final me.purplertp.plugin.gui.RTPMenuListener listener;

    public RTPCommand(PurpleRTP plugin, me.purplertp.plugin.gui.RTPMenuListener listener) {
        this.plugin   = plugin;
        this.menu     = new RTPMenu(plugin);
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("purplertp.use")) {
            player.sendMessage(MessageUtils.format("&cYou don't have permission to use RTP."));
            return true;
        }

        if (!plugin.getConfig().getBoolean("ENABLED", true)) {
            String msg = plugin.getConfig().getString("MESSAGES.DISABLED", "&cRTP is disabled.");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(MessageUtils.format(msg)));
            return true;
        }

        // Denied worlds
        List<String> denied = plugin.getConfig().getStringList("DENIED-WORLDS");
        if (player.getWorld() != null && denied.contains(player.getWorld().getName())) {
            player.sendMessage(MessageUtils.format("&cRTP is not allowed in this world."));
            return true;
        }

        menu.open(player, listener);
        return true;
    }
}
