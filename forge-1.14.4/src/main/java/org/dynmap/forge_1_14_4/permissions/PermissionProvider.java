package org.dynmap.forge_1_14_4.permissions;

import java.util.Set;

import net.minecraft.command.ICommandSource;

public interface PermissionProvider {
    boolean has(ICommandSource sender, String permission);
    boolean hasPermissionNode(ICommandSource sender, String permission); 
    
    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
