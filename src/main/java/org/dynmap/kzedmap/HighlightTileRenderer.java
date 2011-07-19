package org.dynmap.kzedmap;

import java.util.HashSet;
import java.util.List;

import org.bukkit.World;
import org.dynmap.Color;
import org.dynmap.ConfigurationNode;
import org.dynmap.utils.MapIterator;
import org.dynmap.utils.MapIterator.BlockStep;

public class HighlightTileRenderer extends DefaultTileRenderer {
    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();

    public HighlightTileRenderer(ConfigurationNode configuration) {
        super(configuration);
        List<Integer> highlight = configuration.<Integer>getList("highlight");
        for(Integer i : highlight) {
            highlightBlocks.add(i);
        }
    }

    @Override
    protected void scan(World world,int seq, boolean isnether, final Color result, final Color result_day,
            MapIterator mapiter) {
        result.setTransparent();
        for (;;) {
            if (mapiter.getY() < 0) {
                break;
            }

            int id = mapiter.getBlockTypeID();
            if(isnether) {    /* Make bedrock ceiling into air in nether */
                if(id != 0) {
                    /* Remember first color we see, in case we wind up solid */
                    if(result.isTransparent())
                        if(colorScheme.colors[id] != null)
                            result.setColor(colorScheme.colors[id][seq]);
                    id = 0;
                }
                else
                    isnether = false;
            }
            int data = 0;
            if(colorScheme.datacolors[id] != null) {    /* If data colored */
                data = mapiter.getBlockData();
            }

            switch (seq) {
                case 0:
                    mapiter.stepPosition(BlockStep.X_MINUS);
                    break;
                case 1:
                case 3:
                    mapiter.stepPosition(BlockStep.Y_MINUS);
                    break;
                case 2:
                    mapiter.stepPosition(BlockStep.Z_PLUS);
                    break;
            }

            seq = (seq + 1) & 3;

            if (id != 0) {
                Color[] colors;
                if(data != 0)
                    colors = colorScheme.datacolors[id][data];
                else
                    colors = colorScheme.colors[id];
                if (colors != null) {
                    Color c = colors[seq];

                    if (highlightBlocks.contains(id)) {
                        result.setColor(c);
                        return;
                    }

                    if (c.getAlpha() > 0) {

                        /* we found something that isn't transparent! */
                        /*
                         * if (c.getAlpha() == 255) { return c; }
                         */
                        /* this block is transparent, so recurse */

                        // No need to blend if result is opaque.
                        if (result.getAlpha() < 255) {
                            int cr = result.getRed();
                            int cg = result.getGreen();
                            int cb = result.getBlue();
                            int ca = result.getAlpha();
                            cr *= ca;
                            cg *= ca;
                            cb *= ca;
                            int na = 255 - ca;

                            result.setRGBA((c.getRed() * na + cr) >> 8, (c.getGreen() * na + cg) >> 8, (c.getBlue() * na + cb) >> 8,
                                Math.min(255, c.getAlpha()+ca) // Not really correct, but gets the job done without recursion while still looking ok.
                                );
                        }
                    }
                }
            }
        }
    }
}
