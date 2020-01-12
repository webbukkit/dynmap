package org.dynmap.bukkit.permissions;

import de.bananaco.bpermissions.api.User;
import de.bananaco.bpermissions.api.WorldManager;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.dynmap.Log;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class bPermPermissions extends BukkitPermissions {
    WorldManager wm;

    public static bPermPermissions create(Server server, String name, Map<String,Boolean> pd) {
        Plugin permissionsPlugin = server.getPluginManager().getPlugin("bPermissions");
        if (permissionsPlugin == null)
            return null;
        server.getPluginManager().enablePlugin(permissionsPlugin);
        if (!permissionsPlugin.isEnabled())
            return null;
        
        Log.info("Using bPermissions " + permissionsPlugin.getDescription().getVersion() + " for access control");
        return new bPermPermissions(name, pd);
    }

    public bPermPermissions(String name, Map<String,Boolean> pd) {
        super(name, pd);
        wm = WorldManager.getInstance();
    }

    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> hasperms = new HashSet<>();
        User usr = wm.getDefaultWorld().getUser(player);
        if(usr != null) {
            try {
                usr.calculateEffectivePermissions();
            } catch (Exception ignored) {
            }
            Map<String, Boolean> p = usr.getMappedPermissions();
            for (String pp : perms) {
                String permval = name + "." + pp;
                Boolean v = p.get(permval);
                if (v != null) {
                    if (v)
                        hasperms.add(permval);
                } else {
                    v = pd.get(permval);
                    if ((v != null) && v)
                        hasperms.add(permval);
                }
            }
        }
        return hasperms;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
        boolean rslt;
        String permval = name + "." + perm;
        User usr = wm.getDefaultWorld().getUser(player);
        if(usr != null) {
            try {
                usr.calculateEffectivePermissions();
            } catch (Exception ignored) {
            }
            if (usr.getMappedPermissions().containsKey(permval)) {
                rslt = usr.hasPermission(permval);
            } else {
                Boolean v = pd.get(permval);
                if (v != null)
                    rslt = v;
                else
                    rslt = false;
            }
        }
        else {
            rslt = false;
        }
        return rslt;
    }

}
