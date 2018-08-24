package org.dynmap.hdmap.colormult;

import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.MapDataContext;

/**
 * Twilight Forest special leaf color multiplier
 */
public class TFSpecialLeafColorMultiplier extends CustomColorMultiplier {
    public TFSpecialLeafColorMultiplier() {
    }
    
    @Override
    public int getColorMultiplier(MapDataContext ctx) {
        int x = ctx.getX();
        int y = ctx.getY();
        int z = ctx.getZ();
        
        int r = (x * 32) + (y * 16);
        if((r & 0x100) != 0) {
            r = 0xFF - (r & 0xFF);
        }
        r &= 0xFF;
            
        int g = (y * 32) + (z * 16);
        if((g & 0x100) != 0) {
            g = 0xFF - (g & 0xFF);
        }
        g ^= 0xFF; // Probably bug in TwilightForest - needed to match
            
        int b = (x * 16) + (z * 32);
        if((b & 0x100) != 0) {
            b = 0xFF - (b & 0xFF);
        }
        b &= 0xFF;
            
        return (r << 16) | (g << 8) | b;
    }
}
