package org.dynmap.bukkit.helper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.dynmap.Log;
import org.dynmap.hdmap.HDBlockModels;
import org.dynmap.utils.Polygon;

/**
 * Helper for isolation of bukkit version specific issues
 */
public class BukkitVersionHelperCB extends BukkitVersionHelperGeneric {
    private Class<?> nmsblock;
    private Class<?> nmsblockarray;
    private Class<?> nmsmaterial;
    private Class<?> longset;
    private Field blockbyid;
    private Field material;
    private Method material_issolid;
    private Method material_isliquid;
    private Method blockbyidfunc;   // 1.7+ method for getting block by id
    private Method getworldborder;  // 1.8+ method for getting world border
    private Class<?> nmsworldborder;
    private Method worldborderminx;
    private Method worldbordermaxx;
    private Method worldborderminz;
    private Method worldbordermaxz;
    private Method getbiomebyid;
    private Method getbiomefunc;
    private Method getidbybiome;
    private Method getbycombinedid;
    
    private boolean isBadUnload = false;
    
    public BukkitVersionHelperCB() {
    	
        String bukkitver = Bukkit.getServer().getVersion();
        String mcver = "1.0.0";
        int idx = bukkitver.indexOf("(MC: ");
        if(idx > 0) {
            mcver = bukkitver.substring(idx+5);
            idx = mcver.indexOf(")");
            if(idx > 0) mcver = mcver.substring(0, idx);
        }
        isBadUnload = HDBlockModels.checkVersionRange(mcver, "1.9-");
        Log.verboseinfo("MCVER=" + mcver + ", isBadUnload=" + isBadUnload);
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
        if (isBlockMaterialNeeded()) {
            nmsmaterial = getNMSClass("net.minecraft.server.Material");
            if (isBlockIdNeeded()) {   // Not needed for 1.13+
                blockbyid = getFieldNoFail(nmsblock, new String[] { "byId" }, nmsblockarray);
                if (blockbyid == null) {
                    blockbyidfunc = getMethod(nmsblock, new String[] { "getById", "e" }, new Class[] { int.class });
                }
            }
            material = getPrivateField(nmsblock, new String[] { "material" }, nmsmaterial);
            getbycombinedid = getMethod(nmsblock, new String[] { "getByCombinedId" }, new Class[] { int.class });
            // Get material methods
            material_issolid = getMethod(nmsmaterial, new String[] { "isSolid" }, nulltypes);
            material_isliquid = getMethod(nmsmaterial, new String[] { "isLiquid" }, nulltypes);        	
        }
        /* Set up biomebase fields */
        if (isBiomeBaseListNeeded()) {
        	biomebase = getNMSClass("net.minecraft.server.BiomeBase");
        	biomebasearray =  getNMSClass("[Lnet.minecraft.server.BiomeBase;");
        	biomebaselist = getPrivateFieldNoFail(biomebase, new String[] { "biomes" }, biomebasearray);
        	if (biomebaselist == null) {
        		getbiomefunc = getMethodNoFail(biomebase, new String[] { "getBiome" }, new Class[] { int.class, biomebase });
        		if (getbiomefunc == null) {
        			getbiomebyid = getMethod(biomebase, new String[] { "a" }, new Class[] { int.class} );
        		}
        	}
        	biomebasetempfunc = getMethodNoFail(biomebase, new String[] { "getTemperature" }, nulltypes);
        	if (biomebasetempfunc == null) {
        		biomebasetemp = getPrivateFieldNoFail(biomebase, new String[] { "B" }, float.class);
        		if (biomebasetemp != null) {
        			biomebasehumi = getPrivateField(biomebase, new String[] { "C" }, float.class);
        		}
        		else {
        			biomebasetemp = getPrivateField(biomebase, new String[] { "temperature", "F", "C", "aO" }, float.class);
        			biomebasehumi = getPrivateField(biomebase, new String[] { "humidity", "G", "D", "aP" }, float.class);
        		}
        	}
        	else {
        		biomebasehumifunc = getMethod(biomebase, new String[] { "getHumidity" }, nulltypes);
        	}
        	biomebaseidstring = getPrivateField(biomebase, new String[] { "y", "af", "ah", "z", "aS", "aR", "f" }, String.class);
        	biomebaseid = getFieldNoFail(biomebase, new String[] { "id" }, int.class);
        	if (biomebaseid == null) {
        		getidbybiome = getMethod(biomebase, new String[] { "a" }, new Class[] { biomebase } );
        	}
        }
        /* n.m.s.World */
        nmsworld = getNMSClass("net.minecraft.server.WorldServer");
        chunkprovserver = getNMSClass("net.minecraft.server.ChunkProviderServer");
        nmsw_chunkproviderserver = getPrivateFieldNoFail(nmsworld, new String[] { "chunkProviderServer" }, chunkprovserver);
        if (nmsw_chunkproviderserver == null) {
            Class<?> nmsworldbase = getNMSClass("net.minecraft.server.World");
            Class<?> nmsichunkprovider = getNMSClass("net.minecraft.server.IChunkProvider");
            nmsw_chunkproviderserver = getPrivateField(nmsworldbase, new String[] { "chunkProvider" }, nmsichunkprovider);
        }
        getworldborder = getMethodNoFail(nmsworld, new String[] { "af" }, nulltypes);
        
        longhashset = getOBCClassNoFail("org.bukkit.craftbukkit.util.LongHashSet");
        if(longhashset != null) {
            lhs_containskey = getMethod(longhashset, new String[] { "contains" }, new Class[] { int.class, int.class });
        }
        else {
            longhashset = getOBCClassNoFail("org.bukkit.craftbukkit.util.LongHashset");
            if (longhashset != null) {
                lhs_containskey = getMethod(longhashset, new String[] { "containsKey" }, new Class[] { int.class, int.class });
            }
        }
    	longset = getOBCClassNoFail("org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.longs.LongSet");
    	if (longhashset != null) {
            lhs_containskey = getMethod(longhashset, new String[] { "contains" }, new Class[] { long.class });
    	}
        cps_unloadqueue_isSet = false;
        if (longhashset != null) {
            cps_unloadqueue = getPrivateFieldNoFail(chunkprovserver, new String[] { "unloadQueue" }, longhashset); 
        }
        if ((cps_unloadqueue == null) && (longset != null)) {
            cps_unloadqueue = getPrivateFieldNoFail(chunkprovserver, new String[] { "unloadQueue" }, longset); 
            cps_unloadqueue_isSet = true;
        }
        if(cps_unloadqueue == null) {
            cps_unloadqueue = getFieldNoFail(chunkprovserver, new String[] { "unloadQueue" }, Set.class); 
            cps_unloadqueue_isSet = true;
        }
        if(cps_unloadqueue == null) {
            Log.info("Unload queue not found - default to unload all chunks");
        }
        /** n.m.s.Chunk */
        nmschunk = getNMSClass("net.minecraft.server.Chunk");
        nmsc_tileentities = getField(nmschunk, new String[] { "tileEntities" }, Map.class);
        nmsc_inhabitedticks = getPrivateFieldNoFail(nmschunk, new String[] { "s", "q", "u", "v", "w", "A", "z" }, long.class);
        if (nmsc_inhabitedticks == null) {
            Log.info("inhabitedTicks field not found - inhabited shader not functional");
        }
        /** n.m.s.WorldBorder */
        nmsworldborder = getNMSClassNoFail("net.minecraft.server.WorldBorder");
        if (nmsworldborder != null) {
            worldbordermaxz = getMethodNoFail(nmsworldborder, new String[] { "f" }, nulltypes);
            if (worldbordermaxz == null) {
            	worldborderminx = getMethod(nmsworldborder, new String[] { "b" }, nulltypes);
            	worldborderminz = getMethod(nmsworldborder, new String[] { "c" }, nulltypes);
            	worldbordermaxx = getMethod(nmsworldborder, new String[] { "d" }, nulltypes);
            	worldbordermaxz = getMethod(nmsworldborder, new String[] { "e" }, nulltypes);
            }
            else {
            	worldborderminx = getMethod(nmsworldborder, new String[] { "c" }, nulltypes);
            	worldborderminz = getMethod(nmsworldborder, new String[] { "d" }, nulltypes);
            	worldbordermaxx = getMethod(nmsworldborder, new String[] { "e" }, nulltypes);
            }
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
        nmst_readnbt = getMethod(nms_tileentity, new String[] { "b", "save" }, new Class[] { nbttagcompound });
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
        w.unloadChunk(cx, cz, false, false);
    }
    private String stripBlockString(String bname) {
    	int idx = bname.indexOf('{');
    	if (idx >= 0) bname = bname.substring(idx+1);
    	idx = bname.indexOf('}');
    	if (idx >= 0) bname = bname.substring(0, idx);
    	return bname;
    }
    /**
     * Get block short name list
     */
    @Override
    public String[] getBlockNames() {
        try {
            String[] names = new String[4096];
            if (blockbyid != null)  {
                Object[] byid = (Object[])blockbyid.get(nmsblock);
                for (int i = 0; i < names.length; i++) {
                    if (byid[i] != null) {
                        names[i] = stripBlockString(byid[i].toString());
                    }
                }
            }
            else {
                for (int i = 0; i < names.length; i++) {
                    Object blk = blockbyidfunc.invoke(nmsblock, i);
                    if (blk != null) {
                        names[i] = stripBlockString(blk.toString());
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
    
    protected boolean isBlockMaterialNeeded() {
    	return true;
    }

    /**
     * Get material map by block ID
     */
    @Override
    public BukkitMaterial[] getMaterialList() {
        try {
            BukkitMaterial[] map = new BukkitMaterial[4096];
            if (blockbyid != null) {
                Object[] byid = (Object[])blockbyid.get(nmsblock);
                for (int i = 0; i < map.length; i++) {
                    if (byid[i] != null) {
                        Object mat = (Object)material.get(byid[i]);
                        if (mat != null) {
                        	Boolean solid = (Boolean) material_issolid.invoke(mat);
                        	Boolean liquid = (Boolean) material_isliquid.invoke(mat);
                        	map[i] = new BukkitMaterial(mat.toString(), solid, liquid);
                        }
                    }
                }
            }
            else if (blockbyidfunc != null) {
                ArrayList<Object> mats = new ArrayList<Object>();
                for (int i = 0; i < map.length; i++) {
                    Object blk = blockbyidfunc.invoke(nmsblock, i);
                    if (blk != null) {
                        Object mat = (Object)material.get(blk);
                        if (mat != null) {
                        	Boolean solid = (Boolean) material_issolid.invoke(mat);
                        	Boolean liquid = (Boolean) material_isliquid.invoke(mat);
                        	map[i] = new BukkitMaterial(mat.toString(), solid, liquid);
                        }
                    }
                }
            }
            return map;
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return new BukkitMaterial[0];
    }
 
    @Override
    public Polygon getWorldBorder(World world) {
        Polygon p = null;
        if ((getworldborder == null) || (world == null)) {
            return null;
        }
        Object cw = getNMSWorld(world);
        if (cw == null) return null;
        Object wb = callMethod(cw, getworldborder, nullargs, null);
        if (wb != null) {
            double minx = (Double) callMethod(wb, worldborderminx, nullargs, Double.MIN_VALUE);
            double minz = (Double) callMethod(wb, worldborderminz, nullargs, Double.MIN_VALUE);
            double maxx = (Double) callMethod(wb, worldbordermaxx, nullargs, Double.MAX_VALUE);
            double maxz = (Double) callMethod(wb, worldbordermaxz, nullargs, Double.MAX_VALUE);
            if (maxx < 1E7) {
                p = new Polygon();
                p.addVertex(minx, minz);
                p.addVertex(minx, maxz);
                p.addVertex(maxx, maxz);
                p.addVertex(maxx, minz);
            }
        }
        return p;
    }
    protected Object[] biomelist = null;
    /**
     * Get list of defined biomebase objects
     */
    public Object[] getBiomeBaseList() {
        if ((getbiomebyid != null) || (getbiomefunc != null)) {
            if (biomelist == null) {
                biomelist = new Object[1024];
                for (int i = 0; i < 1024; i++) {
                    try {
                        if (getbiomefunc != null)
                            biomelist[i] = getbiomefunc.invoke(biomebase, i, null);
                        else
                            biomelist[i] = getbiomebyid.invoke(biomebase, i);
                    } catch (IllegalAccessException x) {
                    } catch (IllegalArgumentException x) {
                    } catch (InvocationTargetException x) {
                    }
                }
            }
            return biomelist;
        }
        return super.getBiomeBaseList();
    }
    /** Get ID from biomebase */
    public int getBiomeBaseID(Object bb) {
        if (getidbybiome != null) {
            try {
                return (Integer) getidbybiome.invoke(biomebase,  bb);
            } catch (IllegalAccessException e) {
            } catch (IllegalArgumentException e) {
            } catch (InvocationTargetException e) {
            }
        }
        return super.getBiomeBaseID(bb);
    }
    /**
     * Test if broken unloadChunk
     */
    @Override
    public boolean isUnloadChunkBroken() { return isBadUnload; }
    
    @Override
	public String getStateStringByCombinedId(int blkid, int meta) {
    	int id = blkid | (meta << 12);
    	if (getbycombinedid != null) {
            try {
                Object iblockdata = getbycombinedid.invoke(nmsblock, id);
                if (iblockdata != null) {
                	String nm = iblockdata.toString();
            		int off1 = nm.indexOf('[');
            		if (off1 >= 0) {
            			int off2 = nm.indexOf(']');
            			return nm.substring(off1+1, off2);
            		}
                }
            } catch (IllegalAccessException x) {
            } catch (IllegalArgumentException x) {
            } catch (InvocationTargetException x) {
            }
    	}
    	return "meta=" + meta;
    }
}
