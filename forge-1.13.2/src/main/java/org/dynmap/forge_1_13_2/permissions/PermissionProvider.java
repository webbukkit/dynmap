package org.dynmap.forge_1_13_2.permissions;

import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;

public interface PermissionProvider {
    boolean has(EntityPlayer sender, String permission);
    boolean hasPermissionNode(EntityPlayer sender, String permission); 
    
    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
