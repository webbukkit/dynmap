package org.dynmap.fabric_1_16_4.permissions;

import net.minecraft.entity.player.PlayerEntity;
import org.dynmap.Log;
import org.dynmap.fabric_1_16_4.DynmapPlugin;

import java.util.HashSet;
import java.util.Set;

public class OpPermissions implements PermissionProvider {
    public HashSet<String> usrCommands = new HashSet<String>();

    public OpPermissions(String[] usrCommands) {
        for (String usrCommand : usrCommands) {
            this.usrCommands.add(usrCommand);
        }
        Log.info("Using ops.txt for access control");
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> rslt = new HashSet<String>();
        if (DynmapPlugin.plugin.isOp(player)) {
            rslt.addAll(perms);
        }
        return rslt;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return DynmapPlugin.plugin.isOp(player);
    }

    @Override
    public boolean has(PlayerEntity psender, String permission) {
        if (psender != null) {
            if (usrCommands.contains(permission)) {
                return true;
            }
            return DynmapPlugin.plugin.isOp(psender.getName().getString());
        }
        return true;
    }

    @Override
    public boolean hasPermissionNode(PlayerEntity psender, String permission) {
        if (psender != null) {
            return DynmapPlugin.plugin.isOp(psender.getName().getString());
        }
        return true;
    }
}
