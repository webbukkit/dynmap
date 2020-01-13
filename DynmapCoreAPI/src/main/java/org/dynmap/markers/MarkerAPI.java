package org.dynmap.markers;

import java.io.InputStream;
import java.util.Set;

/**
 * This defines the public interface to the MarkerAPI (as retrieved by the getMarkerAPI() method in the DynmapPlugin class).  
 */
public interface MarkerAPI {
    /**
     * Get set of defined marker sets
     * @return set of marker sets
     */
    public Set<MarkerSet> getMarkerSets();
    /**
     * Find marker set by ID
     * @param id - ID of marker set
     * @return marker set, or null if not found
     */
    public MarkerSet getMarkerSet(String id);
    /**
     * Create marker set
     * @param id - ID for marker set (must be unique among marker set - limit to alphanumerics, periods, underscores)
     * @param lbl - Label for marker set
     * @param iconlimit - set of allowed marker icons (if null, any marker icon can be used in set)
     * @param persistent - if true, set is persistent (and can contain persistent markers)
     * @return marker set, or null if failed to be created
     */
    public MarkerSet createMarkerSet(String id, String lbl, Set<MarkerIcon> iconlimit, boolean persistent);
    /**
     * Get set of defined marker icons
     * @return set of marker icons
     */
    public Set<MarkerIcon> getMarkerIcons();
    /**
     * Find marker icon by ID
     * @param id - ID of marker icon
     * @return marker icon, or null if not found
     */
    public MarkerIcon getMarkerIcon(String id);
    /**
     * Register a new marker icon
     * @param id - ID of marker icon (must be unique among marker icons - letters, numbers, periods, underscores only)
     * @param label - label for marker icon
     * @param marker_png - stream containing PNG encoded icon for marker (will be read and copied)
     * @return marker icon object, or null if failed
     */
    public MarkerIcon createMarkerIcon(String id, String label, InputStream marker_png);
    /**
     * Get set of player sets defined
     */
    public Set<PlayerSet> getPlayerSets();
    /**
     * Get player set by ID
     * @param id - player set ID
     * @return set, or null if not found
     */
    public PlayerSet getPlayerSet(String id);
    /**
     * Create a new player set
     * @param id - ID of player set (must be unique among player sets - letters, numbers, periods, underscores only)
     * @param symmetric - is symmetric acccess (players in set can see other players in set) if true
     * @param players - players in the set (ID strings)
     * @param persistent - if true, set is persistent
     * @return player set, or null if failed
     */
    public PlayerSet createPlayerSet(String id, boolean symmetric, Set<String> players, boolean persistent);
}
