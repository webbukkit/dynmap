package org.dynmap.debug;

import java.util.HashSet;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitPlayerDebugger implements Debugger {
    private JavaPlugin plugin;
    private HashSet<Player> debugees = new HashSet<Player>();
    private String debugCommand;
    private String undebugCommand;
    private String prepend;

    public BukkitPlayerDebugger(JavaPlugin plugin, Map<String, Object> configuration) {
        this.plugin = plugin;

        String name = "dynmap";
        debugCommand = "/debug_" + name;
        undebugCommand = "/undebug_" + name;
        prepend = name + ": ";
    }

    public synchronized void enable() {
        plugin.getServer().getPluginManager().registerEvent(Event.Type.PLAYER_COMMAND, new CommandListener(), Priority.Normal, plugin);
        plugin.getServer().getPluginManager().registerEvent(Event.Type.PLAYER_QUIT, new CommandListener(), Priority.Normal, plugin);
    }

    public synchronized void disable() {
        clearDebugees();
    }

    public synchronized void addDebugee(Player p) {
        debugees.add(p);
    }

    public synchronized void removeDebugee(Player p) {
        debugees.remove(p);
    }

    public synchronized void clearDebugees() {
        debugees.clear();
    }

    public synchronized void sendToDebuggees(String message) {
        for (Player p : debugees) {
            p.sendMessage(prepend + message);
        }
    }

    public synchronized void debug(String message) {
        sendToDebuggees(message);
    }

    public synchronized void error(String message) {
        sendToDebuggees(prepend + ChatColor.RED + message);
    }

    public synchronized void error(String message, Throwable thrown) {
        sendToDebuggees(prepend + ChatColor.RED + message);
        sendToDebuggees(thrown.toString());
    }

    protected class CommandListener extends PlayerListener {
        @Override
        public void onPlayerCommand(PlayerChatEvent event) {
            String[] split = event.getMessage().split(" ");
            Player player = event.getPlayer();
            if (split[0].equalsIgnoreCase(debugCommand)) {
                addDebugee(player);
                event.setCancelled(true);
            } else if (split[0].equalsIgnoreCase(undebugCommand)) {
                removeDebugee(player);
                event.setCancelled(true);
            }
        }

        @Override
        public void onPlayerQuit(PlayerEvent event) {
            removeDebugee(event.getPlayer());
        }
    }
}
