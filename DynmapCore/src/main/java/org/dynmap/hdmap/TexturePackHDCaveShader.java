package org.dynmap.hdmap;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.MapChunkCache;
import org.dynmap.utils.MapIterator;

public class TexturePackHDCaveShader extends TexturePackHDShader {
    private int maxskylevel;
    private int minemittedlevel;
    
    class CaveShaderState extends TexturePackHDShader.ShaderState {
        private boolean ready;
        private LightLevels ll = new LightLevels();
        
        protected CaveShaderState(MapIterator mapiter, HDMap map, MapChunkCache cache, int scale) {
            super(mapiter, map, cache, scale);
        }
        @Override
        public void reset(HDPerspectiveState ps) {
            super.reset(ps);
            ready = false;
        }
        /**
         * Process next ray step - called for each block on route
         * @return true if ray is done, false if ray needs to continue
         */
        public boolean processBlock(HDPerspectiveState ps) {
            if(ready)
                return super.processBlock(ps);
            if((ps.getLastBlockStep() == BlockStep.Y_MINUS) && ps.getBlockState().isAir()) {  /* In air? */
                ps.getLightLevels(ll);
                if((ll.sky <= maxskylevel) && (ll.emitted > minemittedlevel)) {
                    ready = true;
                }
            }
            return false;
        }
    }
    public TexturePackHDCaveShader(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        maxskylevel = configuration.getInteger("max-sky-light", 0);
        minemittedlevel = configuration.getInteger("min-emitted-light", 1);
    }
    @Override
    public HDShaderState getStateInstance(HDMap map, MapChunkCache cache, MapIterator mapiter, int scale) {
        return new CaveShaderState(mapiter, map, cache, scale);
    }
}
