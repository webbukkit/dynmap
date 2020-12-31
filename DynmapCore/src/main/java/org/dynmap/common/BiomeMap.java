package org.dynmap.common;

import org.dynmap.hdmap.HDBlockModels;

/* Generic biome mapping */
public class BiomeMap {
    private static BiomeMap[] biome_by_index = new BiomeMap[1025];
    public static final BiomeMap NULL = new BiomeMap(-1, "NULL", 0.5, 0.5, 0xFFFFFF, 0, 0);

    public static final BiomeMap OCEAN = new BiomeMap(0, "OCEAN");
    public static final BiomeMap PLAINS = new BiomeMap(1, "PLAINS", 0.8, 0.4);
    public static final BiomeMap DESERT = new BiomeMap(2, "DESERT", 2.0, 0.0);
    public static final BiomeMap EXTREME_HILLS = new BiomeMap(3, "EXTREME_HILLS", 0.2, 0.3);
    public static final BiomeMap FOREST = new BiomeMap(4, "FOREST", 0.7, 0.8);
    public static final BiomeMap TAIGA = new BiomeMap(5, "TAIGA", 0.05, 0.8);
    public static final BiomeMap SWAMPLAND = new BiomeMap(6, "SWAMPLAND", 0.8, 0.9, 0xE0FFAE, 0x4E0E4E, 0x4E0E4E);
    public static final BiomeMap RIVER = new BiomeMap(7, "RIVER");
    public static final BiomeMap HELL = new BiomeMap(8, "HELL", 2.0, 0.0);
    public static final BiomeMap SKY = new BiomeMap(9, "SKY");
    public static final BiomeMap FROZEN_OCEAN = new BiomeMap(10, "FROZEN_OCEAN", 0.0, 0.5);
    public static final BiomeMap FROZEN_RIVER = new BiomeMap(11, "FROZEN_RIVER", 0.0, 0.5);
    public static final BiomeMap ICE_PLAINS = new BiomeMap(12, "ICE_PLAINS", 0.0, 0.5);
    public static final BiomeMap ICE_MOUNTAINS = new BiomeMap(13, "ICE_MOUNTAINS", 0.0, 0.5);
    public static final BiomeMap MUSHROOM_ISLAND = new BiomeMap(14, "MUSHROOM_ISLAND", 0.9, 1.0);
    public static final BiomeMap MUSHROOM_SHORE = new BiomeMap(15, "MUSHROOM_SHORE", 0.9, 1.0);
    public static final BiomeMap BEACH = new BiomeMap(16, "BEACH", 0.8, 0.4);
    public static final BiomeMap DESERT_HILLS = new BiomeMap(17, "DESERT_HILLS", 2.0, 0.0);
    public static final BiomeMap FOREST_HILLS = new BiomeMap(18, "FOREST_HILLS", 0.7, 0.8);
    public static final BiomeMap TAIGA_HILLS = new BiomeMap(19, "TAIGA_HILLS", 0.05, 0.8);
    public static final BiomeMap SMALL_MOUNTAINS = new BiomeMap(20, "SMALL_MOUNTAINS", 0.2, 0.8);
    public static final BiomeMap JUNGLE = new BiomeMap(21, "JUNGLE", 1.2, 0.9);
    public static final BiomeMap JUNGLE_HILLS = new BiomeMap(22, "JUNGLE_HILLS", 1.2, 0.9);

    public static final int LAST_WELL_KNOWN = 22;
    
    private double tmp;
    private double rain;
    private int watercolormult;
    private int grassmult;
    private int foliagemult;
    private final String id;
    private final int index;
    private int biomeindex256; // Standard biome mapping index (for 256 x 256)
    private boolean isDef;
    
    private static boolean loadDone = false;
    
    public static void loadWellKnownByVersion(String mcver) {
        if (loadDone) return;
        if (HDBlockModels.checkVersionRange(mcver, "1.7.0-")) {
            new BiomeMap(23, "JUNGLE_EDGE", 0.95, 0.8);
            new BiomeMap(24, "DEEP_OCEAN");
            new BiomeMap(25, "STONE_BEACH", 0.2, 0.3);
            new BiomeMap(26, "COLD_BEACH", 0.05, 0.3);
            new BiomeMap(27, "BIRCH_FOREST", 0.6, 0.6);
            new BiomeMap(28, "BIRCH_FOREST_HILLS", 0.6, 0.6);
            new BiomeMap(29, "ROOFED_FOREST", 0.7, 0.8);
            new BiomeMap(30, "COLD_TAIGA", -0.5, 0.4);
            new BiomeMap(31, "COLD_TAIGA_HILLS", -0.5, 0.4);
            new BiomeMap(32, "MEGA_TAIGA", 0.3, 0.8);
            new BiomeMap(33, "MEGA_TAIGA_HILLS", 0.3, 0.8);
            new BiomeMap(34, "EXTREME_HILLS_PLUS", 0.2, 0.3);
            new BiomeMap(35, "SAVANNA", 1.2, 0.0);
            new BiomeMap(36, "SAVANNA_PLATEAU", 1.0, 0.0);
            new BiomeMap(37, "MESA", 2.0, 0.0);
            new BiomeMap(38, "MESA_PLATEAU_FOREST", 2.0, 0.0);
            new BiomeMap(39, "MESA_PLATEAU", 2.0, 0.0);
            new BiomeMap(129, "SUNFLOWER_PLAINS", 0.8, 0.4);
            new BiomeMap(130, "DESERT_MOUNTAINS", 2.0, 0.0);
            new BiomeMap(131, "EXTREME_HILLS_MOUNTAINS", 0.2, 0.3);
            new BiomeMap(132, "FLOWER_FOREST", 0.7, 0.8);
            new BiomeMap(133, "TAIGA_MOUNTAINS", 0.05, 0.8);
            new BiomeMap(134, "SWAMPLAND_MOUNTAINS", 0.8, 0.9, 0xE0FFAE, 0x4E0E4E, 0x4E0E4E);
            new BiomeMap(140, "ICE_PLAINS_SPIKES", 0.0, 0.5);
            new BiomeMap(149, "JUNGLE_MOUNTAINS", 1.2, 0.9);
            new BiomeMap(151, "JUNGLE_EDGE_MOUNTAINS", 0.95, 0.8);
            new BiomeMap(155, "BIRCH_FOREST_MOUNTAINS", 0.6, 0.6);
            new BiomeMap(156, "BIRCH_FOREST_HILLS_MOUNTAINS", 0.6, 0.6);
            new BiomeMap(157, "ROOFED_FOREST_MOUNTAINS", 0.7, 0.8);
            new BiomeMap(158, "COLD_TAIGA_MOUNTAINS", -0.5, 0.4);
            new BiomeMap(160, "MEGA_SPRUCE_TAIGA", 0.25, 0.8);
            new BiomeMap(161, "MEGA_SPRUCE_TAIGA_HILLS", 0.3, 0.8);
            new BiomeMap(162, "EXTREME_HILLS_PLUS_MOUNTAINS", 0.2, 0.3);
            new BiomeMap(163, "SAVANNA_MOUNTAINS", 1.2, 0.0);
            new BiomeMap(164, "SAVANNA_PLATEAU_MOUNTAINS", 1.0, 0.0);
            new BiomeMap(165, "MESA_BRYCE", 2.0, 0.0);
            new BiomeMap(166, "MESA_PLATEAU_FOREST_MOUNTAINS", 2.0, 0.0);
            new BiomeMap(167, "MESA_PLATEAU_MOUNTAINS", 2.0, 0.0);
        }
        if (HDBlockModels.checkVersionRange(mcver, "1.9.0-")) {
            new BiomeMap(127, "THE_VOID");
        }
        if (HDBlockModels.checkVersionRange(mcver, "1.13.0-")) {
            new BiomeMap(40, "SMALL_END_ISLANDS");
            new BiomeMap(41, "END_MIDLANDS");
            new BiomeMap(42, "END_HIGHLANDS");
            new BiomeMap(43, "END_BARRENS");
            new BiomeMap(44, "WARM_OCEAN");
            new BiomeMap(45, "LUKEWARM_OCEAN");
            new BiomeMap(46, "COLD_OCEAN");
            new BiomeMap(47, "DEEP_WARM_OCEAN");
            new BiomeMap(48, "DEEP_LUKEWARM_OCEAN");
            new BiomeMap(49, "DEEP_COLD_OCEAN");
            new BiomeMap(50, "DEEP_FROZEN_OCEAN");
        }
        if (HDBlockModels.checkVersionRange(mcver, "1.14.0-")) {
            new BiomeMap(168, "BAMBOO_JUNGLE");
            new BiomeMap(169, "BAMBOO_JUNGLE_HILLS");
        }
        if (HDBlockModels.checkVersionRange(mcver, "1.16.0-")) {
            new BiomeMap(170, "SOUL_SAND_VALLEY");
            new BiomeMap(171, "CRIMSON_FOREST");
            new BiomeMap(172, "WARPED_FOREST");
            new BiomeMap(173, "BASALT_DELTAS");
        }
        loadDone = true;
    }
    
    static {
        for (int i = 0; i < 1024; i++) {
            BiomeMap bm = BiomeMap.byBiomeID(i);
            if (bm == null) {
                bm = new BiomeMap(i, "BIOME_" + i);
                bm.isDef = true;
            }
        }
    }

    private static boolean isUniqueID(String id) {
        for(int i = 0; i < biome_by_index.length; i++) {
            if(biome_by_index[i] == null) continue;
            if(biome_by_index[i].id.equals(id))
                return false;
        }
        return true;
    }
    private BiomeMap(int idx, String id, double tmp, double rain, int waterColorMultiplier, int grassmult, int foliagemult) {
        /* Clamp values : we use raw values from MC code, which are clamped during color mapping only */
        setTemperature(tmp);
        setRainfall(rain);
        this.watercolormult = waterColorMultiplier;
        this.grassmult = grassmult;
        this.foliagemult = foliagemult;
        // Handle null biome
        if (id == null) { id = "biome_" + idx; }
        id = id.toUpperCase().replace(' ', '_');
        if(isUniqueID(id) == false) {
            id = id + "_" + idx;
        }
        this.id = id;
        idx++;  /* Insert one after ID value - null is zero index */
        this.index = idx;
        if(idx >= 0) {
            biome_by_index[idx] = this;
        }
    }
    public BiomeMap(int idx, String id) {
        this(idx, id, 0.5, 0.5, 0xFFFFFF, 0, 0);
    }
    
    public BiomeMap(int idx, String id, double tmp, double rain) {
        this(idx, id, tmp, rain, 0xFFFFFF, 0, 0);
    }
    
    private final int biomeLookup(int width) {
        int w = width-1;
        int t = (int)((1.0-tmp)*w);
        int h = (int)((1.0 - (tmp*rain))*w);
        return width*h + t;
    }

    public final int biomeLookup() {
        return this.biomeindex256;
    }
    
    public final int getModifiedGrassMultiplier(int rawgrassmult) {
        if(grassmult == 0)
            return rawgrassmult;
        else if(grassmult > 0xFFFFFF)
            return grassmult & 0xFFFFFF;
        else
            return ((rawgrassmult & 0xfefefe) + grassmult) / 2;
    }
    
    public final int getModifiedFoliageMultiplier(int rawfoliagemult) {
        if(foliagemult == 0)
            return rawfoliagemult;
        else if(foliagemult > 0xFFFFFF)
            return foliagemult & 0xFFFFFF;
        else
            return ((rawfoliagemult & 0xfefefe) + foliagemult) / 2;
    }
    public final int getWaterColorMult() {
        return watercolormult;
    }
    public final int ordinal() {
        return index;
    }
    public static final BiomeMap byBiomeID(int idx) {
        idx++;
        if((idx >= 0) && (idx < biome_by_index.length))
            return biome_by_index[idx];
        else
            return NULL;
    }
    public int getBiomeID() {
        return index - 1;   // Index of biome in MC biome table
    }
    public final String toString() {
        return id;
    }
    public static final BiomeMap[] values() {
        return biome_by_index;
    }
    public void setWaterColorMultiplier(int watercolormult) {
        this.watercolormult = watercolormult;
    }
    public void setGrassColorMultiplier(int grassmult) {
        this.grassmult = grassmult;
    }
    public void setFoliageColorMultiplier(int foliagemult) {
        this.foliagemult = foliagemult;
    }
    public void setTemperature(double tmp) {
        if(tmp < 0.0) tmp = 0.0;
        if(tmp > 1.0) tmp = 1.0;
        this.tmp = tmp;
        this.biomeindex256 = this.biomeLookup(256);
    }
    public void setRainfall(double rain) {
        if(rain < 0.0) rain = 0.0;
        if(rain > 1.0) rain = 1.0;
        this.rain = rain;
        this.biomeindex256 = this.biomeLookup(256);
    }
    public final double getTemperature() {
        return this.tmp;
    }
    public final double getRainfall() {
        return this.rain;
    }
    public boolean isDefault() {
        return isDef;
    }
}
