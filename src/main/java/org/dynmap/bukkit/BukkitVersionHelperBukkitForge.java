package org.dynmap.bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.Log;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperBukkitForge extends BukkitVersionHelperGeneric {
    BukkitVersionHelperBukkitForge() {
    }
    @Override
    protected String getNMSPackage() {
        return "";
    }
    @Override
    protected void loadNMS() {
        /* biomebase */
        biomebase = getNMSClass("yy");
        biomebasearray = getNMSClass("[Lyy;");
        /* world */
        nmsworld = getNMSClass("in");
        /* chunk */
        chunkprovserver = getNMSClass("im");
        nmschunk = getNMSClass("zz");
        /* nbt */
        nbttagcompound = getNMSClass("bq");
        nbttagbyte = getNMSClass("bp");
        nbttagshort = getNMSClass("cb");
        nbttagint = getNMSClass("bx");
        nbttaglong = getNMSClass("bz");
        nbttagfloat = getNMSClass("bv");
        nbttagdouble = getNMSClass("bt");
        nbttagbytearray = getNMSClass("bo");
        nbttagstring = getNMSClass("cc");
        nbttagintarray = getNMSClass("bw");
        /* tileentity */
        nms_tileentity = getNMSClass("any");
        
        /** Set up NMS fields **/
        /* biomebase */
        biomebaselist = getField(biomebase, new String[] { "a" }, biomebasearray);
        biomebasetemp = getField(biomebase, new String[] { "F" }, float.class);
        biomebasehumi = getField(biomebase, new String[] { "G" }, float.class);
        biomebaseidstring = getField(biomebase, new String[] { "y" }, String.class);
        biomebaseid = getField(biomebase, new String[] { "N" }, int.class);
        /* chunk */
        nmsw_chunkproviderserver = getField(nmsworld, new String[] { "b" }, chunkprovserver);
        cps_unloadqueue = getFieldNoFail(chunkprovserver, new String[] { "b" }, longhashset);
        if(cps_unloadqueue == null) {
            Log.info("Unload queue not found - default to unload all chunks");
        }
        nmsc_removeentities = getMethod(nmschunk, new String[] { "d" }, new Class[0]);
        nmsc_tileentities = getField(nmschunk, new String[] { "i" }, Map.class);
        /* nbt */
        compound_get = getMethod(nbttagcompound, new String[] { "a" }, new Class[] { String.class });
        nbttagbyte_val = getField(nbttagbyte, new String[] { "a" }, byte.class);
        nbttagshort_val = getField(nbttagshort, new String[] { "a" }, short.class);
        nbttagint_val = getField(nbttagint, new String[] { "a" }, int.class);
        nbttaglong_val = getField(nbttaglong, new String[] { "a" }, long.class);
        nbttagfloat_val = getField(nbttagfloat, new String[] { "a" }, float.class);
        nbttagdouble_val = getField(nbttagdouble, new String[] { "a" }, double.class);
        nbttagbytearray_val = getField(nbttagbytearray, new String[] { "a" }, byte[].class);
        nbttagstring_val = getField(nbttagstring, new String[] { "a" }, String.class);
        nbttagintarray_val = getField(nbttagintarray, new String[] { "a" }, int[].class);
        /* tileentity */
        nmst_readnbt = getMethod(nms_tileentity, new String[] { "b" }, new Class[] { nbttagcompound });
        nmst_x = getField(nms_tileentity, new String[] { "l" }, int.class);
        nmst_y = getField(nms_tileentity, new String[] { "m" }, int.class);
        nmst_z = getField(nms_tileentity, new String[] { "n" }, int.class);
    }
    
    private static long chunkXZ2Int(int par0, int par1)
    {
        return (long)par0 & 4294967295L | ((long)par1 & 4294967295L) << 32;
    }

    /* For testing unload queue for presence of givne chunk */
    @Override
    public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
        if(unloadqueue != null) {
            Set uq = (Set)unloadqueue;
            return uq.contains(Long.valueOf(chunkXZ2Int(x, z)));
        }
        return true;
    }
    @Override
    public void unloadChunkNoSave(World w, int cx, int cz) {
        w.unloadChunkRequest(cx, cz);
    }
}
