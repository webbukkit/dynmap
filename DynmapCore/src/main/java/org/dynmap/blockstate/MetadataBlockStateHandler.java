package org.dynmap.blockstate;

import org.dynmap.renderer.MapDataContext;

public class MetadataBlockStateHandler implements IBlockStateHandler {
    @Override
    public int getBlockStateCount() {
        return 16;  // Always 16 for metadata
    }
    @Override
    public int getBlockStateIndex(MapDataContext mdc) {
        return mdc.getBlockType().stateIndex;
    }
}
