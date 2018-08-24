package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.Log;

public class BukkitPermissions implements PermissionProvider {
    protected String name;
    protected Map<String, Boolean> pd;

    public static BukkitPermissions create(String name, Map<String,Boolean> pd) {
        try {
            Class.forName("org.bukkit.permissions.PermissibleBase");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        Log.info("Using Bukkit Permissions (superperms) for access control");
        Log.info("Web interface permissions only available for online users");
        return new BukkitPermissions(name, pd);
    }

    public BukkitPermissions(String name, Map<String, Boolean> pd) {
        this.name = name;
        this.pd = pd;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return player != null
                ? player.hasPermission(name + "." + permission) || player.hasPermission(name + ".*")
                : true;
    }
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        Player p = Bukkit.getPlayerExact(player);
        HashSet<String> hasperms = null;
        if (p != null) {
            hasperms = new HashSet<String>();
            for(String perm : perms) {
                if (p.hasPermission(name + "." + perm)) {
                    hasperms.add(perm);
                }
            }
        }
        return hasperms;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        Player p = Bukkit.getPlayerExact(player);
        if (p != null) {
            return p.hasPermission(name + "." + perm);
        }
        else {
            OfflinePlayer op = Bukkit.getOfflinePlayer(player);
            if((op != null) && op.isOp()) {
                return true;
            }
            return false;
        }
    }
}
