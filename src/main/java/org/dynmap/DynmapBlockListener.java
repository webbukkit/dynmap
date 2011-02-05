package org.dynmap;

import org.bukkit.block.Block;
import org.bukkit.block.BlockDamageLevel;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;

public class DynmapBlockListener extends BlockListener {
    private MapManager mgr;

    public DynmapBlockListener(MapManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        Block blockPlaced = event.getBlockPlaced();
        mgr.touch(blockPlaced.getX(), blockPlaced.getY(), blockPlaced.getZ());
    }

    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getDamageLevel() == BlockDamageLevel.BROKEN) {
            Block blockBroken = event.getBlock();
            mgr.touch(blockBroken.getX(), blockBroken.getY(), blockBroken.getZ());
        }
    }
}
