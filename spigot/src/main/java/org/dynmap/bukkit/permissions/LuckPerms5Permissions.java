package org.dynmap.bukkit.permissions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.query.QueryOptions;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.dynmap.Log;

public class LuckPerms5Permissions implements PermissionProvider {
    String name;
    LuckPerms luckPerms;

    public static LuckPerms5Permissions create(Server server, String name) {
        try {
            Class.forName("net.luckperms.api.LuckPerms");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        if (!server.getPluginManager().isPluginEnabled("LuckPerms"))
            return null;
        LuckPerms luckPerms = server.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null)
            return null;
        Log.info("Using LuckPerms " + luckPerms.getPluginMetadata().getVersion() + " for access control");
        return new LuckPerms5Permissions(name, luckPerms);
    }

    public LuckPerms5Permissions(String name, LuckPerms luckPerms) {
        this.name = name;
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(name + "." + permission);
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        Set<String> result = new HashSet<>();
        CachedPermissionData user = getUser(player);
        if (user != null) {
            for (String p : perms) {
                if (user.checkPermission(name + "." + p).asBoolean())
                    result.add(p);
            }
        }
        return result;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        CachedPermissionData user = getUser(player);
        if (user == null)
            return false;
        return user.checkPermission(name + "." + perm).asBoolean();
    }

    private CachedPermissionData getUser(String username) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        UUID uuid;

        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null)
            uuid = offlinePlayer.getUniqueId();
        else
            uuid = joinFuture(luckPerms.getUserManager().lookupUniqueId(username));

        if (uuid == null)
            return null;

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            joinFuture(luckPerms.getUserManager().loadUser(uuid));
            user = luckPerms.getUserManager().getUser(uuid);
        }

        if (user == null)
            return null;

        CachedDataManager data = user.getCachedData();
        return luckPerms
            .getContextManager()
            .getQueryOptions(user)
            .map(queryOptions -> data.getPermissionData(queryOptions))
            .orElse(null);
    }

    private static <T> T joinFuture(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
