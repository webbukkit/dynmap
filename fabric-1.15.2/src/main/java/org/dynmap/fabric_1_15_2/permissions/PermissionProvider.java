package org.dynmap.fabric_1_15_2.permissions;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Set;

public interface PermissionProvider {
    boolean has(PlayerEntity sender, String permission);

    boolean hasPermissionNode(PlayerEntity sender, String permission);

    Set<String> hasOfflinePermissions(String player, Set<String> perms);

    boolean hasOfflinePermission(String player, String perm);

}
