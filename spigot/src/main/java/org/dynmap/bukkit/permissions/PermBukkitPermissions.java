package org.dynmap.bukkit.permissions;

import java.util.Map;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import com.platymuus.bukkit.permissions.PermissionsPlugin;

public class PermBukkitPermissions extends BukkitPermissions {
    PermissionsPlugin plugin;
    Map<String, Boolean> pd;
    
    public static PermBukkitPermissions create(Server server, String name, Map<String, Boolean> pd) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("PermissionsBukkit");
        if (permissionsPlugin == null)
            return null;
        
        server.getPluginManager().enablePlugin(permissionsPlugin);
        if(permissionsPlugin.isEnabled() == false)
            return null;
        
        Log.info("Using PermissionsBukkit " + permissionsPlugin.getDescription().getVersion() + " for access control");
        Log.info("Web interface permissions only available for online users");
        return new PermBukkitPermissions(permissionsPlugin, name, pd);
    }

    public PermBukkitPermissions(Plugin permissionsPlugin, String name, Map<String, Boolean> pd) {
        super(name, pd);
        plugin = (PermissionsPlugin) permissionsPlugin;
        this.pd = pd;
    }
}
