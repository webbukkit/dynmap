package org.dynmap.bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.Log;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelper {
    private String obc_package; // Package used for org.bukkit.craftbukkit
    private String nms_package; // Package used for net.minecraft.server
    private static BukkitVersionHelper helper;
    private boolean failed;
    private static final Object[] nullargs = new Object[0];
    private static final Map nullmap = Collections.emptyMap();
    /** BiomeBase related helpers */
    private Class<?> biomebase;
    private Class<?> biomebasearray;
    private Field biomebaselist;
    private Field biomebasetemp;
    private Field biomebasehumi;
    private Field biomebaseidstring;
    private Field biomebaseid;
    /** CraftWorld */
    private Class<?> craftworld;
    private Method cw_gethandle;
    /** n.m.s.World */
    private Class<?> nmsworld;
    private Class<?> chunkprovserver;
    private Class<?> longhashset;
    private Field nmsw_chunkproviderserver;
    private Field cps_unloadqueue;
    private Method lhs_containskey;
    /** CraftChunkSnapshot */
    private Class<?> craftchunksnapshot;
    private Field ccss_biome;
    /** CraftChunk */
    private Class<?> craftchunk;
    private Method cc_gethandle;
    /** o.m.s.Chunk */
    private Class<?> nmschunk;
    private Method nmsc_removeentities;
    private Field nmsc_tileentities;
    /** nbt classes */
    private Class<?> nbttagcompound;
    private Class<?> nbttagbyte;
    private Class<?> nbttagshort;
    private Class<?> nbttagint;
    private Class<?> nbttaglong;
    private Class<?> nbttagfloat;
    private Class<?> nbttagdouble;
    private Class<?> nbttagbytearray;
    private Class<?> nbttagstring;
    private Class<?> nbttagintarray;
    private Method compound_get;
    private Field nbttagbyte_val;
    private Field nbttagshort_val;
    private Field nbttagint_val;
    private Field nbttaglong_val;
    private Field nbttagfloat_val;
    private Field nbttagdouble_val;
    private Field nbttagbytearray_val;
    private Field nbttagstring_val;
    private Field nbttagintarray_val;
    
    /** Tile entity */
    private Class<?> nms_tileentity;
    private Method nmst_readnbt;
    private Field nmst_x;
    private Field nmst_y;
    private Field nmst_z;

    public static final BukkitVersionHelper getHelper() {
        if(helper == null) {
            helper = new BukkitVersionHelper();
        }
        return helper;
    }
    
    private BukkitVersionHelper() {
        failed = false;
        Server srv = Bukkit.getServer();
        /* Look up base classname for bukkit server - tells us OBC package */
        obc_package = Bukkit.getServer().getClass().getPackage().getName();
        /* Get getHandle() method */
        try {
            Method m = srv.getClass().getMethod("getHandle");
            Object scm = m.invoke(srv); /* And use it to get SCM (nms object) */
            nms_package = scm.getClass().getPackage().getName();
        } catch (Exception x) {
            Log.severe("Error finding net.minecraft.server packages");
            nms_package = "net.minecraft.server" + obc_package.substring("org.bukkit.craftbukkit".length());
            failed = true;
        }
        
        /* Set up biomebase fields */
        biomebase = getNMSClass("net.minecraft.server.BiomeBase");
        biomebasearray =  getNMSClass("[Lnet.minecraft.server.BiomeBase;");
        biomebaselist = getField(biomebase, new String[] { "biomes" }, biomebasearray);
        biomebasetemp = getField(biomebase, new String[] { "temperature", "F" }, float.class);
        biomebasehumi = getField(biomebase, new String[] { "humidity", "G" }, float.class);
        biomebaseidstring = getField(biomebase, new String[] { "y" }, String.class);
        biomebaseid = getField(biomebase, new String[] { "id" }, int.class);
        /* Craftworld fields */
        craftworld = getOBCClass("org.bukkit.craftbukkit.CraftWorld");
        cw_gethandle = getMethod(craftworld, new String[] { "getHandle" }, new Class[0]);
        /* n.m.s.World */
        nmsworld = getNMSClass("net.minecraft.server.WorldServer");
        chunkprovserver = getNMSClass("net.minecraft.server.ChunkProviderServer");
        longhashset = getOBCClassNoFail("org.bukkit.craftbukkit.util.LongHashSet");
        if(longhashset != null) {
            lhs_containskey = getMethod(longhashset, new String[] { "contains" }, new Class[] { int.class, int.class });
        }
        else {
            longhashset = getOBCClass("org.bukkit.craftbukkit.util.LongHashset");
            lhs_containskey = getMethod(longhashset, new String[] { "containsKey" }, new Class[] { int.class, int.class });
        }
        nmsw_chunkproviderserver = getField(nmsworld, new String[] { "chunkProviderServer" }, chunkprovserver);
        cps_unloadqueue = getFieldNoFail(chunkprovserver, new String[] { "unloadQueue" }, longhashset); 
        if(cps_unloadqueue == null) {
            Log.info("Unload queue not found - default to unload all chunks");
        }
        /* CraftChunkSnapshot */
        craftchunksnapshot = getOBCClass("org.bukkit.craftbukkit.CraftChunkSnapshot");
        ccss_biome = getPrivateField(craftchunksnapshot, new String[] { "biome" }, biomebasearray);
        /** CraftChunk */
        craftchunk = getOBCClass("org.bukkit.craftbukkit.CraftChunk");
        cc_gethandle = getMethod(craftchunk, new String[] { "getHandle" }, new Class[0]);
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
        
        if(failed)
            throw new IllegalArgumentException("Error initializing dynmap - bukkit version incompatible!");
    }
    
    public Class<?> getOBCClass(String classname) {
        return getClassByName(classname, "org.bukkit.craftbukkit", obc_package, false);
    }

    public Class<?> getOBCClassNoFail(String classname) {
        return getClassByName(classname, "org.bukkit.craftbukkit", obc_package, true);
    }

    public Class<?> getNMSClass(String classname) {
        return getClassByName(classname, "net.minecraft.server", nms_package, false);
    }
    
    public Class<?> getClassByName(String classname, String base, String mapping, boolean nofail) {
        String n = classname;
        int idx = classname.indexOf(base);
        if(idx >= 0) {
            n = classname.substring(0, idx) + mapping + classname.substring(idx + base.length());
        }
        try {
            return Class.forName(n);
        } catch (ClassNotFoundException cnfx) {
            try {
                return Class.forName(classname);
            } catch (ClassNotFoundException cnfx2) {
                if(!nofail) {
                    Log.severe("Cannot find " + classname);
                    failed = true;
                }
                return null;
            }
        }
    }
    /**
     * Get field
     */
    private Field getField(Class<?> cls, String[] ids, Class<?> type) {
        return getField(cls, ids, type, false);
    }
    private Field getFieldNoFail(Class<?> cls, String[] ids, Class<?> type) {
        return getField(cls, ids, type, true);
    }
    /**
     * Get field
     */
    private Field getField(Class<?> cls, String[] ids, Class<?> type, boolean nofail) {
        if((cls == null) || (type == null)) return null;
        for(String id : ids) {
            try {
                Field f = cls.getField(id);
                if(f.getType().isAssignableFrom(type)) {
                    return f;
                }
            } catch (NoSuchFieldException nsfx) {
            }
        }
        if(!nofail) {
            Log.severe("Unable to find field " + ids[0] + " for " + cls.getName());
            failed = true;
        } 
        return null;
    }
    /**
     * Get private field
     */
    private Field getPrivateField(Class<?> cls, String[] ids, Class<?> type) {
        if((cls == null) || (type == null)) return null;
        for(String id : ids) {
            try {
                Field f = cls.getDeclaredField(id);
                if(f.getType().isAssignableFrom(type)) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException nsfx) {
            }
        }
        Log.severe("Unable to find field " + ids[0] + " for " + cls.getName());
        failed = true;
        return null;
    }
    private Object getFieldValue(Object obj, Field field, Object def) {
        if((obj != null) && (field != null)) {
            try {
                return field.get(obj);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            }
        }
        return def;
    }
    /**
     * Get method
     */
    private Method getMethod(Class<?> cls, String[] ids, Class[] args) {
        if(cls == null) return null;
        for(String id : ids) {
            try {
                return cls.getMethod(id, args);
            } catch (SecurityException e) {
            } catch (NoSuchMethodException e) {
            }
        }
        Log.severe("Unable to find method " + ids[0] + " for " + cls.getName());
        failed = true;
        return null;
    }
    private Object callMethod(Object obj, Method meth, Object[] args, Object def) {
        if((obj == null) || (meth == null)) {
            return def;
        }
        try {
            return meth.invoke(obj, args);
        } catch (IllegalArgumentException iax) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return def;
    }
    

    /**
     * Get list of defined biomebase objects
     */
    public Object[] getBiomeBaseList() {
        return (Object[]) getFieldValue(biomebase, biomebaselist, new Object[0]);
    }
    /** Get temperature from biomebase */
    public float getBiomeBaseTemperature(Object bb) {
        return (Float) getFieldValue(bb, biomebasetemp, 0.5F);
    }
    /** Get humidity from biomebase */
    public float getBiomeBaseHumidity(Object bb) {
        return (Float) getFieldValue(bb, biomebasehumi, 0.5F);
    }
    /** Get ID string from biomebase */
    public String getBiomeBaseIDString(Object bb) {
        return (String) getFieldValue(bb, biomebaseidstring, null);
    }
    /** Get ID from biomebase */
    public int getBiomeBaseID(Object bb) {
        return (Integer) getFieldValue(bb, biomebaseid, -1);
    }

    /* Get net.minecraft.server.world for given world */
    public Object getNMSWorld(World w) {
        return callMethod(w, cw_gethandle, nullargs, null);
    }

    /* Get unload queue for given NMS world */
    public Object getUnloadQueue(Object nmsworld) {
        Object cps = getFieldValue(nmsworld, nmsw_chunkproviderserver, null); // Get chunkproviderserver
        if(cps != null) {
            return getFieldValue(cps, cps_unloadqueue, null);
        }
        return null;
    }

    /* For testing unload queue for presence of givne chunk */
    public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
        if(unloadqueue != null) {
            return (Boolean)callMethod(unloadqueue, lhs_containskey, new Object[] { x, z }, true);
        }
        return true;
    }
    
    public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
        return (Object[])getFieldValue(css, ccss_biome, null);
    }
    public boolean isCraftChunkSnapshot(ChunkSnapshot css) {
        if(craftchunksnapshot != null) {
            return craftchunksnapshot.isAssignableFrom(css.getClass());
        }
        return false;
    }
    /** Remove entities from given chunk */
    public void removeEntitiesFromChunk(Chunk c) {
        Object omsc = callMethod(c, cc_gethandle, nullargs, null);
        if(omsc != null) {
            callMethod(omsc, nmsc_removeentities, nullargs, null);
        }
    }
    /** Get tile entities map from chunk */
    public Map getTileEntitiesForChunk(Chunk c) {
        Object omsc = callMethod(c, cc_gethandle, nullargs, null);
        if(omsc != null) {
            return (Map)getFieldValue(omsc, nmsc_tileentities, nullmap);
        }
        return nullmap;
    }
    /**
     * Get X coordinate of tile entity
     */
    public int getTileEntityX(Object te) {
        return (Integer)getFieldValue(te, nmst_x, 0);
    }
    /**
     * Get Y coordinate of tile entity
     */
    public int getTileEntityY(Object te) {
        return (Integer)getFieldValue(te, nmst_y, 0);
    }
    /**
     * Get Z coordinate of tile entity
     */
    public int getTileEntityZ(Object te) {
        return (Integer)getFieldValue(te, nmst_z, 0);
    }
    /**
     * Read tile entity NBT
     */
    public Object readTileEntityNBT(Object te) {
        if(nbttagcompound == null) return null;
        Object nbt = null;
        try {
            nbt = nbttagcompound.newInstance();
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        }
        if(nbt != null) {
            callMethod(te, nmst_readnbt, new Object[] { nbt }, null);
        }
        return nbt;
    }
    /**
     * Get field value from NBT compound
     */
    public Object getFieldValue(Object nbt, String field) {
        Object val = callMethod(nbt, compound_get, new Object[] { field }, null);
        if(val == null) return null;
        Class<?> valcls = val.getClass();
        if(valcls.equals(nbttagbyte)) {
            return getFieldValue(val, nbttagbyte_val, null);
        }
        else if(valcls.equals(nbttagshort)) {
            return getFieldValue(val, nbttagshort_val, null);
        }
        else if(valcls.equals(nbttagint)) {
            return getFieldValue(val, nbttagint_val, null);
        }
        else if(valcls.equals(nbttaglong)) {
            return getFieldValue(val, nbttaglong_val, null);
        }
        else if(valcls.equals(nbttagfloat)) {
            return getFieldValue(val, nbttagfloat_val, null);
        }
        else if(valcls.equals(nbttagdouble)) {
            return getFieldValue(val, nbttagdouble_val, null);
        }
        else if(valcls.equals(nbttagbytearray)) {
            return getFieldValue(val, nbttagbytearray_val, null);
        }
        else if(valcls.equals(nbttagstring)) {
            return getFieldValue(val, nbttagstring_val, null);
        }
        else if(valcls.equals(nbttagintarray)) {
            return getFieldValue(val, nbttagintarray_val, null);
        }
        return null;
    }
}
