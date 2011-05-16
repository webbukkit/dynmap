package org.dynmap.kzedmap;

import org.dynmap.Color;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bukkit.World;

public class HighlightTileRenderer extends DefaultTileRenderer {
    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();

    public HighlightTileRenderer(Map<String, Object> configuration) {
        super(configuration);
        Object highlightObj = configuration.get("highlight");
        if (highlightObj instanceof List<?>) {
            for(Object o : (List<?>)highlightObj) {
                highlightBlocks.add((Integer)o);
            }
        } else if (highlightObj instanceof Integer) {
            highlightBlocks.add((Integer)highlightObj);
        }
    }
    
    @Override
    protected void scan(World world, int x, int y, int z, int seq, boolean isnether, final Color result) {
        result.setTransparent();
    	int top_nether_id = 0;
        for (;;) {
            if (y < 0) {
                break;
            }

            int id = world.getBlockTypeIdAt(x, y, z);
            if(isnether) {	/* Make bedrock ceiling into air in nether */
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
            byte data = 0;
            if(colorScheme.datacolors[id] != null) {	/* If data colored */
            	data = world.getBlockAt(x, y, z).getData();
            }

            switch (seq) {
            case 0:
                x--;
                break;
            case 1:
                y--;
                break;
            case 2:
                z++;
                break;
            case 3:
                y--;
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
