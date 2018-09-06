package org.dynmap.hdmap;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

public class TexturePackHDUnderwaterShader extends TexturePackHDShader {
    private boolean hide_land = true;
    
    class UnderwaterShaderState extends TexturePackHDShader.ShaderState {
        private boolean ready;
        private DynmapBlockState full_water;
        
        protected UnderwaterShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache, int scale) {
            super(mapiter, map, cache, scale);
            full_water = DynmapBlockState.getBaseStateByName(DynmapBlockState.WATER_BLOCK);
        }
        @Override
        public void reset(HDPerspectiveState ps) {
            super.reset(ps);
            ready = (!hide_land); // Start ready if not hiding land
        }
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
    		DynmapBlockState bs = ps.getBlockState();
    		if (bs.isWater() || bs.isWaterlogged()) {
    			ready = true;
    			this.lastblk = full_water;
    			this.lastblkhit = full_water;
    		}
            return ready ? super.processBlock(ps) : false;
        }
    }
    public TexturePackHDUnderwaterShader(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        hide_land = configuration.getBoolean("hide-land", true);
    }
    @Override
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter, int scale) {
        return new UnderwaterShaderState(mapiter, map, cache, scale);
    }
}
