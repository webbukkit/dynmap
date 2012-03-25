package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import ru.tehkode.permissions.bukkit.PermissionsEx;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;
import de.bananaco.bpermissions.api.util.Permission;

public class bPermPermissions implements PermissionProvider {
    String name;
    String defworld;

    public static bPermPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("bPermissions");
        if (permissionsPlugin == null)
            return null;
        server.getPluginManager().enablePlugin(permissionsPlugin);
        Log.info("Using bPermissions " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new bPermPermissions(name);
    }

    public bPermPermissions(String name) {
        this.name = name;
        defworld = Bukkit.getServer().getWorlds().get(0).getName();
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return (player != null) ? ApiLayer.hasPermission(defworld, CalculableType.USER, player.getName(), name + "." + permission) : true;
    }
    
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> hasperms = new HashSet<String>();
        for (String pp : perms) {
            if(ApiLayer.hasPermission(defworld, CalculableType.USER, player, name + "." + pp)) {
                hasperms.add(pp);
            }
        }
        return hasperms;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return ApiLayer.hasPermission(defworld, CalculableType.USER, player, name + "." + perm);
    }

}
