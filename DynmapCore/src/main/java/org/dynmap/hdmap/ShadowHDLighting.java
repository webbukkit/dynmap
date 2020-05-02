package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapManager;
import org.dynmap.utils.LightLevels;
import org.dynmap.utils.BlockStep;

public class ShadowHDLighting extends DefaultHDLighting {

    protected final int   defLightingTable[];  /* index=skylight level, value = 256 * scaling value */
    protected final int   lightscale[];   /* scale skylight level (light = lightscale[skylight] */
    protected final boolean night_and_day;    /* If true, render both day (prefix+'-day') and night (prefix) tiles */
    protected final boolean smooth;
    protected final boolean useWorldBrightnessTable;
    
    public ShadowHDLighting(DynmapCore core, ConfigurationNode configuration) {
        super(core, configuration);
        double shadowweight = configuration.getDouble("shadowstrength", 0.0);
        // See if we're using world's lighting table, or our own
        useWorldBrightnessTable = configuration.getBoolean("use-brightness-table", MapManager.mapman.useBrightnessTable());

        defLightingTable = new int[16];
        defLightingTable[15] = 256;
        /* Normal brightness weight in MC is a 20% relative dropoff per step */
        for(int i = 14; i >= 0; i--) {
            double v = defLightingTable[i+1] * (1.0 - (0.2 * shadowweight));
            defLightingTable[i] = (int)v;
            if(defLightingTable[i] > 256) defLightingTable[i] = 256;
            if(defLightingTable[i] < 0) defLightingTable[i] = 0;
        }
        int v = configuration.getInteger("ambientlight", -1);
        if(v < 0) v = 15;
        if(v > 15) v = 15;
        night_and_day = configuration.getBoolean("night-and-day", false);
        lightscale = new int[16];
        for(int i = 0; i < 16; i++) {
            if(i < (15-v))
                lightscale[i] = 0;
            else
                lightscale[i] = i - (15-v);
        }
        smooth = configuration.getBoolean("smooth-lighting", MapManager.mapman.getSmoothLighting());
    }
    
    private void    applySmoothLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor, int[] shadowscale) {
        int[] xyz = ps.getSubblockCoord();
        int scale = (int)ps.getScale();
        int mid = scale/2;
        BlockStep s1, s2;
        int w1, w2;
        /* Figure out which directions to look */
        switch(ps.getLastBlockStep()) {
        case X_MINUS:
        case X_PLUS:
            if(xyz[1] < mid) {
                s1 = BlockStep.Y_MINUS;
                w1 = mid - xyz[1];
            }
            else {
                s1 = BlockStep.Y_PLUS;
                w1 = xyz[1] - mid;
            }
            if(xyz[2] < mid) {
                s2 = BlockStep.Z_MINUS;
                w2 = mid - xyz[2];
            }
            else {
                s2 = BlockStep.Z_PLUS;
                w2 = xyz[2] - mid;
            }
            break;
        case Z_MINUS:
        case Z_PLUS:
            if(xyz[0] < mid) {
                s1 = BlockStep.X_MINUS;
                w1 = mid - xyz[0];
            }
            else {
                s1 = BlockStep.X_PLUS;
                w1 = xyz[0] - mid;
            }
            if(xyz[1] < mid) {
                s2 = BlockStep.Y_MINUS;
                w2 = mid - xyz[1];
            }
            else {
                s2 = BlockStep.Y_PLUS;
                w2 = xyz[1] - mid;
            }
            break;
        default:
            if(xyz[0] < mid) {
                s1 = BlockStep.X_MINUS;
                w1 = mid - xyz[0];
            }
            else {
                s1 = BlockStep.X_PLUS;
                w1 = xyz[0] - mid;
            }
            if(xyz[2] < mid) {
                s2 = BlockStep.Z_MINUS;
                w2 = mid - xyz[2];
            }
            else {
                s2 = BlockStep.Z_PLUS;
                w2 = xyz[2] - mid;
            }
            break;
        }
        /* Now get the 3 needed light levels */
        LightLevels skyemit0 = ps.getCachedLightLevels(0);
        ps.getLightLevels(skyemit0);
        LightLevels skyemit1 = ps.getCachedLightLevels(1);
        ps.getLightLevelsAtStep(s1, skyemit1);
        LightLevels skyemit2 = ps.getCachedLightLevels(2);
        ps.getLightLevelsAtStep(s2, skyemit2);

        /* Get light levels */
        int ll0 = getLightLevel(skyemit0, true);
        int ll1 = getLightLevel(skyemit1, true);
        int weight = 0;
        if(ll1 < ll0)
            weight -= w1;
        else if(ll1 > ll0)
            weight += w1;
        int ll2 = getLightLevel(skyemit2, true);
        if(ll2 < ll0)
            weight -= w2;
        else if(ll2 > ll0)
            weight += w2;
        outcolor[0].setColor(incolor);
        int cscale = 256;
        if(weight == 0) {
            cscale = shadowscale[ll0];
        }
        else if(weight < 0) {   /* If negative, interpolate down */
            weight = -weight;
            if(ll0 > 0) {
                cscale = (shadowscale[ll0] * (scale-weight) + shadowscale[ll0-1] * weight)/scale;
            }
            else {
                cscale = shadowscale[ll0];
            }
        }
        else {
            if(ll0 < 15) {
                cscale = (shadowscale[ll0] * (scale-weight) + shadowscale[ll0+1] * weight)/scale;
            }
            else {
                cscale = shadowscale[ll0];
            }
        }
        if(cscale < 256) {
            Color c = outcolor[0];
            c.setRGBA((c.getRed() * cscale) >> 8, (c.getGreen() * cscale) >> 8, 
                (c.getBlue() * cscale) >> 8, c.getAlpha());
        }
        if(outcolor.length > 1) {
            ll0 = getLightLevel(skyemit0, false);
            ll1 = getLightLevel(skyemit1, false);
            weight = 0;
            if(ll1 < ll0)
                weight -= w1;
            else if(ll1 > ll0)
                weight += w1;
            ll2 = getLightLevel(skyemit2, false);
            if(ll2 < ll0)
                weight -= w2;
            else if(ll2 > ll0)
                weight += w2;
            outcolor[1].setColor(incolor);
            cscale = 256;
            if(weight == 0) {
                cscale = shadowscale[ll0];
            }
            else if(weight < 0) {   /* If negative, interpolate down */
                weight = -weight;
                if(ll0 > 0) {
                    cscale = (shadowscale[ll0] * (scale-weight) + shadowscale[ll0-1] * weight)/scale;
                }
                else {
                    cscale = shadowscale[ll0];
                }
            }
            else {
                if(ll0 < 15) {
                    cscale = (shadowscale[ll0] * (scale-weight) + shadowscale[ll0+1] * weight)/scale;
                }
                else {
                    cscale = shadowscale[ll0];
                }
            }
            if(cscale < 256) {
                Color c = outcolor[1];
                c.setRGBA((c.getRed() * cscale) >> 8, (c.getGreen() * cscale) >> 8, 
                    (c.getBlue() * cscale) >> 8, c.getAlpha());
            }
        }
    }
    
    private final int getLightLevel(final LightLevels ll, boolean useambient) {
        int lightlevel;
        /* If ambient light, adjust base lighting for it */
        if(useambient)
            lightlevel = lightscale[ll.sky];
        else
            lightlevel = ll.sky;
        /* If we're below max, see if emitted light helps */
        if(lightlevel < 15) {
            lightlevel = Math.max(ll.emitted, lightlevel);                                
        }
        return lightlevel;
    }
        
    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    public void    applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        int[] shadowscale = null;
        if(smooth) {
            shadowscale = ss.getLightingTable();
            if (shadowscale == null) {
                shadowscale = defLightingTable;
            }
            applySmoothLighting(ps, ss, incolor, outcolor, shadowscale);
            checkGrayscale(outcolor);
            return;
        }
        LightLevels ll = null;
        int lightlevel = 15, lightlevel_day = 15;
        /* If processing for shadows, use sky light level as base lighting */
        if(defLightingTable != null) {
            shadowscale = ss.getLightingTable();
            if (shadowscale == null) {
                shadowscale = defLightingTable;
            }
            ll = ps.getCachedLightLevels(0);
            ps.getLightLevels(ll);
            lightlevel = lightlevel_day = ll.sky;
        }
        /* If ambient light, adjust base lighting for it */
        lightlevel = lightscale[lightlevel];
        /* If we're below max, see if emitted light helps */
        if((lightlevel < 15) || (lightlevel_day < 15)) {
            int emitted = ll.emitted;
            lightlevel = Math.max(emitted, lightlevel);                                
            lightlevel_day = Math.max(emitted, lightlevel_day);                                
        }
        /* Figure out our color, with lighting if needed */
        outcolor[0].setColor(incolor);
        if(lightlevel < 15) {
            shadowColor(outcolor[0], lightlevel, shadowscale);
        }
        if(outcolor.length > 1) {
            if(lightlevel_day == lightlevel) {
                outcolor[1].setColor(outcolor[0]);
            }
            else {
                outcolor[1].setColor(incolor);
                if(lightlevel_day < 15) {
                    shadowColor(outcolor[1], lightlevel_day, shadowscale);
                }
            }
        }
        checkGrayscale(outcolor);
    }

    private final void shadowColor(Color c, int lightlevel, int[] shadowscale) {
        int scale = shadowscale[lightlevel];
        if(scale < 256)
            c.setRGBA((c.getRed() * scale) >> 8, (c.getGreen() * scale) >> 8, 
                (c.getBlue() * scale) >> 8, c.getAlpha());
    }


    /* Test if night/day is enabled for this renderer */
    public boolean isNightAndDayEnabled() { return night_and_day; }
    
    /* Test if sky light level needed */
    public boolean isSkyLightLevelNeeded() { return true; }
    
    /* Test if emitted light level needed */
    public boolean isEmittedLightLevelNeeded() { return true; }    

    @Override
    public int[] getBrightnessTable(DynmapWorld world) {
        if (useWorldBrightnessTable) {
            return world.getBrightnessTable();
        }
        else {
            return null;
        }
    }
}
