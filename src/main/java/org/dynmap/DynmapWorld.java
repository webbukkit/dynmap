package org.dynmap;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;
import org.dynmap.MapType;

public class DynmapWorld {
    public World world;
    public List<MapType> maps = new ArrayList<MapType>();
    public UpdateQueue updates = new UpdateQueue();
}
