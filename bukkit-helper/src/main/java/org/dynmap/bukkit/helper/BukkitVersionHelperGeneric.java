package org.dynmap.bukkit.helper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.dynmap.Log;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import com.google.common.base.Charsets;
import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.Iterables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Helper for isolation of bukkit version specific issues
 */
public abstract class BukkitVersionHelperGeneric extends BukkitVersionHelper {
    private String obc_package; // Package used for org.bukkit.craftbukkit
    protected String nms_package; // Package used for net.minecraft.server
    private boolean failed;
    protected static final Object[] nullargs = new Object[0];
    protected static final Class<?>[] nulltypes = new Class[0];
    private static final Map<?, ?> nullmap = Collections.emptyMap();
    
    /** CraftChunkSnapshot */
    protected Class<?> craftchunksnapshot;
    private Field ccss_biome;
    /** CraftChunk */
    private Class<?> craftchunk;
    private Method cc_gethandle;
    /** CraftWorld */
    private Class<?> craftworld;
    private Method cw_gethandle;

    /** BiomeBase related helpers */
    protected Class<?> biomestorage;
    protected Field biomestoragebase;
    protected Class<?> biomebase;
    protected Class<?> biomebasearray;
    protected Field biomebaselist;
    protected Field biomebasetemp;
    protected Field biomebasehumi;
    protected Method biomebasetempfunc;
    protected Method biomebasehumifunc;
    protected Field biomebaseidstring;
    protected Field biomebaseid;
    /** n.m.s.World */
    protected Class<?> nmsworld;
    protected Class<?> chunkprovserver;
    protected Class<?> longhashset;
    protected Field nmsw_chunkproviderserver;
    protected Field cps_unloadqueue;
    protected boolean cps_unloadqueue_isSet;
    protected Method lhs_containskey;
    /** n.m.s.Chunk */
    protected Class<?> nmschunk;
    protected Field nmsc_tileentities;
    protected Field nmsc_inhabitedticks;
    /** nbt classes */
    protected Class<?> nbttagcompound;
    protected Class<?> nbttagbyte;
    protected Class<?> nbttagshort;
    protected Class<?> nbttagint;
    protected Class<?> nbttaglong;
    protected Class<?> nbttagfloat;
    protected Class<?> nbttagdouble;
    protected Class<?> nbttagbytearray;
    protected Class<?> nbttagstring;
    protected Class<?> nbttagintarray;
    protected Method compound_get;
    protected Field nbttagbyte_val;
    protected Field nbttagshort_val;
    protected Field nbttagint_val;
    protected Field nbttaglong_val;
    protected Field nbttagfloat_val;
    protected Field nbttagdouble_val;
    protected Field nbttagbytearray_val;
    protected Field nbttagstring_val;
    protected Field nbttagintarray_val;
    /** Tile entity */
    protected Class<?> nms_tileentity;
    protected Method nmst_readnbt;
    protected Field nmst_x;
    protected Field nmst_y;
    protected Field nmst_z;
    protected Method nmst_getposition;
    /** BlockPosition */
    protected Class<?> nms_blockposition;
    protected Method nmsbp_getx;
    protected Method nmsbp_gety;
    protected Method nmsbp_getz;
    
    /** Server */
    protected Method server_getonlineplayers;
    /** Player */
    protected Method player_gethealth;
    // CraftPlayer
    private Class<?> obc_craftplayer;
    private Method obcplayer_getprofile;
    // GameProfile
    private Class<?> cma_gameprofile;
    private Method cmaprofile_getproperties;
    // Property
    private Class<?> cma_property;
    private Method cmaproperty_getvalue;

    protected BukkitVersionHelperGeneric() {
        failed = false;
        /* Look up base classname for bukkit server - tells us OBC package */
        obc_package = Bukkit.getServer().getClass().getPackage().getName();
        /* Get NMS package */
        nms_package = getNMSPackage();
        if(nms_package == null) {
            failed = true;
        }
        /* Craftworld fields */
        craftworld = getOBCClass("org.bukkit.craftbukkit.CraftWorld");
        cw_gethandle = getMethod(craftworld, new String[] { "getHandle" }, new Class[0]);
        /* CraftChunkSnapshot */
        craftchunksnapshot = getOBCClass("org.bukkit.craftbukkit.CraftChunkSnapshot");
        biomebasearray =  getNMSClass("[Lnet.minecraft.server.BiomeBase;");
        ccss_biome = getPrivateFieldNoFail(craftchunksnapshot, new String[] { "biome" }, biomebasearray);
        if(ccss_biome == null) {
            biomestorage = getNMSClass("net.minecraft.server.BiomeStorage");
            biomestoragebase = getPrivateField(biomestorage, new String[] { "h", "g", "f" }, biomebasearray);
            ccss_biome = getPrivateField(craftchunksnapshot, new String[] { "biome" }, biomestorage);
        }
        /* CraftChunk */
        craftchunk = getOBCClass("org.bukkit.craftbukkit.CraftChunk");
        cc_gethandle = getMethod(craftchunk, new String[] { "getHandle" }, new Class[0]);
        
        /** Server */
        server_getonlineplayers = getMethod(Server.class, new String[] { "getOnlinePlayers" }, new Class[0]);
        /** Player */
        player_gethealth = getMethod(Player.class, new String[] { "getHealth" }, new Class[0]);

        // CraftPlayer
        obc_craftplayer = getOBCClass("org.bukkit.craftbukkit.entity.CraftPlayer");
        obcplayer_getprofile = getMethod(obc_craftplayer, new String[] { "getProfile" }, new Class[0]);
        // GameProfile
        cma_gameprofile = getOBCClass("com.mojang.authlib.GameProfile");
        cmaprofile_getproperties = getMethod(cma_gameprofile, new String[] { "getProperties" }, new Class[0]);
        // Property
        cma_property = getOBCClass("com.mojang.authlib.properties.Property");
	    cmaproperty_getvalue = getMethod(cma_property, new String[] { "getValue" }, new Class[0]);
        		
        /* Get NMS classes and fields */
        if(!failed)
            loadNMS();

        if(failed)
            throw new IllegalArgumentException("Error initializing dynmap - bukkit version incompatible!");
    }
    
    protected abstract void loadNMS();
    
    protected abstract String getNMSPackage();
    
    protected Class<?> getOBCClass(String classname) {
        return getClassByName(classname, "org.bukkit.craftbukkit", obc_package, false);
    }

    protected Class<?> getOBCClassNoFail(String classname) {
        return getClassByName(classname, "org.bukkit.craftbukkit", obc_package, true);
    }

    protected Class<?> getNMSClass(String classname) {
        return getClassByName(classname, "net.minecraft.server", nms_package, false);
    }

    protected Class<?> getNMSClassNoFail(String classname) {
        return getClassByName(classname, "net.minecraft.server", nms_package, true);
    }

    protected Class<?> getClassByName(String classname, String base, String mapping, boolean nofail) {
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
    protected Field getField(Class<?> cls, String[] ids, Class<?> type) {
        return getField(cls, ids, type, false);
    }
    protected Field getFieldNoFail(Class<?> cls, String[] ids, Class<?> type) {
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
    protected Field getPrivateFieldNoFail(Class<?> cls, String[] ids, Class<?> type) {
        return getPrivateField(cls, ids, type, true);
    }
    /**
     * Get private field
     */
    protected Field getPrivateField(Class<?> cls, String[] ids, Class<?> type) {
        return getPrivateField(cls, ids, type, false);
    }
    /**
     * Get private field
     */
    protected Field getPrivateField(Class<?> cls, String[] ids, Class<?> type, boolean nofail) {
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
        if (!nofail) {
            Log.severe("Unable to find field " + ids[0] + " for " + cls.getName());
            failed = true;
        }
        return null;
    }
    protected Object getFieldValue(Object obj, Field field, Object def) {
        if((obj != null) && (field != null)) {
            try {
                return field.get(obj);
            } catch (IllegalArgumentException e) {
                System.out.println(String.format("IllegalArgExc(%s,%s)", obj.toString(), field.toString()));
            } catch (IllegalAccessException e) {
                System.out.println(String.format("IllegalAccessExc(%s,%s)", obj.toString(), field.toString()));
            }
        }
        else {
            System.out.println(String.format("NullArg(%s,%s)", (obj != null)?obj.toString():"null", (field != null)?field.toString():"null"));
        }
        return def;
    }
    /**
     * Get method
     */
    protected Method getMethod(Class<?> cls, String[] ids, Class<?>[] args) {
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
    protected Method getMethodNoFail(Class<?> cls, String[] ids, Class<?>[] args) {
        if(cls == null) return null;
        for(String id : ids) {
            try {
                return cls.getMethod(id, args);
            } catch (SecurityException e) {
            } catch (NoSuchMethodException e) {
            }
        }
        return null;
    }
    protected Object callMethod(Object obj, Method meth, Object[] args, Object def) {
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
        Object baselist = getFieldValue(biomebase, biomebaselist, new Object[0]);
        if(biomestoragebase != null)
            baselist = getFieldValue(baselist, biomestoragebase, new Object[0]);

        return (Object[])baselist;
    }
    /** Get temperature from biomebase */
    public float getBiomeBaseTemperature(Object bb) {
        if (biomebasetempfunc != null) {
            return (Float) callMethod(bb, biomebasetempfunc, new Object[0], 0.5f);
        }
        else {
            return (Float) getFieldValue(bb, biomebasetemp, 0.5F);
        }
    }
    /** Get humidity from biomebase */
    public float getBiomeBaseHumidity(Object bb) {
        if (biomebasehumifunc != null) {
            return (Float) callMethod(bb, biomebasehumifunc, new Object[0], 0.5f);
        }
        else {
            return (Float) getFieldValue(bb, biomebasehumi, 0.5F);
        }
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
    protected Object getNMSWorld(World w) {
        return callMethod(w, cw_gethandle, nullargs, null);
    }

    /* Get unload queue for given NMS world */
    public Object getUnloadQueue(World world) {
        Object cps = getFieldValue(getNMSWorld(world), nmsw_chunkproviderserver, null); // Get chunkproviderserver
        if ((cps != null) && (cps_unloadqueue != null)) {
            return getFieldValue(cps, cps_unloadqueue, null);
        }
        return null;
    }

    /* For testing unload queue for presence of givne chunk */
    public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
        if(unloadqueue != null) {
            if (cps_unloadqueue_isSet)
                return ((Set) unloadqueue).contains(Long.valueOf((long)x & 0xFFFFFFFF | ((long)z & 0xFFFFFFFF) << 32));
            return (Boolean)callMethod(unloadqueue, lhs_containskey, new Object[] { x, z }, true);
        }
        return true;
    }
    
    public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
        return (Object[])getFieldValue(css, ccss_biome, null);
    }
//    public boolean isCraftChunkSnapshot(ChunkSnapshot css) {
//        if(craftchunksnapshot != null) {
//            return craftchunksnapshot.isAssignableFrom(css.getClass());
//        }
//        return false;
//    }

    /**
     * Get inhabited ticks count from chunk
     */
    private static final Long zero = new Long(0);
    public long getInhabitedTicks(Chunk c) {
        if (nmsc_inhabitedticks == null) {
            return 0;
        }
        Object omsc = callMethod(c, cc_gethandle, nullargs, null);
        if(omsc != null) {
            return (Long)getFieldValue(omsc, nmsc_inhabitedticks, zero);
        }
        return 0;
    }

    /** Get tile entities map from chunk */
    public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
        Object omsc = callMethod(c, cc_gethandle, nullargs, null);
        if(omsc != null) {
            return (Map<?, ?>)getFieldValue(omsc, nmsc_tileentities, nullmap);
        }
        return nullmap;
    }
    /**
     * Get X coordinate of tile entity
     */
    public int getTileEntityX(Object te) {
        if (nmst_getposition == null) {
            return (Integer)getFieldValue(te, nmst_x, 0);
        }
        else {
            Object pos = callMethod(te, nmst_getposition, nullargs, null);
            return (Integer) callMethod(pos, nmsbp_getx, nullargs, null);
        }
    }
    /**
     * Get Y coordinate of tile entity
     */
    public int getTileEntityY(Object te) {
        if (nmst_getposition == null) {
            return (Integer)getFieldValue(te, nmst_y, 0);
        }
        else {
            Object pos = callMethod(te, nmst_getposition, nullargs, null);
            return (Integer) callMethod(pos, nmsbp_gety, nullargs, null);
        }
    }
    /**
     * Get Z coordinate of tile entity
     */
    public int getTileEntityZ(Object te) {
        if (nmst_getposition == null) {
            return (Integer)getFieldValue(te, nmst_z, 0);
        }
        else {
            Object pos = callMethod(te, nmst_getposition, nullargs, null);
            return (Integer) callMethod(pos, nmsbp_getz, nullargs, null);
        }
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
    /**
     * Get list of online players
     */
    public Player[] getOnlinePlayers() {
        Object players = callMethod(Bukkit.getServer(), server_getonlineplayers, nullargs, null);
        if (players instanceof Player[]) {  /* Pre 1.7.10 */
            return (Player[]) players;
        }
        else {
            @SuppressWarnings("unchecked")
            Collection<? extends Player> p = (Collection<? extends Player>) players;
            return p.toArray(new Player[0]);
        }
    }
    /**
     * Get player health
     */
    @Override
    public double getHealth(Player p) {
        Object health = callMethod(p, player_gethealth, nullargs, null);
        if (health instanceof Integer) {
            return (Integer) health;
        }
        else {
            return ((Double) health).intValue();
        }
    }
    
    private static final Gson gson = new GsonBuilder().create();

    public class TexturesPayload {
        public long timestamp;
        public String profileId;
        public String profileName;
        public boolean isPublic;
        public Map<String, ProfileTexture> textures;

    }
    public class ProfileTexture {
        public String url;
    }

    /**
     * Get skin URL for player
     * @param player
     */
    public String getSkinURL(Player player) {
    	String url = null;
        Object profile = callMethod(player, obcplayer_getprofile, nullargs, null);
    	if (profile != null) {
    		Object propmap = callMethod(profile, cmaprofile_getproperties, nullargs, null);
    		if ((propmap != null) && (propmap instanceof ForwardingMultimap)) {
    			ForwardingMultimap<String, Object> fmm = (ForwardingMultimap<String, Object>) propmap;
    			Collection<Object> txt = fmm.get("textures");
    	        Object textureProperty = Iterables.getFirst(fmm.get("textures"), null);
    	        if (textureProperty != null) {
    				String val = (String) callMethod(textureProperty, cmaproperty_getvalue, nullargs, null);
    				if (val != null) {
    					TexturesPayload result = null;
    					try {
    						String json = new String(Base64Coder.decode(val), Charsets.UTF_8);
    						result = gson.fromJson(json, TexturesPayload.class);
    					} catch (JsonParseException e) {
    					} catch (IllegalArgumentException x) {
    						Log.warning("Malformed response from skin URL check: " + val);
    					}
    					if ((result != null) && (result.textures != null) && (result.textures.containsKey("SKIN"))) {
    						url = result.textures.get("SKIN").url;
    					}
    				}
    			}
    		}
    	}
    	
    	return url;
    }

}
