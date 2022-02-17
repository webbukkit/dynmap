package org.dynmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Scanner;

import org.dynmap.common.BiomeMap;
import org.dynmap.debug.Debug;
import org.dynmap.renderer.DynmapBlockState;

public class ColorScheme {
	private static final HashMap<String, ColorScheme> cache = new HashMap<String, ColorScheme>();

	public String name;
	/* Switch to arrays - faster than map */
	public final Color[][] colors; /* [global-state-idx][step] */
	public final Color[][] biomecolors; /* [Biome.ordinal][step] */
	public final Color[][] raincolors; /* [rain * 63][step] */
	public final Color[][] tempcolors; /* [temp * 63][step] */

	public ColorScheme(String name, Color[][] colors, Color[][] biomecolors, Color[][] raincolors,
			Color[][] tempcolors) {
		this.name = name;
		this.colors = colors;
		this.biomecolors = biomecolors;
		this.raincolors = raincolors;
		this.tempcolors = tempcolors;
	}

	private static File getColorSchemeDirectory(DynmapCore core) {
		return new File(core.getDataFolder(), "colorschemes");
	}

	public static ColorScheme getScheme(DynmapCore core, String name) {
		if (name == null)
			name = "default";
		ColorScheme scheme = cache.get(name);
		if (scheme == null) {
			scheme = loadScheme(core, name);
			cache.put(name, scheme);
		}
		return scheme;
	}

	public static ColorScheme loadScheme(DynmapCore core, String name) {
		File colorSchemeFile = new File(getColorSchemeDirectory(core), name + ".txt");
		Color[][] colors = new Color[DynmapBlockState.getGlobalIndexMax()][];
		Color[][] biomecolors = new Color[BiomeMap.values().length][];
		Color[][] raincolors = new Color[64][];
		Color[][] tempcolors = new Color[64][];

		/* Default the biome color */
		for (int i = 0; i < biomecolors.length; i++) {
			Color[] c = new Color[5];
			int red = 0x80 | (0x40 * ((i >> 0) & 1)) | (0x20 * ((i >> 3) & 1)) | (0x10 * ((i >> 6) & 1));
			int green = 0x80 | (0x40 * ((i >> 1) & 1)) | (0x20 * ((i >> 4) & 1)) | (0x10 * ((i >> 7) & 1));
			int blue = 0x80 | (0x40 * ((i >> 2) & 1)) | (0x20 * ((i >> 5) & 1));
			c[0] = new Color(red, green, blue);
			c[3] = new Color(red * 4 / 5, green * 4 / 5, blue * 4 / 5);
			c[1] = new Color(red / 2, green / 2, blue / 2);
			c[2] = new Color(red * 2 / 5, green * 2 / 5, blue * 2 / 5);
			c[4] = new Color((c[1].getRed() + c[3].getRed()) / 2, (c[1].getGreen() + c[3].getGreen()) / 2,
					(c[1].getBlue() + c[3].getBlue()) / 2, (c[1].getAlpha() + c[3].getAlpha()) / 2);

			biomecolors[i] = c;
		}

		InputStream stream;
		// Get defaults from biome_rainfall_temp.txt - let custom file override after
		File files[] = {
			new File(getColorSchemeDirectory(core), "biome_rainfall_temp.txt"), 
			new File(getColorSchemeDirectory(core), "default.txt"), 
			colorSchemeFile };
		try {
			for (int fidx = 0; fidx < files.length; fidx++) {
				Debug.debug("Loading colors from '" + files[fidx] + "' for " + name + "...");
				stream = new FileInputStream(files[fidx]);

				Scanner scanner = new Scanner(stream);
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine();
					if (line.startsWith("#") || line.equals("")) {
						continue;
					}
					/* Make parser less pedantic - tabs or spaces should be fine */
					String[] split = line.split("[\t ]");
					int cnt = 0;
					for (String s : split) {
						if (s.length() > 0)
							cnt++;
					}
					String[] nsplit = new String[cnt];
					cnt = 0;
					for (String s : split) {
						if (s.length() > 0) {
							nsplit[cnt] = s;
							cnt++;
						}
					}
					split = nsplit;
					if (split.length < 17) {
						continue;
					}
					Integer id = null;
					boolean isbiome = false;
					boolean istemp = false;
					boolean israin = false;
					DynmapBlockState state = null;
					int idx = split[0].indexOf(':');
					if (idx > 0) { /* ID:data - data color OR blockstate - data color */
						String[] vsplit = split[0].split("[\\[\\]]");
						// Log.info(String.format("split[0]=%s,vsplit[0]=%s,vsplit[1]=%s", split[0],
						// vsplit[0], vsplit.length > 1 ? vsplit[1] : ""));
						if (vsplit.length > 1) {
							state = DynmapBlockState.getStateByNameAndState(vsplit[0], vsplit[1]);
						} else {
							state = DynmapBlockState.getBaseStateByName(vsplit[0]);
						}
					} else if (split[0].charAt(0) == '[') { /* Biome color data */
						String bio = split[0].substring(1);
						idx = bio.indexOf(']');
						if (idx >= 0)
							bio = bio.substring(0, idx);
						isbiome = true;
						id = -1;
						BiomeMap[] bm = BiomeMap.values();
						for (int i = 0; i < bm.length; i++) {
							if (bm[i].getId().equalsIgnoreCase(bio)) {
								id = i;
								break;
							} else if (bio.equalsIgnoreCase("BIOME_" + i)) {
								id = i;
								break;
							}
						}
						if (id < 0) { /* Not biome - check for rain or temp */
							if (bio.startsWith("RAINFALL-")) {
								try {
									double v = Double.parseDouble(bio.substring(9));
									if ((v >= 0) && (v <= 1.00)) {
										id = (int) (v * 63.0);
										israin = true;
									}
								} catch (NumberFormatException nfx) {
								}
							} else if (bio.startsWith("TEMPERATURE-")) {
								try {
									double v = Double.parseDouble(bio.substring(12));
									if ((v >= 0) && (v <= 1.00)) {
										id = (int) (v * 63.0);
										istemp = true;
									}
								} catch (NumberFormatException nfx) {
								}
							}
						}
					} else {
						id = Integer.parseInt(split[0]);
						state = DynmapBlockState.getStateByLegacyBlockID(id);
					}

					Color[] c = new Color[5];

					/* store colors by raycast sequence number */
					c[0] = new Color(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]),
							Integer.parseInt(split[4]));
					c[3] = new Color(Integer.parseInt(split[5]), Integer.parseInt(split[6]), Integer.parseInt(split[7]),
							Integer.parseInt(split[8]));
					c[1] = new Color(Integer.parseInt(split[9]), Integer.parseInt(split[10]),
							Integer.parseInt(split[11]), Integer.parseInt(split[12]));
					c[2] = new Color(Integer.parseInt(split[13]), Integer.parseInt(split[14]),
							Integer.parseInt(split[15]), Integer.parseInt(split[16]));
					/* Blended color - for 'smooth' option on flat map */
					c[4] = new Color((c[1].getRed() + c[3].getRed()) / 2, (c[1].getGreen() + c[3].getGreen()) / 2,
							(c[1].getBlue() + c[3].getBlue()) / 2, (c[1].getAlpha() + c[3].getAlpha()) / 2);

					if (isbiome) {
						if (istemp) {
							tempcolors[id] = c;
						} else if (israin) {
							raincolors[id] = c;
						} else if ((id >= 0) && (id < biomecolors.length))
							biomecolors[id] = c;
					} else if (state != null) {
						int stateid = state.globalStateIndex;
						colors[stateid] = c;
					}
				}
				scanner.close();
			}
			/* Last, push base color into any open slots in data colors list */
			for (int i = 0; i < colors.length; i++) {
				if (colors[i] == null) {
					DynmapBlockState bs = DynmapBlockState.getStateByGlobalIndex(i); // Get state
					DynmapBlockState bsbase = bs.baseState;
					if ((bsbase != null) && (colors[bsbase.globalStateIndex] != null)) {
						colors[i] = colors[bsbase.globalStateIndex];
					}
				}
			}
			/* And interpolate any missing rain and temperature colors */
			interpolateColorTable(tempcolors);
			interpolateColorTable(raincolors);
		} catch (RuntimeException e) {
			Log.severe("Could not load colors '" + name + "' ('" + colorSchemeFile + "').", e);
			return null;
		} catch (FileNotFoundException e) {
			Log.severe("Could not load colors '" + name + "' ('" + colorSchemeFile + "'): File not found.", e);
		}
		return new ColorScheme(name, colors, biomecolors, raincolors, tempcolors);
	}

	public static void interpolateColorTable(Color[][] c) {
		int idx = -1;
		for (int k = 0; k < c.length; k++) {
			if (c[k] == null) { /* Missing? */
				if ((idx >= 0) && (k == (c.length - 1))) { /* We're last - so fill forward from last color */
					for (int kk = idx + 1; kk <= k; kk++) {
						c[kk] = c[idx];
					}
				}
				/* Skip - will backfill when we find next color */
			} else if (idx == -1) { /* No previous color, just backfill this color */
				for (int kk = 0; kk < k; kk++) {
					c[kk] = c[k];
				}
				idx = k; /* This is now last defined color */
			} else { /* Else, interpolate between last idx and this one */
				int cnt = c[k].length;
				for (int kk = idx + 1; kk < k; kk++) {
					double interp = (double) (kk - idx) / (double) (k - idx);
					Color[] cc = new Color[cnt];
					for (int jj = 0; jj < cnt; jj++) {
						cc[jj] = new Color((int) ((1.0 - interp) * c[idx][jj].getRed() + interp * c[k][jj].getRed()),
								(int) ((1.0 - interp) * c[idx][jj].getGreen() + interp * c[k][jj].getGreen()),
								(int) ((1.0 - interp) * c[idx][jj].getBlue() + interp * c[k][jj].getBlue()),
								(int) ((1.0 - interp) * c[idx][jj].getAlpha() + interp * c[k][jj].getAlpha()));
					}
					c[kk] = cc;
				}
				idx = k;
			}
		}
	}

	public Color[] getRainColor(double rain) {
		int idx = (int) (rain * 63.0);
		if ((idx >= 0) && (idx < raincolors.length))
			return raincolors[idx];
		else
			return null;
	}

	public Color[] getTempColor(double temp) {
		int idx = (int) (temp * 63.0);
		if ((idx >= 0) && (idx < tempcolors.length))
			return tempcolors[idx];
		else
			return null;
	}

	public static void reset() {
		cache.clear();
	}
}
