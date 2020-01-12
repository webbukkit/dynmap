package org.dynmap.common;

import org.dynmap.DynmapLocation;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Player (server neutral) - represents online or offline player
 */
public interface DynmapPlayer extends DynmapCommandSender {
    /**
     * Get player ID
     *
     * @return ID (case insensitive)
     */
    String getName();

    /**
     * Get player display name
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Is player online?
     *
     * @return true if online
     */
    boolean isOnline();

    /**
     * Get current location of player
     *
     * @return location
     */
    DynmapLocation getLocation();

    /**
     * Get world ID of player
     *
     * @return id
     */
    String getWorld();

    /**
     * Get connected address for player
     *
     * @return connection address, or null if unknown
     */
    InetSocketAddress getAddress();

    /**
     * Check if player is sneaking
     *
     * @return true if sneaking
     */
    boolean isSneaking();

    /**
     * Get health
     *
     * @return health points
     */
    double getHealth();

    /**
     * Get armor points
     *
     * @return armor points
     */
    int getArmorPoints();

    /**
     * Get spawn bed location
     *
     * @return bed location, or null if none
     */
    DynmapLocation getBedSpawnLocation();

    /**
     * Get last login time
     *
     * @return UTC time (msec) of last login
     */
    long getLastLoginTime();

    /**
     * Get first login time
     *
     * @return UTC time (msec) of first login
     */
    long getFirstLoginTime();

    /**
     * Is invisible
     *
     * @return true if invisible
     */
    boolean isInvisible();

    /**
     * Get sort weight (ordered lowest to highest in player list: 0=default)
     *
     * @return sort weight
     */
    int getSortWeight();

    /**
     * Set sort weight (ordered lowest to highest in player list: 0=default)
     *
     * @param wt - sort weight
     */
    void setSortWeight(int wt);

    /**
     * Get skin URL for player
     *
     * @return URL, or null if not available
     */
    default String getSkinURL() {
        return null;
    }

    /**
     * Get player UUID
     * Return UUID, or null if not available
     */
    default UUID getUUID() {
        return null;
    }
}
