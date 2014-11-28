package org.dynmap.bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.Log;
import org.dynmap.common.BiomeMap;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperCB extends BukkitVersionHelperGeneric {
    private Class<?> nmsblock;
    private Class<?> nmsblockarray;
    private Class<?> nmsmaterial;
    private Field blockbyid;
    private Field blockname;
    private Field material;
    private Method blockbyidfunc;   // 1.7+ method for getting block by id
    
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
        // Get block fields
        nmsblock = getNMSClass("net.minecraft.server.Block");
        nmsblockarray = getNMSClass("[Lnet.minecraft.server.Block;");
        nmsmaterial = getNMSClass("net.minecraft.server.Material");
        blockbyid = getFieldNoFail(nmsblock, new String[] { "byId" }, nmsblockarray);
        if (blockbyid == null) {
            blockbyidfunc = getMethod(nmsblock, new String[] { "getById", "e" }, new Class[] { int.class });
        }
        blockname = getPrivateField(nmsblock, new String[] { "name", "b" }, String.class);
        material = getPrivateField(nmsblock, new String[] { "material" }, nmsmaterial);

        /* Set up biomebase fields */
        biomebase = getNMSClass("net.minecraft.server.BiomeBase");
        biomebasearray =  getNMSClass("[Lnet.minecraft.server.BiomeBase;");
        biomebaselist = getPrivateField(biomebase, new String[] { "biomes" }, biomebasearray);
        biomebasetemp = getField(biomebase, new String[] { "temperature", "F" }, float.class);
        biomebasehumi = getField(biomebase, new String[] { "humidity", "G" }, float.class);
        biomebaseidstring = getField(biomebase, new String[] { "y", "af", "ah" }, String.class);
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
        nmsc_inhabitedticks = getFieldNoFail(nmschunk, new String[] { "s", "q", "u" }, long.class);
        if (nmsc_inhabitedticks == null) {
            Log.info("inhabitedTicks field not found - inhabited shader not functional");
        }
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
        nbttagbyte_val = getPrivateField(nbttagbyte, new String[] { "data" }, byte.class);
        nbttagshort_val = getPrivateField(nbttagshort, new String[] { "data" }, short.class);
        nbttagint_val = getPrivateField(nbttagint, new String[] { "data" }, int.class);
        nbttaglong_val = getPrivateField(nbttaglong, new String[] { "data" }, long.class);
        nbttagfloat_val = getPrivateField(nbttagfloat, new String[] { "data" }, float.class);
        nbttagdouble_val = getPrivateField(nbttagdouble, new String[] { "data" }, double.class);
        nbttagbytearray_val = getPrivateField(nbttagbytearray, new String[] { "data" }, byte[].class);
        nbttagstring_val = getPrivateField(nbttagstring, new String[] { "data" }, String.class);
        nbttagintarray_val = getPrivateField(nbttagintarray, new String[] { "data" }, int[].class);

        /** Tile entity */
        nms_tileentity = getNMSClass("net.minecraft.server.TileEntity");
        nmst_readnbt = getMethod(nms_tileentity, new String[] { "b" }, new Class[] { nbttagcompound });
        nmst_getposition = getMethodNoFail(nms_tileentity, new String[] { "getPosition" }, new Class[0]); // Try 1.8 method
        if (nmst_getposition == null) {
            nmst_x = getField(nms_tileentity, new String[] { "x" }, int.class); 
            nmst_y = getField(nms_tileentity, new String[] { "y" }, int.class); 
            nmst_z = getField(nms_tileentity, new String[] { "z" }, int.class); 
        }
        else {  /* BlockPosition */
            nms_blockposition = getNMSClass("net.minecraft.server.BlockPosition");
            nmsbp_getx = getMethod(nms_blockposition, new String[] { "getX" }, new Class[0]);
            nmsbp_gety = getMethod(nms_blockposition, new String[] { "getY" }, new Class[0]);
            nmsbp_getz = getMethod(nms_blockposition, new String[] { "getZ" }, new Class[0]);
        }
    }
    @Override
    public void unloadChunkNoSave(World w, Chunk c, int cx, int cz) {
        this.removeEntitiesFromChunk(c);
        w.unloadChunk(cx, cz, false, false);
    }
    /**
     * Get block short name list
     */
    @Override
    public String[] getBlockShortNames() {
        try {
            String[] names = new String[4096];
            if (blockbyid != null)  {
                Object[] byid = (Object[])blockbyid.get(nmsblock);
                for (int i = 0; i < names.length; i++) {
                    if (byid[i] != null) {
                        names[i] = (String)blockname.get(byid[i]);
                    }
                }
            }
            else {
                for (int i = 0; i < names.length; i++) {
                    Object blk = blockbyidfunc.invoke(nmsblock, i);
                    if (blk != null) {
                        names[i] = (String)blockname.get(blk);
                    }
                }
            }
            return names;
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return new String[0];
    }
    /**
     * Get biome name list
     */
    @Override
    public String[] getBiomeNames() {
        String[] names;
        /* Find array of biomes in biomebase */
        Object[] biomelist = getBiomeBaseList();
        names = new String[biomelist.length];
        /* Loop through list, starting afer well known biomes */
        for(int i = 0; i < biomelist.length; i++) {
            Object bb = biomelist[i];
            if(bb != null) {
                names[i] = getBiomeBaseIDString(bb);
            }
        }
        return names;
    }
    /**
     * Get block material index list
     */
    public int[] getBlockMaterialMap() {
        try {
            int[] map = new int[4096];
            if (blockbyid != null) {
                Object[] byid = (Object[])blockbyid.get(nmsblock);
                ArrayList<Object> mats = new ArrayList<Object>();
                for (int i = 0; i < map.length; i++) {
                    if (byid[i] != null) {
                        Object mat = (Object)material.get(byid[i]);
                        if (mat != null) {
                            map[i] = mats.indexOf(mat);
                            if (map[i] < 0) {
                                map[i] = mats.size();
                                mats.add(mat);
                            }
                        }
                        else {
                            map[i] = -1;
                        }
                    }
                }
            }
            else {
                ArrayList<Object> mats = new ArrayList<Object>();
                for (int i = 0; i < map.length; i++) {
                    Object blk = blockbyidfunc.invoke(nmsblock, i);
                    if (blk != null) {
                        Object mat = (Object)material.get(blk);
                        if (mat != null) {
                            map[i] = mats.indexOf(mat);
                            if (map[i] < 0) {
                                map[i] = mats.size();
                                mats.add(mat);
                            }
                        }
                        else {
                            map[i] = -1;
                        }
                    }
                }
            }
            return map;
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return new int[0];
    }
}
