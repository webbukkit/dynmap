package org.dynmap.bukkit.permissions;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.dynmap.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class OpPermissions implements PermissionProvider {
    public HashSet<String> opCommands = new HashSet<>();

    public OpPermissions(String[] opCommands) {
        this.opCommands.addAll(Arrays.asList(opCommands));
        Log.info("Using ops.txt for access control");
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return (!(sender instanceof Player)) || (!opCommands.contains(permission) || sender.isOp());
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
