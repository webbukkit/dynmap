package org.dynmap.common;

import java.net.InetSocketAddress;

import org.dynmap.DynmapLocation;

/**
 * Player (server neutral) - represents online or offline player 
 */
public interface DynmapPlayer extends DynmapCommandSender {
    /**
     * Get player ID
     * @return ID (case insensitive)
     */
    public String getName();
    /**
     * Get player display name
     * @return display name
     */
    public String getDisplayName();
    /**
     * Is player online?
     * @return true if online
     */
    public boolean isOnline();
    /**
     * Get current location of player
     * @return location
     */
    public DynmapLocation getLocation();
    /**
     * Get world ID of player
     * @return id
     */
    public String getWorld();
    /**
     * Get connected address for player
     */
    public InetSocketAddress getAddress();
    /**
     * Check if player is sneaking
     */
    public boolean isSneaking();
    /**
     * Get health
     */
    public int getHealth();
    /**
     * Get armor points
     */
    public int getArmorPoints();
    /**
     * Get spawn bed location
     */
    public DynmapLocation getBedSpawnLocation();
}
