package org.dynmap;

import org.dynmap.markers.MarkerAPI;

/**
 * This is the interface representing the published API for the Dynmap plugin, common for all Dynmap
 * implementations (not Bukkit specific).  
 */
public interface DynmapCommonAPI {    
    /**
     * This method can return null if the 'markers' component has not been configured - 
     * a warning message will be issued to the server.log in this event.
     * 
     * @return MarkerAPI, or null if not configured
     */
    public MarkerAPI getMarkerAPI();
    /**
     * Test if the marker API has been initialized yet
     *
     * @return true if it has been initialized
     */
    public boolean markerAPIInitialized();
    /**
     * Send generic message to all web users
     * @param sender - label for sender of message ("Message from &lt;plugin&gt;:") - if null, no from notice
     * @param msg - message to be sent
     */
    public boolean sendBroadcastToWeb(String sender, String msg);
    /**
     * Trigger update on tiles associated with given locations.  The volume is the rectangular prism ("cuboid") 
     * with the two locations on opposite corners, (minx, miny, minz) and (maxx, maxy, maxz).
     * 
     * @param wid - world ID
     * @param minx - minimum x of volume
     * @param miny - minimum y of volume
     * @param minz - minimum z of volume
     * @param maxx - maximum x of volume
     * @param maxy - maximum y of volume
     * @param maxz - maximum z of volume
     * 
     * @return number of tiles queued to be rerendered (@deprecated return value - just returns 0)
     */
    public int triggerRenderOfVolume(String wid, int minx, int miny, int minz, int maxx, int maxy, int maxz);
    /**
     * Trigger update on tiles associated with given block location.
     *  
     * @param wid - world ID
     * @param x - x coordinate of block
     * @param y - y coordinate of block
     * @param z - z coordinate of block
     * 
     * @return number of tiles queued to be rerendered (@deprecated return value - just returns 0)
     */
    public int triggerRenderOfBlock(String wid, int x, int y, int z);
    /*
     * Pause full/radius render processing
     * @param dopause - true to pause, false to unpause
     */
    public void setPauseFullRadiusRenders(boolean dopause);
    /*
     * Test if full renders are paused
     */
    public boolean getPauseFullRadiusRenders();
    /*
     * Pause update render processing
     * @param dopause - true to pause, false to unpause
     */
    public void setPauseUpdateRenders(boolean dopause);
    /*
     * Test if update renders are paused
     */
    public boolean getPauseUpdateRenders();
    /**
     * Set player visibility (configuration - persistent)
     * @param player - player ID
     * @param is_visible - true if visible, false if hidden
     */
    public void setPlayerVisiblity(String player, boolean is_visible);
    /**
     * Test if player is visible
     * @param player - player ID
     * 
     * @return true if visible, false if not
     */
    public boolean getPlayerVisbility(String player);
    /**
     * Set player visibility (transient - if player is configured to be visible, they are hidden if one or more plugins assert their invisiblity)
     * @param player - player ID
     * @param is_invisible - true if asserting player should be invisible, false if no assertion
     * @param plugin_id - ID of asserting plugin
     */
    public void assertPlayerInvisibility(String player, boolean is_invisible, String plugin_id);
    /**
     * Set player visibility (transient - if player is configured to be hidden, they are made visibile if one or more plugins assert their visibility))
     * @param player - player ID
     * @param is_visible - true if asserting that hidden player should be visible, false if no assertion
     * @param plugin_id - ID of asserting plugin
     */
    public void assertPlayerVisibility(String player, boolean is_visible, String plugin_id);
    /**
     * Post message from player to web
     * @param playerid - player ID
     * @param playerdisplay - player display name
     * @param message - message text
     */
    public void postPlayerMessageToWeb(String playerid, String playerdisplay, String message);
    /**
     * Post join/quit message for player to web
     * @param playerid - player ID
     * @param playerdisplay - player display name
     * @param isjoin - if true, join message; if false, quit message
     */
    public void postPlayerJoinQuitToWeb(String playerid, String playerdisplay, boolean isjoin);
    /**
     * Get version of dynmap core
     * @return version - format is "major.minor-build" or "major.minor.patch-build"
     */
    public String getDynmapCoreVersion();
    /**
     * Disable chat message processing (used by mods that will handle sending chat to the web themselves, via sendBroadcastToWeb()
     * @param disable - if true, suppress internal chat-to-web messages
     */
    public boolean setDisableChatToWebProcessing(boolean disable);
    /**
     * Test if given player can see another player on the map (based on dynmap settings, player sets, etc).
     * @param player - player attempting to observe
     * @param player_to_see - player to be observed by 'player'
     * @return true if can be seen on map, false if cannot be seen
     */
    public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see);
    /**
     * Test if player position/information is protected on map view
     * @return true if protected, false if visible to guests and all players
     */
    public boolean testIfPlayerInfoProtected();
    /**
     * Process sign change
     * @param material - block's Material enum value as a string
     * @param world - world name
     * @param x - x coord
     * @param y - y coord
     * @param z - z coord
     * @param lines - sign lines (input and output)
     * @param playerid - player ID
     */
    public void processSignChange(String material, String world, int x, int y, int z, String[] lines, String playerid);
}
