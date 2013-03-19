package org.dynmap.bukkit;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.Log;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperCB extends BukkitVersionHelperGeneric {

    BukkitVersionHelperCB() {
    }
    @Override
    protected String getNMSPackage() {
        Server srv = Bukkit.getServer();
        /* Get getHandle() method */
        try {
            Method m = srv.getClass().getMethod("getHandle");
            Object scm = m.invoke(srv); /* And use it to get SCM (nms object) */
            return scm.getClass().getPackage().getName();
        } catch (Exception x) {
            Log.severe("Error finding net.minecraft.server packages");
            return null;
        }
    }
    @Override
    protected void loadNMS() {
        /* Set up biomebase fields */
        biomebase = getNMSClass("net.minecraft.server.BiomeBase");
        biomebasearray =  getNMSClass("[Lnet.minecraft.server.BiomeBase;");
        biomebaselist = getField(biomebase, new String[] { "biomes" }, biomebasearray);
        biomebasetemp = getField(biomebase, new String[] { "temperature", "F" }, float.class);
        biomebasehumi = getField(biomebase, new String[] { "humidity", "G" }, float.class);
        biomebaseidstring = getField(biomebase, new String[] { "y" }, String.class);
        biomebaseid = getField(biomebase, new String[] { "id" }, int.class);
        /* n.m.s.World */
        nmsworld = getNMSClass("net.minecraft.server.WorldServer");
        chunkprovserver = getNMSClass("net.minecraft.server.ChunkProviderServer");
        nmsw_chunkproviderserver = getField(nmsworld, new String[] { "chunkProviderServer" }, chunkprovserver);
        
        longhashset = getOBCClassNoFail("org.bukkit.craftbukkit.util.LongHashSet");
        if(longhashset != null) {
            lhs_containskey = getMethod(longhashset, new String[] { "contains" }, new Class[] { int.class, int.class });
        }
        else {
            longhashset = getOBCClass("org.bukkit.craftbukkit.util.LongHashset");
            lhs_containskey = getMethod(longhashset, new String[] { "containsKey" }, new Class[] { int.class, int.class });
        }

        cps_unloadqueue = getFieldNoFail(chunkprovserver, new String[] { "unloadQueue" }, longhashset); 
        if(cps_unloadqueue == null) {
            Log.info("Unload queue not found - default to unload all chunks");
        }
        /** n.m.s.Chunk */
        nmschunk = getNMSClass("net.minecraft.server.Chunk");
        nmsc_removeentities = getMethod(nmschunk, new String[] { "removeEntities" }, new Class[0]);
        nmsc_tileentities = getField(nmschunk, new String[] { "tileEntities" }, Map.class); 
        /** nbt classes */
        nbttagcompound = getNMSClass("net.minecraft.server.NBTTagCompound");
        nbttagbyte = getNMSClass("net.minecraft.server.NBTTagByte");
        nbttagshort = getNMSClass("net.minecraft.server.NBTTagShort");
        nbttagint = getNMSClass("net.minecraft.server.NBTTagInt");
        nbttaglong = getNMSClass("net.minecraft.server.NBTTagLong");
        nbttagfloat = getNMSClass("net.minecraft.server.NBTTagFloat");
        nbttagdouble = getNMSClass("net.minecraft.server.NBTTagDouble");
        nbttagbytearray = getNMSClass("net.minecraft.server.NBTTagByteArray");
        nbttagstring = getNMSClass("net.minecraft.server.NBTTagString");
        nbttagintarray = getNMSClass("net.minecraft.server.NBTTagIntArray");
        compound_get = getMethod(nbttagcompound, new String[] { "get" }, new Class[] { String.class });
        nbttagbyte_val = getField(nbttagbyte, new String[] { "data" }, byte.class);
        nbttagshort_val = getField(nbttagshort, new String[] { "data" }, short.class);
        nbttagint_val = getField(nbttagint, new String[] { "data" }, int.class);
        nbttaglong_val = getField(nbttaglong, new String[] { "data" }, long.class);
        nbttagfloat_val = getField(nbttagfloat, new String[] { "data" }, float.class);
        nbttagdouble_val = getField(nbttagdouble, new String[] { "data" }, double.class);
        nbttagbytearray_val = getField(nbttagbytearray, new String[] { "data" }, byte[].class);
        nbttagstring_val = getField(nbttagstring, new String[] { "data" }, String.class);
        nbttagintarray_val = getField(nbttagintarray, new String[] { "data" }, int[].class);

        /** Tile entity */
        nms_tileentity = getNMSClass("net.minecraft.server.TileEntity");
        nmst_readnbt = getMethod(nms_tileentity, new String[] { "b" }, new Class[] { nbttagcompound });
        nmst_x = getField(nms_tileentity, new String[] { "x" }, int.class); 
        nmst_y = getField(nms_tileentity, new String[] { "y" }, int.class); 
        nmst_z = getField(nms_tileentity, new String[] { "z" }, int.class); 
    }
    @Override
    public void unloadChunkNoSave(World w, Chunk c, int cx, int cz) {
        this.removeEntitiesFromChunk(c);
        w.unloadChunk(cx, cz, false, false);
    }

}
