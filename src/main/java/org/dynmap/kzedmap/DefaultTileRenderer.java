package org.dynmap.kzedmap;

import org.dynmap.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.dynmap.Client;
import org.dynmap.ColorScheme;
import org.dynmap.MapManager;
import org.dynmap.MapTile;
import org.dynmap.debug.Debug;

public class DefaultTileRenderer implements MapTileRenderer {
    protected static final Color translucent = new Color(0, 0, 0, 0);
    protected String name;
    protected int maximumHeight = 127;
    protected ColorScheme colorScheme;
    
    protected HashSet<Integer> highlightBlocks = new HashSet<Integer>();
    protected Color highlightColor = new Color(255, 0, 0);
    
    @Override
    public String getName() {
        return name;
    }

    public DefaultTileRenderer(Map<String, Object> configuration) {
        name = (String) configuration.get("prefix");
        Object o = configuration.get("maximumheight");
        if (o != null) {
            maximumHeight = Integer.parseInt(String.valueOf(o));
            if (maximumHeight > 127)
                maximumHeight = 127;
        }
        colorScheme = ColorScheme.getScheme((String)configuration.get("colorscheme"));
    }

    public boolean render(KzedMapTile tile, File outputFile) {
        World world = tile.getWorld();
        boolean isnether = (world.getEnvironment() == Environment.NETHER);
        BufferedImage im = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);

        WritableRaster r = im.getRaster();
        boolean isempty = true;

        int ix = KzedMap.anchorx + tile.px / 2 + tile.py / 2 - ((127-maximumHeight)/2);
        int iy = maximumHeight;
        int iz = KzedMap.anchorz + tile.px / 2 - tile.py / 2 + ((127-maximumHeight)/2);

        /* Don't mess with existing height-clipped renders */
        if(maximumHeight < 127)
        	isnether = false;
        
        int jx, jz;
        
        int x, y;

        Color c1 = new Color();
        Color c2 = new Color();
        int[] rgb = new int[3];
        /* draw the map */
        for (y = 0; y < KzedMap.tileHeight;) {
            jx = ix;
            jz = iz;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                scan(world, jx, iy, jz, 0, isnether, c1);
                scan(world, jx, iy, jz, 2, isnether, c2);
                if(c1.isTransparent() == false) {
                    rgb[0] = c1.getRed(); rgb[1] = c1.getGreen(); rgb[2] = c1.getBlue();
                    r.setPixel(x, y, rgb);
                    isempty = false;
                }
                if(c2.isTransparent() == false) {
                    rgb[0] = c2.getRed(); rgb[1] = c2.getGreen(); rgb[2] = c2.getBlue();
                    r.setPixel(x - 1, y, rgb);
                    isempty = false;
                }
                
                jx++;
                jz++;

            }

            y++;

            jx = ix;
            jz = iz - 1;

            for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
                scan(world, jx, iy, jz, 2, isnether, c1);
                jx++;
                jz++;
                scan(world, jx, iy, jz, 0, isnether, c2);
                if(c1.isTransparent() == false) {
                    rgb[0] = c1.getRed(); rgb[1] = c1.getGreen(); rgb[2] = c1.getBlue();
                    r.setPixel(x, y, rgb);
                    isempty = false;
                }
                if(c2.isTransparent() == false) {
                    rgb[0] = c2.getRed(); rgb[1] = c2.getGreen(); rgb[2] = c2.getBlue();

                    r.setPixel(x - 1, y, rgb);
                    isempty = false;
                }
            }

            y++;

            ix++;
            iz--;
        }

        /* Hand encoding and writing file off to MapManager */
        final File fname = outputFile;
        final KzedMapTile mtile = tile;
        final BufferedImage img = im;
		final KzedZoomedMapTile zmtile = new KzedZoomedMapTile(mtile.getWorld(), 
				(KzedMap) mtile.getMap(), mtile);
		final File zoomFile = MapManager.mapman.getTileFile(zmtile);
		
		MapManager.mapman.enqueueImageWrite(new Runnable() {
        	public void run() {
        	    doFileWrites(fname, mtile, img, zmtile, zoomFile);
        	}
        });        

        return !isempty;
    }
    
    private void doFileWrites(final File fname, final KzedMapTile mtile,
    	final BufferedImage img, final KzedZoomedMapTile zmtile, final File zoomFile) {
		Debug.debug("saving image " + fname.getPath());        
		try {
			ImageIO.write(img, "png", fname);
		} catch (IOException e) {
			Debug.error("Failed to save image: " + fname.getPath(), e);
		} catch (java.lang.NullPointerException e) {
			Debug.error("Failed to save image (NullPointerException): " + fname.getPath(), e);
		}
		mtile.file = fname;
		// Since we've already got the new tile, and we're on an async thread, just
		// make the zoomed tile here
		int px = mtile.px;
		int py = mtile.py;
		int zpx = zmtile.getTileX();
		int zpy = zmtile.getTileY();

		/* scaled size */
		int scw = KzedMap.tileWidth / 2;
		int sch = KzedMap.tileHeight / 2;

		/* origin in zoomed-out tile */
		int ox = 0;
		int oy = 0;

		if (zpx != px)
			ox = scw;
		if (zpy != py)
			oy = sch;

		BufferedImage zIm = null;
		try {
			zIm = ImageIO.read(zoomFile);
		} catch (IOException e) {
		} catch (IndexOutOfBoundsException e) {
		}

		if (zIm == null) {
			/* create new one */
			zIm = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);
			Debug.debug("New zoom-out tile created " + zmtile.getFilename());
		} else {
			Debug.debug("Loaded zoom-out tile from " + zmtile.getFilename());
		}
		
		/* blit scaled rendered tile onto zoom-out tile */
		Graphics2D g2 = zIm.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(img, ox, oy, scw, sch, null);

		img.flush();

		/* save zoom-out tile */
		
		try {
			ImageIO.write(zIm, "png", zoomFile);
			Debug.debug("Saved zoom-out tile at " + zoomFile.getName());
		} catch (IOException e) {
			Debug.error("Failed to save zoom-out tile: " + zoomFile.getName(), e);
		} catch (java.lang.NullPointerException e) {
			Debug.error("Failed to save zoom-out tile (NullPointerException): " + zoomFile.getName(), e);
		}
		zIm.flush();
		/* Push updates for both files.*/
		MapManager.mapman.pushUpdate(mtile.getWorld(), 
			new Client.Tile(mtile.getFilename()));        		
		MapManager.mapman.pushUpdate(zmtile.getWorld(), 
				new Client.Tile(zmtile.getFilename()));        		    	
    }
    

    protected void scan(World world, int x, int y, int z, int seq, boolean isnether, final Color result) {
        result.setTransparent();
        for (;;) {
            if (y < 0) {
                return;
            }
            int id = world.getBlockTypeIdAt(x, y, z);
            byte data = 0;
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
                if (highlightBlocks.contains(id)) {
                    result.setColor(highlightColor);
                    return;
                }
                Color[] colors;
                if(data != 0)
                	colors = colorScheme.datacolors[id][data];
                else
                	colors = colorScheme.colors[id];
                if (colors != null) {
                    Color c = colors[seq];
                    if (c.getAlpha() > 0) {
                        /* we found something that isn't transparent! */
                        if (c.getAlpha() == 255) {
                            /* it's opaque - the ray ends here */
                            result.setColor(c);
                            return;
                        }

                        /* this block is transparent, so recurse */
                        scan(world, x, y, z, seq, isnether, result);

                        int cr = c.getRed();
                        int cg = c.getGreen();
                        int cb = c.getBlue();
                        int ca = c.getAlpha();
                        cr *= ca;
                        cg *= ca;
                        cb *= ca;
                        int na = 255 - ca;
                        result.setRGBA((result.getRed() * na + cr) >> 8, (result.getGreen() * na + cg) >> 8, (result.getBlue() * na + cb) >> 8, 255);
                        return;
                    }
                }
            }
        }
    }
}
