package org.dynmap.bukkit.permissions;

import java.util.Set;

import org.bukkit.command.CommandSender;

public interface PermissionProvider {
    boolean has(CommandSender sender, String permission);
    
    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
