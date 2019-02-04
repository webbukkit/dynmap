# What is Dynmap?

Dynmap is a **Google Maps-like map for your Minecraft Java Edition Server that can be viewed in any browser**. Easy to set up when making use of Dynmap's integrated webserver which works out-of-the-box, while also available to be integrated into existing websites running on Apache and the like. Dynmap can render your worlds using different renderers, some suitable for performance, some for high detail.

Components allow you to add/remove functionality to make Dynmap suit your needs. Using the components Dynmap comes supplied with, there is support for chat balloons, web-to-game chat, and configurable markers, areas, and lines.

# Features:

 - Highly configurable maps per world
 - Real-time updates: maps are kept in sync with your world in real-time, updates are shown while your leave your browser open
 - Players with their faces are visible on the map
 - Chat messages are visible (as balloons or in a chatbox) on the map.
 - Map viewers can chat to players in-game.
 - Current Minecraft time is visible on the map.
 - Current Minecraft weather is visible on the map.
 - WorldGuard, Residence, Towny and Factions regions that can be visible on the map (through corresponding Dynmap-* plugins)
 - Overall highly configurable and customizable.

# Installation:
Copy dynmap-*.jar into your plugins directory. If you are upgrading, delete the previous dynmap-*.jar - you do NOT need to delete the plugins/dynmap directory or its contents.

If you are running a separate webserver (like Apache) you may need to copy the files from 'plugins/dynmap/web/' to a directory in your http-root and follow this guide. When upgrading, make sure you also upgrade the copied files.

# First time use:

When you start CraftBukkit, you should be able to navigate to [http://yourserverip:8123/](http://yourserverip:8123/) in your browser. In case you are running CraftBukkit on the PC you are currently working on, you can navigate to http://localhost:8123/. You should be able to see the players who are in-game. Note that the map is not yet rendered, therefore the background will be black.

If you are planning on using the HD renderer, now would be a good time to do so. Enable 'deftemplatesuffix: hires' in the top of configuration.txt. More information about deftemplatesuffix is available at Base plugin settings.

If you just want to see Dynmap work, use the following command in-game: **/dynmap fullrender**. The wiki contains more information about commands and permissions. The map should reveal itself gradually in the browser, give it some time. Progress messages indicate that Dynmap is working and will show when the render is completed.
