package org.dynmap.common;

import java.net.InetSocketAddress;
import java.util.UUID;

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
     * @return connection address, or null if unknown
     */
    public InetSocketAddress getAddress();
    /**
     * Check if player is sneaking
     * @return true if sneaking
     */
    public boolean isSneaking();
    /**
     * Get health
     * @return health points
     */
    public double getHealth();
    /**
     * Get armor points
     * @return armor points
     */
    public int getArmorPoints();
    /**
     * Get spawn bed location
     * @return bed location, or null if none
     */
    public DynmapLocation getBedSpawnLocation();
    /**
     * Get last login time
     * @return UTC time (msec) of last login
     */
    public long getLastLoginTime();
    /**
     * Get first login time
     * @return UTC time (msec) of first login
     */
    public long getFirstLoginTime();
    /**
     * Is invisible
     * @return true if invisible
     */
    public boolean isInvisible();
    /**
     * Get sort weight (ordered lowest to highest in player list: 0=default)
     * @return sort weight
     */
    public int getSortWeight();
    /**
     * Set sort weight (ordered lowest to highest in player list: 0=default)
     * @param wt - sort weight
     */
    public void setSortWeight(int wt);
    /**
     * Get skin URL for player
     * @return URL, or null if not available
     */
    public default String getSkinURL() { return null; }
    /**
     * Get player UUID
     * Return UUID, or null if not available
     */
    public default UUID getUUID() { return null; }
    /**
     * Send title and subtitle text (called from server thread)
     */
    public default void sendTitleText(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTIcks) {
    	// Fallback if not implemented
    	if (title != null) this.sendMessage(title);;
    	if (subtitle != null) this.sendMessage(subtitle);
	}
}
