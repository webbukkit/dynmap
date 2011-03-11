package org.dynmap.permissions;

import org.bukkit.command.CommandSender;

public interface PermissionProvider {
    boolean has(CommandSender sender, String permission);
}
