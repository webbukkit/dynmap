package org.dynmap.bukkit.permissions;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.dynmap.Log;
import org.dynmap.bukkit.DynmapPlugin;

import java.util.HashSet;
import java.util.Set;

public class VaultPermissions implements PermissionProvider, Listener {
    private RegisteredServiceProvider<Permission> permissionProvider;
    private final String prefix;

    public static VaultPermissions create(DynmapPlugin plugin, String name) {
        try {
            Class.forName("net.milkbowl.vault.permission.Permission");
        } catch (ClassNotFoundException cnfx) {
            return null;
        }

        RegisteredServiceProvider<Permission> provider = plugin.getServer().getServicesManager().getRegistration(Permission.class);
        if (provider == null || provider.getProvider() == null)
            return null;

        Log.info("Using Vault provider " + provider.getProvider().getName() + " for access control");
        VaultPermissions ret = new VaultPermissions(name, provider);
        plugin.getServer().getPluginManager().registerEvents(ret, plugin);
        return ret;
    }

    private VaultPermissions(String prefix, RegisteredServiceProvider<Permission> initialProvider) {
        this.prefix = prefix;
        this.permissionProvider = initialProvider;
    }

    /**
     * Update the used permission provider if a new one becomes available
     *
     * @param event The event with new service registration details
     */
    @SuppressWarnings("unchecked")
    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService().equals(Permission.class)) {
        	RegisteredServiceProvider<Permission> newProvider = (RegisteredServiceProvider<Permission>) event.getProvider();
            if (newProvider != this.permissionProvider && newProvider.getPriority().compareTo(this.permissionProvider.getPriority()) >= 0) {
                this.permissionProvider = newProvider;
                Log.info("Using Vault provider " + this.permissionProvider.getProvider().getName() + " for access control");
            }
        }
    }

    @Override
    public boolean has(CommandSender sender, String permission) {
        return permissionProvider.getProvider().has(sender, processPermission(permission));
    }

    @Override
    public Set<String> hasOfflinePermissions(String playerName, Set<String> perms) {
        final Permission vault = this.permissionProvider.getProvider();
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);

        Set<String> hasperms = new HashSet<>();

        for (String perm : perms) {
            if (vault.playerHas(null, player, processPermission(perm))) {
                hasperms.add(perm);
            }
        }

        return hasperms;
    }

    @Override
    public boolean hasOfflinePermission(String playerName, String perm) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return permissionProvider.getProvider().playerHas(null, player, processPermission(perm));
    }

    private String processPermission(String perm) {
        return prefix + "." + perm;
    }
}
