package org.dynmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.bukkit.Location;

public class DynmapWorld {
    public World world;
    public List<MapType> maps = new ArrayList<MapType>();
    public UpdateQueue updates = new UpdateQueue();
    public ConfigurationNode configuration;
    public List<Location> seedloc;
    public int servertime;
    public boolean sendposition;
    public boolean sendhealth;
    public boolean bigworld;    /* If true, deeper directory hierarchy */
}
