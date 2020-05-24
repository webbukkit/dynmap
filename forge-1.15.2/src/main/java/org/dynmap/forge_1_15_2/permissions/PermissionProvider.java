package org.dynmap.forge_1_15_2.permissions;

import java.util.Set;

import net.minecraft.entity.player.PlayerEntity;

public interface PermissionProvider {
    boolean has(PlayerEntity sender, String permission);
    boolean hasPermissionNode(PlayerEntity sender, String permission); 
    
    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
