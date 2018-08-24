package org.dynmap.permissions;

import java.util.Set;


public abstract class PermissionsHandler {
    /**
     * Location to install provider (used by DynmapCBBridge)
     */
    private static PermissionsHandler handler = null;
    
    /**
     * Set handler (used by DynmapCBBridge)
     */
    public static void setHandler(PermissionsHandler ph) {
        handler = ph;
    }
    /**
     * Get handler
     */
    public static PermissionsHandler getHandler() {
        return handler;
    }
    /**
     * Test if given logged in user has given permissions
     * 
     * @param username - user name
     * @param perm - permission (relative ID - e.g. 'dynmap.fullrender' is 'fullrender')
     * @return true if has permission, false if not
     * 
     */
    public abstract boolean hasPermission(String username, String perm);
    /**
     * Test if given logged in user has given permission node
     * 
     * @param username - user name
     * @param perm - permission (relative ID - e.g. 'dynmap.fullrender' is 'fullrender')
     * @return true if has permission, false if not
     * 
     */
    public abstract boolean hasPermissionNode(String username, String perm);
    /**
     * Test if given (potentially offline) user has the given permissions
     * 
     * @param username - user name
     * @param perms - permissions to be tested
     * @return set of permissions granted to user
     */
    public abstract Set<String> hasOfflinePermissions(String username, Set<String> perms);
    /**
     * Test if given (potentially offline) user has the given permission
     * 
     * @param player - user name
     * @param perm - permission
     * @return true if has permission, false if not
     */
    public abstract boolean hasOfflinePermission(String player, String perm);
}
