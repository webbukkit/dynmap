package org.dynmap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.dynmap.utils.MapChunkCache;

public abstract class MapTile {
    protected DynmapWorld world;

    public abstract boolean render(MapChunkCache cache, String mapname);
    public abstract List<DynmapChunk> getRequiredChunks();
    public abstract MapTile[] getAdjecentTiles();

    public DynmapWorld getDynmapWorld() {
        return world;
    }

    public MapTile(DynmapWorld world) {
        this.world = world;
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);
    
    public abstract boolean isBiomeDataNeeded();
    public abstract boolean isHightestBlockYDataNeeded();
    public abstract boolean isRawBiomeDataNeeded();
    public abstract boolean isBlockTypeDataNeeded();

    public abstract int tileOrdinalX();
    public abstract int tileOrdinalY();
    
    public ConfigurationNode saveTile() {
        ConfigurationNode cn = new ConfigurationNode();
        cn.put("class", this.getClass().getName());
        cn.put("data", saveTileData());
        return cn;
    }
    
    protected abstract String saveTileData();
    
    public static MapTile restoreTile(DynmapWorld w, ConfigurationNode node) {
        String cn = node.getString("class");
        String dat = node.getString("data");
        if((cn == null) || (dat == null)) return null;
        try {
            Class<?> cls = Class.forName(cn);
            Constructor<?> con = cls.getConstructor(DynmapWorld.class, String.class);
            return (MapTile)con.newInstance(w, dat);
        } catch (ClassNotFoundException cnfx) {
        } catch (NoSuchMethodException nsmx) {
        } catch (InvocationTargetException itx) {
        } catch (IllegalAccessException iax) {
        } catch (InstantiationException ix) {
        }
        return null;
    }
}
