package org.dynmap.hdmap.colormult;

import org.dynmap.renderer.CustomColorMultiplier;
import org.dynmap.renderer.MapDataContext;

/**
 * Twilight Forest magic leaf color multiplier
 */
public class TFMagicLeafColorMultiplier extends CustomColorMultiplier {
    public TFMagicLeafColorMultiplier() {
    }
    
    @Override
    public int getColorMultiplier(MapDataContext ctx) {
        int x = ctx.getX();
        int y = ctx.getY();
        int z = ctx.getZ();

        int fade = x * 16 + y * 16 + z * 16;
        if ((fade & 0x100) != 0) {
            fade = 255 - (fade & 0xFF);
        }
        fade &= 255;
        float spring = (255 - fade) / 255.0F;
        float fall = fade / 255.0F;
        int red = (int)(spring * 106.0F + fall * 251.0F);
        int green = (int)(spring * 156.0F + fall * 108.0F);
        int blue = (int)(spring * 23.0F + fall * 27.0F);
        
        return (red << 16) | (green << 8) | blue;
    }
}
