package org.dynmap.hdmap;

import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.MapManager;
import org.dynmap.utils.BlockStep;
import org.dynmap.utils.LightLevels;

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
    
    private void applySmoothLighting(HDPerspectiveState ps, Color incolor, Color[] outcolor, int[] shadowscale) {
        int[] xyz = ps.getSubblockCoord();
        int scale = (int)ps.getScale();
        int mid = scale / 2;
        BlockStep s1, s2;
        int w1, w2;
        /* Figure out which two neighbor directions to sample */
        switch(ps.getLastBlockStep()) {
        case X_MINUS:
        case X_PLUS:
            s1 = (xyz[1] < mid) ? BlockStep.Y_MINUS : BlockStep.Y_PLUS;
            w1 = Math.abs(xyz[1] - mid);
            s2 = (xyz[2] < mid) ? BlockStep.Z_MINUS : BlockStep.Z_PLUS;
            w2 = Math.abs(xyz[2] - mid);
            break;
        case Z_MINUS:
        case Z_PLUS:
            s1 = (xyz[0] < mid) ? BlockStep.X_MINUS : BlockStep.X_PLUS;
            w1 = Math.abs(xyz[0] - mid);
            s2 = (xyz[1] < mid) ? BlockStep.Y_MINUS : BlockStep.Y_PLUS;
            w2 = Math.abs(xyz[1] - mid);
            break;
        default:
            s1 = (xyz[0] < mid) ? BlockStep.X_MINUS : BlockStep.X_PLUS;
            w1 = Math.abs(xyz[0] - mid);
            s2 = (xyz[2] < mid) ? BlockStep.Z_MINUS : BlockStep.Z_PLUS;
            w2 = Math.abs(xyz[2] - mid);
            break;
        }
        /* Fetch the 3 needed light levels (once, shared by both night and day passes) */
        LightLevels ll0 = ps.getCachedLightLevels(0);
        ps.getLightLevels(ll0);
        LightLevels ll1 = ps.getCachedLightLevels(1);
        ps.getLightLevelsAtStep(s1, ll1);
        LightLevels ll2 = ps.getCachedLightLevels(2);
        ps.getLightLevelsAtStep(s2, ll2);

        /* Night (ambient) pass */
        applySmoothedLightToColor(outcolor[0], incolor, ll0, ll1, ll2, true, w1, w2, scale, shadowscale);
        /* Day pass (only when night/day rendering is active) */
        if(outcolor.length > 1) {
            applySmoothedLightToColor(outcolor[1], incolor, ll0, ll1, ll2, false, w1, w2, scale, shadowscale);
        }
    }

    /** Apply smooth lighting to a single output color for one pass (ambient or day). */
    private void applySmoothedLightToColor(Color out, Color incolor,
            LightLevels ll0, LightLevels ll1, LightLevels ll2,
            boolean useambient, int w1, int w2, int scale, int[] shadowscale) {
        int lv0 = getLightLevel(ll0, useambient);
        int lv1 = getLightLevel(ll1, useambient);
        int weight = 0;
        if(lv1 < lv0) weight -= w1;
        else if(lv1 > lv0) weight += w1;
        int lv2 = getLightLevel(ll2, useambient);
        if(lv2 < lv0) weight -= w2;
        else if(lv2 > lv0) weight += w2;
        out.setColor(incolor);
        int cscale = computeSmoothedCscale(lv0, weight, scale, shadowscale);
        if(cscale < 256) {
            out.scaleRGB(cscale);
        }
    }

    /** Interpolate the shadow scale for a light level, blending toward the adjacent level by weight/scale. */
    private int computeSmoothedCscale(int ll0, int weight, int scale, int[] shadowscale) {
        if(weight == 0) {
            return shadowscale[ll0];
        }
        if(weight < 0) {
            weight = -weight;
            return (ll0 > 0)
                ? (shadowscale[ll0] * (scale - weight) + shadowscale[ll0 - 1] * weight) / scale
                : shadowscale[ll0];
        }
        return (ll0 < 15)
            ? (shadowscale[ll0] * (scale - weight) + shadowscale[ll0 + 1] * weight) / scale
            : shadowscale[ll0];
    }

    private int getLightLevel(final LightLevels ll, boolean useambient) {
        int lightlevel = useambient ? lightscale[ll.sky] : ll.sky;
        if(lightlevel < 15) {
            lightlevel = Math.max(ll.emitted, lightlevel);
        }
        return lightlevel;
    }

    /* Apply lighting to given pixel colors (1 outcolor if normal, 2 if night/day) */
    @Override
    public void applyLighting(HDPerspectiveState ps, HDShaderState ss, Color incolor, Color[] outcolor) {
        int[] shadowscale = ss.getLightingTable();
        if(shadowscale == null) {
            shadowscale = defLightingTable;
        }
        if(smooth && ps.getShade()) {
            applySmoothLighting(ps, incolor, outcolor, shadowscale);
            checkGrayscale(outcolor);
            return;
        }
        /* Non-smooth: fetch light levels and apply flat shadow */
        LightLevels ll = ps.getCachedLightLevels(0);
        ps.getLightLevels(ll);
        int lightlevel = lightscale[ll.sky];   // apply ambient scale immediately
        int lightlevel_day = ll.sky;
        if((lightlevel < 15) || (lightlevel_day < 15)) {
            int emitted = ll.emitted;
            lightlevel     = Math.max(emitted, lightlevel);
            lightlevel_day = Math.max(emitted, lightlevel_day);
        }
        outcolor[0].setColor(incolor);
        if(lightlevel < 15) {
            int s = shadowscale[lightlevel];
            if(s < 256) outcolor[0].scaleRGB(s);
        }
        if(outcolor.length > 1) {
            if(lightlevel_day == lightlevel) {
                outcolor[1].setColor(outcolor[0]);
            }
            else {
                outcolor[1].setColor(incolor);
                if(lightlevel_day < 15) {
                    int s = shadowscale[lightlevel_day];
                    if(s < 256) outcolor[1].scaleRGB(s);
                }
            }
        }
        checkGrayscale(outcolor);
    }


    /* Test if night/day is enabled for this renderer */
    @Override
    public boolean isNightAndDayEnabled() { return night_and_day; }
    
    /* Test if sky light level needed */
    @Override
    public boolean isSkyLightLevelNeeded() { return true; }
    
    /* Test if emitted light level needed */
    @Override
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
