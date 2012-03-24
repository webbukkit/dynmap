package org.dynmap.bukkit.permissions;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import com.nijiko.permissions.PermissionHandler;
import com.platymuus.bukkit.permissions.PermissionsPlugin;

public class PermBukkitPermissions implements PermissionProvider {
    String name;
    PermissionsPlugin plugin;
    
    public static PermBukkitPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("PermissionsBukkit");
        if (permissionsPlugin == null)
            return null;
        
        server.getPluginManager().enablePlugin(permissionsPlugin);
        Log.info("Using PermissionsBukkit " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new PermBukkitPermissions(permissionsPlugin, name);
    }

    public PermBukkitPermissions(Plugin permissionsPlugin, String name) {
        this.name = name;
        plugin = (PermissionsPlugin) permissionsPlugin;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return (player != null) ? plugin.getPlayerInfo(player.getName()).getPermissions().containsKey(name + "." + permission) : true;
    }
}
