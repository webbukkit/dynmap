package org.dynmap.bukkit.permissions;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import ru.tehkode.permissions.bukkit.PermissionsEx;

import de.bananaco.bpermissions.api.ApiLayer;
import de.bananaco.bpermissions.api.util.CalculableType;

public class bPermPermissions implements PermissionProvider {
    String name;

    public static bPermPermissions create(Server server, String name) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("bPermissions");
        if (permissionsPlugin == null)
            return null;
        server.getPluginManager().enablePlugin(permissionsPlugin);
        Log.info("Using bPermissions " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new bPermPermissions(name);
    }

    public bPermPermissions(String name) {
        this.name = name;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        Player player = sender instanceof Player ? (Player) sender : null;
        return (player != null) ? ApiLayer.hasPermission(player.getWorld().getName(), CalculableType.USER, player.getName(), name + "." + permission) : true;
    }
}
