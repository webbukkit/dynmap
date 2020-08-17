package org.dynmap.forge_1_12_2.permissions;

import java.util.HashSet;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.dynmap.Log;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class Sponge7Permissions implements PermissionProvider {

    public static Sponge7Permissions create() {
        try {
            Class.forName("org.spongepowered.api.Sponge");    /* See if class exists */
        } catch (ClassNotFoundException cnfx) {
            return null;
        }
        Log.info("Using Sponge Permissions for access control");
        Log.info("Web interface permissions only available for online users");
        Log.info("Note: you may need to give users permissions for base commands (e.g. dynmap.command.* on LuckPerms) as well as for specific actions");
        return new Sponge7Permissions();
    }

    private Sponge7Permissions() {
    }

    @Override
    public boolean has(ICommandSender sender, String permission) {
    	return sender.canUseCommand(4, "dynmap." + permission);
    }
    
    @Override
    public boolean hasPermissionNode(ICommandSender sender, String permission) {
    	return sender.canUseCommand(4, "dynmap." + permission);
    }
    
    @Override
    public Set<String> hasOfflinePermissions(String player, Set<String> perms) {
        HashSet<String> rslt = new HashSet<String>();
    	Optional<Player> p = Sponge.getServer().getPlayer(player);
    	if (p.isPresent()) {
    		Player plyr = p.get();
    		for (String perm : perms) {
    			if (plyr.hasPermission("dynmap." + perm)) {
    				rslt.add(perm);
    			}
    		}
        }
        return rslt;
    }

    @Override
    public boolean hasOfflinePermission(String player, String perm) {
    	Optional<Player> p = Sponge.getServer().getPlayer(player);
    	if (p.isPresent()) {
    		Player plyr = p.get();
    		return plyr.hasPermission("dynmap." + perm);
        }
    	return false;
    }
}
