package org.dynmap.hdmap.colormult;

import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.MapDataContext;

/**
 * Twilight Forest banded wood color multiplier
 */
public class TFBandedWoodColorMultiplier extends CustomColorMultiplier {
    public TFBandedWoodColorMultiplier() {
    }
    
    @Override
    public int getColorMultiplier(MapDataContext ctx) {
        int x = ctx.getX();
        int y = ctx.getY();
        int z = ctx.getZ();
        
        int value = x * 31 + y * 15 + z * 33;
        if ((value & 0x100) != 0) {
            value = 255 - (value & 0xFF);
        }
        value &= 255;
        value >>= 1;
        value |= 128;
        
        return (value << 16) | (value << 8) | value;
    }
}
