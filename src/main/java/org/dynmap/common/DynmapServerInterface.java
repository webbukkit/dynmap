package org.dynmap.common;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.dynmap.common.DynmapListenerManager.EventType;

/**
 * This interface defines a server-neutral interface for the DynmapCore and other neutral components to use to access server provided
 * services.  Platform-specific plugin must supply DynmapCore with an instance of an object implementing this interface.
 */
public interface DynmapServerInterface {
    /**
     * Schedule task to run on server-safe thread (one suitable for other server API calls)
     * @param run - runnable method
     * @param delay - delay in server ticks (50msec)
     */
    public void scheduleServerTask(Runnable run, long delay);
    /**
     * Call method on server-safe thread
     * @param call - Callable method
     * @return future for completion of call
     */
    public <T> Future<T> callSyncMethod(Callable<T> task);
    /**
     * Get list of online players
     * @return list of online players
     */
    public DynmapPlayer[] getOnlinePlayers();
    /**
     * Request reload of plugin
     */
    public void reload();
    /**
     * Get active player
     * @param name - player name
     * @return player
     */
    public DynmapPlayer getPlayer(String name);
    /**
     * Get banned IPs
     */
    public Set<String> getIPBans();
    /**
     * Get server name
     */
    public String getServerName();
    /**
     * Test if player ID is banned
     */
    public boolean isPlayerBanned(String pid);    
    /**
     * Strip out chat color
     */
    public String stripChatColor(String s);
    /**
     * Request notificiation for given events (used by DynmapListenerManager)
     */
    public boolean requestEventNotification(EventType type);
    /**
     * Send notification of web chat message
     * @param source - source
     * @param name - name
     * @param msg - message text
     * @return true if not cancelled
     */
    public boolean sendWebChatEvent(String source, String name, String msg);
    /**
     * Broadcast message to players
     * @param msg
     */
    public void broadcastMessage(String msg);
    /**
     * Get Biome ID list
     */
    public String[] getBiomeIDs();
}
