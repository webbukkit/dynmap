package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;
import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.dataholder.worlds.WorldsHolder;
import org.anjocaido.groupmanager.permissions.AnjoPermissionsHandler;

public class GroupManagerPermissions implements PermissionProvider {
    String name;
    GroupManager gm;
    WorldsHolder wh;

    public static GroupManagerPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("GroupManager");
        if (permissionsPlugin == null)
            return null;
        server.getPluginManager().enablePlugin(permissionsPlugin);
        if(permissionsPlugin.isEnabled() == false)
            return null;
        Log.info("Using GroupManager " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new GroupManagerPermissions(name, permissionsPlugin);
    }

    public GroupManagerPermissions(String name, Plugin permissionsPlugin) {
        this.name = name;
        gm = (GroupManager)permissionsPlugin;
        wh = gm.getWorldsHolder();
    }

    @Override
    public boolean has(CommandSender sender, String permission) {        
        Player player = sender instanceof Player ? (Player) sender : null;
        boolean rslt = (player != null) ? gm.getWorldsHolder().getDefaultWorld().getPermissionsHandler().permission(player, name + "." + permission) : true;
        return rslt;
    }
    
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> hasperms = new HashSet<String>();
        AnjoPermissionsHandler apm = gm.getWorldsHolder().getDefaultWorld().getPermissionsHandler();
        if (apm != null) {
            for (String pp : perms) {
                if (apm.permission(player, name + "." + pp)) {
                    hasperms.add(pp);
                }
            }
        }
        return hasperms;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        AnjoPermissionsHandler apm = gm.getWorldsHolder().getDefaultWorld().getPermissionsHandler();
        boolean rslt = false;
        if(apm != null) {
            rslt = apm.permission(player, name + "." + perm);
        }
        return rslt;
    }
}
