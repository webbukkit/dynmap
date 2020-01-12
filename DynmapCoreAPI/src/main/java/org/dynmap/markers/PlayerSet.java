package org.dynmap.markers;

import java.util.Set;

/**
 * Interface defining a set of players that can be made visible on the map to other players with the needed permissions.
 * The set can have symmetric access - meaning that any player in the set can see the other players in the set.
 * In any case, players with the permission node 'dynmap.playerset.&lt;set-id&gt;' can see players in the set.
 */
public interface PlayerSet {
    /**
     * Get player set ID
     *
     * @return set ID
     */
    String getSetID();

    /**
     * Get player in set
     *
     * @return set of player IDs
     */
    Set<String> getPlayers();

    /**
     * Set players in set (replace existing list)
     *
     * @param players - set of players
     */
    void setPlayers(Set<String> players);

    /**
     * Add player to set
     *
     * @param player - player ID
     */
    void addPlayer(String player);

    /**
     * Delete player from set
     *
     * @param player - player ID
     */
    void removePlayer(String player);

    /**
     * Test if player is in set
     *
     * @param player - player ID
     * @return true if in set, false if not
     */
    boolean isPlayerInSet(String player);

    /**
     * Delete player set
     */
    void deleteSet();

    /**
     * Test if set is symmetric (players in set can see other players in set)
     *
     * @return true if players in set can see other players in set (independent of privileges)
     */
    boolean isSymmetricSet();

    /**
     * Set the symmetric access for the set
     *
     * @param symmetric - true=players in set can see players in set, false=privilege is always required
     */
    void setSymmetricSet(boolean symmetric);

    /**
     * Test if set is persistent (stored across restarts)
     *
     * @return true if persistent, false if transient
     */
    boolean isPersistentSet();
}
