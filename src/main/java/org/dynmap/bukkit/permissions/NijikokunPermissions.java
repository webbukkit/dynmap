package org.dynmap.bukkit.permissions;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class NijikokunPermissions implements PermissionProvider {
    String name;
    PermissionHandler permissions;
    Plugin plugin;
    
    public static NijikokunPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("Permissions");
        if (permissionsPlugin == null)
            return null;
        
        server.getPluginManager().enablePlugin(permissionsPlugin);
        Log.info("Using Permissions " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new NijikokunPermissions(permissionsPlugin, name);
    }

    public NijikokunPermissions(Plugin permissionsPlugin, String name) {
        this.name = name;
        plugin = permissionsPlugin;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        if(permissions == null)
            permissions = ((Permissions)plugin).getHandler();
        Player player = sender instanceof Player ? (Player) sender : null;
        return player != null
                ? permissions.has(player, name + "." + permission) || permissions.has(player, name + ".*")
                : true;
    }
}
