package org.dynmap.fabric_26_1.permissions;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.world.entity.player.Player;
import org.dynmap.Log;
import org.dynmap.fabric_26_1.DynmapPlugin;
import org.dynmap.json.simple.parser.JSONParser;

import java.util.Set;
import java.util.stream.Collectors;

public class FabricPermissions implements PermissionProvider {

    private String permissionKey(String perm) {
        return "dynmap." + perm;
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        return perms.stream()
                .filter(perm -> hasOfflinePermission(player, perm))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return DynmapPlugin.plugin.isOp(player.toLowerCase());
    }

    @Override
    public boolean has(Player player, String permission) {
        if (player == null) return false;
        String name = player.getName().getString().toLowerCase();
        if (DynmapPlugin.plugin.isOp(name)) return true;
        // return Permissions.check(player, permissionKey(permission));
        return true; // TODO: implement proper permission checks (waiting on fabric-permissions-api update)
    }

    @Override
    public boolean hasPermissionNode(Player player, String permission) {
        if (player != null) {
            String name = player.getName().getString().toLowerCase();
            return DynmapPlugin.plugin.isOp(name);
        }
        return false;
    }

}
