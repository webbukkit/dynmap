package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.Log;

public class BukkitPermissions implements PermissionProvider {
    String name;

    public static BukkitPermissions create(String name) {
        try {
            Class.forName("org.bukkit.permissions.PermissibleBase");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        Log.info("Using Bukkit Permissions (superperms) for access control");
        return new BukkitPermissions(name);
    }

    public BukkitPermissions(String name) {
        this.name = name;
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
        Player p = Bukkit.getPlayerExact(name);
        HashSet<String> hasperms = null;
        if (p != null) {
            hasperms = new HashSet<String>();
            for(String perm : perms) {
                if (p.hasPermission(perm)) {
                    hasperms.add(perm);
                }
            }
        }
        return hasperms;
    }

}
