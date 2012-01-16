package org.dynmap.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.block.Block;
import org.dynmap.Log;

/**
 * Wrapper for accessing raw light levels for given block
 */
public class BlockLightLevel {
    private Method gethandle;
    private Method getrawlight;
    private Object enum_sky;
    private Object enum_block;
    private boolean ready;
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public BlockLightLevel() {
        /* Get CraftChunk.getChunkSnapshot(boolean,boolean,boolean) and CraftChunk.getHandle() */
        try {
            Class c = Class.forName("org.bukkit.craftbukkit.CraftChunk");
            gethandle = c.getDeclaredMethod("getHandle", new Class[0]);
            Class enumskyblock = Class.forName("net.minecraft.server.EnumSkyBlock");
            Object[] enumvals = enumskyblock.getEnumConstants();
            for(int i = 0; i < enumvals.length; i++) {
                String ev = enumvals[i].toString();
                if(ev.equals("Sky")) {
                    enum_sky = enumvals[i]; 
                }
                else if(ev.equals("Block")) {
                    enum_block = enumvals[i];
                }
            }
            Class cc = Class.forName("net.minecraft.server.Chunk");
            getrawlight = cc.getDeclaredMethod("a", new Class[] { enumskyblock, int.class, int.class, int.class });
        } catch (ClassNotFoundException cnfx) {
        } catch (NoSuchMethodException nsmx) {
        }
        if((gethandle != null) && (enum_sky != null) && (enum_block != null) && (getrawlight != null)) {
            ready = true;
        }
        else {
            Log.warning("Block raw light level API not available");
        }
    }
    
    public boolean isReady() {
        return ready;
    }

    public int getSkyLightLevel(Block b) {
        try {
            Object hand = gethandle.invoke(b.getChunk());
            if(hand != null) {
                Integer v = (Integer)getrawlight.invoke(hand, enum_sky, b.getX() & 0xF, b.getY() & 0x7F, b.getZ() & 0xF);
                return v;
            }
        } catch (InvocationTargetException itx) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }
        return -1;
    }

    public int getBlockLightLevel(Block b) {
        try {
            Object hand = gethandle.invoke(b.getChunk());
            if(hand != null) {
                Integer v = (Integer)getrawlight.invoke(hand, enum_block, b.getX() & 0xF, b.getY() & 0x7F, b.getZ() & 0xF);
                return v;
            }
        } catch (InvocationTargetException itx) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }
        return -1;
    }
}
