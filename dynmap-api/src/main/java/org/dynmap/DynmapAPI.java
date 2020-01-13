package org.dynmap;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * This is the interface representing the published API for the Dynmap plugin for Bukkit.  Public methods of the
 * DynmapPlugin class that are not defined in this interface are subject to change without notice, so
 * be careful with forming dependencies beyond these.  Plugins accessing dynmap 0.24 or later should
 * do so by casting the Plugin to this interface.
 * 
 * This interface is Bukkit specific.
 */
public interface DynmapAPI extends DynmapCommonAPI {
    /**
     * Trigger update on tiles associated with given locations.  If two locations provided,
     * the volume is the rectangular prism ("cuboid") with the two locations on opposite corners.
     * 
     * @param l0 - first location (required)
     * @param l1 - second location (if null, only single point invalidated (l0))
     * @return number of tiles queued to be rerendered  (@deprecated return value - just returns 1)
     */
    public int triggerRenderOfVolume(Location l0, Location l1);
    /**
     * Set player visibility
     * @param player - player
     * @param is_visible - true if visible, false if hidden
     */
    public void setPlayerVisiblity(Player player, boolean is_visible);
    /**
     * Test if player is visible
     * @return true if visible, false if not
     */
    public boolean getPlayerVisbility(Player player);
    /**
     * Post message from player to web
     * @param player - player
     * @param message - message text
     */
    public void postPlayerMessageToWeb(Player player, String message);
    /**
     * Post join/quit message for player to web
     * @param player - player
     * @param isjoin - if true, join message; if false, quit message
     */
    public void postPlayerJoinQuitToWeb(Player player, boolean isjoin);
    /**
     * Get version of dynmap plugin
     * @return version - format is "major.minor-build" or "major.minor.patch-build"
     */
    public String getDynmapVersion();
    /**
     * Set player visibility (transient - if player is configured to be visible, they are hidden if one or more plugins assert their invisiblity)
     * @param player - player ID
     * @param is_invisible - true if asserting player should be invisible, false if no assertion
     * @param plugin - asserting plugin
     */
    public void assertPlayerInvisibility(Player player, boolean is_invisible, Plugin plugin);
    /**
     * Set player visibility (transient - if player is configured to be hidden, they are made visibile if one or more plugins assert their visibility))
     * @param player - player
     * @param is_visible - true if asserting that hidden player should be visible, false if no assertion
     * @param plugin - asserting plugin
     */
    public void assertPlayerVisibility(Player player, boolean is_visible, Plugin plugin);
}
