package org.dynmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.event.block.*;

public class DynmapBlockListener extends BlockListener {
	private static final Logger log = Logger.getLogger("Minecraft");
	private MapManager mgr;
	
	public DynmapBlockListener(MapManager mgr) {
		this.mgr = mgr;
	}

	@Override
	public void onBlockPlaced(BlockPlacedEvent event) {
		Block blockPlaced = event.getBlock();
		if(mgr.touch(blockPlaced.getX(), blockPlaced.getY(), blockPlaced.getZ()))
			mgr.debug(/*player.getName() + */" touch " + blockPlaced.getX() + "," + blockPlaced.getY() + "," + blockPlaced.getZ() + " from onBlockCreate");
	}
}
