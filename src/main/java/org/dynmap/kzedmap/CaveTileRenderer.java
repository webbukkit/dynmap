package org.dynmap.kzedmap;

import org.bukkit.World;
import org.dynmap.MapChunkCache;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;

public class CaveTileRenderer extends DefaultTileRenderer {

    public CaveTileRenderer(ConfigurationNode configuration) {
        super(configuration);
    }

    @Override
    protected void scan(World world, int seq, boolean isnether, final Color result,
            MapChunkCache.MapIterator mapiter) {
        boolean air = true;
        result.setTransparent();
        for (;;) {
            if (mapiter.y < 0)
                return;

            int id = mapiter.getBlockTypeID();
            if(isnether) {    /* Make ceiling into air in nether */
                if(id != 0)
                    id = 0;
                else
                    isnether = false;
            }

            switch (seq) {
            case 0:
                mapiter.decrementX();
                break;
            case 1:
            case 3:
                mapiter.decrementY();
                break;
            case 2:
                mapiter.incrementZ();
                break;
            }

            seq = (seq + 1) & 3;

            switch (id) {
            case 20:
            case 18:
            case 17:
            case 78:
            case 79:
                id = 0;
                break;
            default:
            }

            if (id != 0) {
                air = false;
                continue;
            }

            if (id == 0 && !air) {
                int cr, cg, cb;
                int mult = 256;

                if (mapiter.y < 64) {
                    cr = 0;
                    cg = 64 + mapiter.y * 3;
                    cb = 255 - mapiter.y * 4;
                } else {
                    cr = (mapiter.y - 64) * 4;
                    cg = 255;
                    cb = 0;
                }

                switch (seq) {
                case 0:
                    mult = 224;
                    break;
                case 1:
                    mult = 256;
                    break;
                case 2:
                    mult = 192;
                    break;
                case 3:
                    mult = 160;
                    break;
                }

                cr = cr * mult / 256;
                cg = cg * mult / 256;
                cb = cb * mult / 256;

                result.setRGBA(cr, cg, cb, 255);
                return;
            }
        }
    }
}
