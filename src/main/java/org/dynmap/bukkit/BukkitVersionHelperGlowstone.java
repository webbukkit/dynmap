package org.dynmap.bukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class BukkitVersionHelperGlowstone extends BukkitVersionHelper {
    private Method rawbiome;
    private Method server_getonlineplayers;
    
    public BukkitVersionHelperGlowstone() {
        try {
            Class<?> c = Class.forName("net.glowstone.GlowChunkSnapshot");
            rawbiome = c.getMethod("getRawBiomes", new Class[0]);

            /** Server */
            server_getonlineplayers = Server.class.getMethod("getOnlinePlayers", new Class[0]);
        } catch (SecurityException e) {
        } catch (NoSuchMethodException e) {
        } catch (ClassNotFoundException e) {
        }
        if ((rawbiome == null) && (server_getonlineplayers == null)) {
            throw new IllegalArgumentException("Error initializing dynmap - Glowstone version incompatible!");
        }
    }
    
    @Override
    public Object[] getBiomeBaseList() {
        return new Object[0];
    }

    @Override
    public float getBiomeBaseTemperature(Object bb) {
        return 0;
    }

    @Override
    public float getBiomeBaseHumidity(Object bb) {
        return 0;
    }

    @Override
    public String getBiomeBaseIDString(Object bb) {
        return "";
    }

    @Override
    public int getBiomeBaseID(Object bb) {
        if (bb != null) 
            return (Integer) bb;
        else
            return 0;
    }

    @Override
    public Object getUnloadQueue(World world) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isInUnloadQueue(Object unloadqueue, int x, int z) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Object[] getBiomeBaseFromSnapshot(ChunkSnapshot css) {
        Integer b[] = new Integer[256];
        byte[] rb = null;
        try {
            rb = (byte[]) rawbiome.invoke(css, new Object[0]);
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        if (rb != null) {
            for (int i = 0; i < 256; i++) {
                b[i] = Integer.valueOf(255 & (int)rb[i]);
            }
        }
        return b;
    }

    @Override
    public long getInhabitedTicks(Chunk c) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Map<?, ?> getTileEntitiesForChunk(Chunk c) {
        // TODO Auto-generated method stub
        return Collections.emptyMap();
    }

    @Override
    public int getTileEntityX(Object te) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getTileEntityY(Object te) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getTileEntityZ(Object te) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Object readTileEntityNBT(Object te) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getFieldValue(Object nbt, String field) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void unloadChunkNoSave(World w, Chunk c, int cx, int cz) {
        // TODO Auto-generated method stub

    }

    @Override
    public String[] getBlockShortNames() {
        // TODO Auto-generated method stub
        return null;
    }

    private static final String[] bnames = {
       "Ocean",
       "Plains",
       "Desert",
       "Extreme Hills",
       "Forest",
       "Taiga",
       "Swampland",
       "River",
       "Hell",
       "Sky",
       "FrozenOcean",
       "FrozenRiver",
       "Ice Plains",
       "Ice Mountains",
       "MushroomIsland",
       "MushroomIslandShore",
       "Beach",
       "DesertHills",
       "ForestHills",
       "TaigaHills",
       "Extreme Hills Edge",
       "Jungle",
       "JungleHills",
       "JungleEdge",
       "Deep Ocean",
       "Stone Beach",
       "Cold Beach",
       "Birch Forest",
       "Birch Forest Hills",
       "Roofed Forest",
       "Cold Taiga",
       "Cold Taiga Hills",
       "Mega Taiga",
       "Mega Taiga Hills",
       "Extreme Hills+",
       "Savanna",
       "Savanna Plateau",
       "Mesa",
       "Mesa Plateau F",
       "Mesa Plateau",
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       "Sunflower Plains",
       "Desert M",
       "Extreme Hills M",
       "Flower Forest",
       "Taiga M",
       "Swampland M",
       null,
       null,
       null,
       null,
       null,
       "Ice Plains Spikes",
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       "Jungle M",
       null,
       "JungleEdge M",
       null,
       null,
       null,
       "Birch Forest M",
       "Birch Forest Hills M",
       "Roofed Forest M",
       "Cold Taiga M",
       null,
       "Mega Spruce Taiga",
       "Mega Spruce Taiga",
       "Extreme Hills+ M",
       "Savanna M",
       "Savanna Plateau M",
       "Mesa (Bryce)",
       "Mesa Plateau F M",
       "Mesa Plateau M",
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null,
       null
    };

    @Override
    public String[] getBiomeNames() {
        return bnames;
    }

    @Override
    public int[] getBlockMaterialMap() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Get list of online players
     */
    public Player[] getOnlinePlayers() {
        Object players;
        try {
            players = server_getonlineplayers.invoke(Bukkit.getServer(), new Object[0]);
            if (players instanceof Player[]) {  /* Pre 1.7.10 */
                return (Player[]) players;
            }
            else {
                @SuppressWarnings("unchecked")
                Collection<? extends Player> p = (Collection<? extends Player>) players;
                return p.toArray(new Player[0]);
            }
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        }
        return new Player[0];
    }

    @Override
    public double getHealth(Player p) {
        return p.getHealth();
    }

}
