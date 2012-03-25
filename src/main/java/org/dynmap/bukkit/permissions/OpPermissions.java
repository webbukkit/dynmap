package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.Log;

public class OpPermissions implements PermissionProvider {
    public HashSet<String> opCommands = new HashSet<String>();

    public OpPermissions(String[] opCommands) {
        for (String opCommand : opCommands) {
            this.opCommands.add(opCommand);
        }
        Log.info("Using ops.txt for access control");
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return (sender instanceof Player)
            ? opCommands.contains(permission)
                ? ((Player) sender).isOp()
                : true
            : true;
    }
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        return null;
    }
    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        return false;
    }
}
