package org.dynmap.kzedmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.bukkit.World;
import org.dynmap.debug.Debugger;

public class DefaultTileRenderer implements MapTileRenderer {
	protected static Color translucent = new Color(0, 0, 0, 0);
	private String name;
	protected Debugger debugger;

	public String getName() {
		return name;
	}

	public DefaultTileRenderer(Debugger debugger, Map<String, Object> configuration) {
		this.debugger = debugger;
		name = (String) configuration.get("prefix");
	}

	public boolean render(KzedMapTile tile, String path) {
		World world = tile.getMap().getWorld();
		BufferedImage im = new BufferedImage(KzedMap.tileWidth, KzedMap.tileHeight, BufferedImage.TYPE_INT_RGB);

		WritableRaster r = im.getRaster();
		boolean isempty = true;

		int ix = tile.mx;
		int iy = tile.my;
		int iz = tile.mz;

		int jx, jz;

		int x, y;

		/* draw the map */
		for (y = 0; y < KzedMap.tileHeight;) {
			jx = ix;
			jz = iz;

			for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
				Color c1 = scan(world, jx, iy, jz, 0);
				Color c2 = scan(world, jx, iy, jz, 2);
				isempty = isempty && c1 == translucent && c2 == translucent;
				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x - 1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });

				jx++;
				jz++;

			}

			y++;

			jx = ix;
			jz = iz - 1;

			for (x = KzedMap.tileWidth - 1; x >= 0; x -= 2) {
				Color c1 = scan(world, jx, iy, jz, 2);
				jx++;
				jz++;
				Color c2 = scan(world, jx, iy, jz, 0);
				isempty = isempty && c1 == translucent && c2 == translucent;
				r.setPixel(x, y, new int[] { c1.getRed(), c1.getGreen(), c1.getBlue() });
				r.setPixel(x - 1, y, new int[] { c2.getRed(), c2.getGreen(), c2.getBlue() });
			}

			y++;

			ix++;
			iz--;
		}

		/* save the generated tile */
		saveTile(tile, im, path);

		((KzedMap) tile.getMap()).invalidateTile(new KzedZoomedMapTile((KzedMap)tile.getMap(), im, tile));

		return !isempty;
	}

	protected Color scan(World world, int x, int y, int z, int seq) {
		for (;;) {
			if (y < 0)
				return translucent;

			int id = world.getBlockTypeIdAt(x, y, z);

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
				Color[] colors = KzedMap.colors.get(id);
				if (colors != null) {
					Color c = colors[seq];
					if (c.getAlpha() > 0) {
						/* we found something that isn't transparent! */
						if (c.getAlpha() == 255) {
							/* it's opaque - the ray ends here */
							return c;
						}

						/* this block is transparent, so recurse */
						Color bg = scan(world, x, y, z, seq);

						int cr = c.getRed();
						int cg = c.getGreen();
						int cb = c.getBlue();
						int ca = c.getAlpha();
						cr *= ca;
						cg *= ca;
						cb *= ca;
						int na = 255 - ca;

						return new Color((bg.getRed() * na + cr) >> 8, (bg.getGreen() * na + cg) >> 8, (bg.getBlue() * na + cb) >> 8);
					}
				}
			}
		}
	}

	/* save rendered tile, update zoom-out tile */
	public void saveTile(KzedMapTile tile, BufferedImage im, String path) {
		String tilePath = getPath(tile, path);

		debugger.debug("saving tile " + tilePath);

		/* save image */
		try {
			File file = new File(tilePath);
			ImageIO.write(im, "png", file);
		} catch (IOException e) {
			debugger.error("Failed to save tile: " + tilePath, e);
		} catch (java.lang.NullPointerException e) {
			debugger.error("Failed to save tile (NullPointerException): " + tilePath, e);
		}
	}

	public static String getPath(KzedMapTile tile, String outputPath) {
		return new File(new File(outputPath), tile.getName() + ".png").getPath();
	}
}
