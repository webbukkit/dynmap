package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;

public class PEXPermissions implements PermissionProvider {
    String name;
    PermissionManager pm;

    public static PEXPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("PermissionsEx");
        if (permissionsPlugin == null)
            return null;

        try {
            Class.forName("ru.tehkode.permissions.bukkit.PermissionsEx");
        } catch (ClassNotFoundException e) {
            return null;
        }

        server.getPluginManager().enablePlugin(permissionsPlugin);
        if(permissionsPlugin.isEnabled() == false)
            return null;

        //Broken in new dev builds, apparently
        //if(PermissionsEx.isAvailable() == false)
        //    return null;
        Log.info("Using PermissionsEx " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new PEXPermissions(name);
    }

    public PEXPermissions(String name) {
        this.name = name;
        pm = PermissionsEx.getPermissionManager();
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return (player != null) ? pm.has(player, name + "." + permission) : true;
    }
    
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> hasperms = new HashSet<String>();
        PermissionUser pu = pm.getUser(player);
        if(pu != null) {
            for (String pp : perms) {
                if (pu.has(name + "." + pp)) {
                    hasperms.add(pp);
                }
            }
        }
        return hasperms;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        PermissionUser pu = pm.getUser(player);
        if(pu != null) {
            return pu.has(name + "." + perm);
        }
        return false;
    }
}
