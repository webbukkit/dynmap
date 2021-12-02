package org.dynmap.forge_1_18.permissions;

import java.util.Set;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionProvider {
    boolean has(ServerPlayer sender, String permission);
    boolean hasPermissionNode(ServerPlayer sender, String permission); 
    
    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
