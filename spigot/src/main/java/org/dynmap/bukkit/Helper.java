package org.dynmap.bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.dynmap.Log;
import org.dynmap.bukkit.helper.BukkitVersionHelper;

public class Helper {

	private static BukkitVersionHelper loadVersionHelper(String classname) {
		try {
			Class<?> c = Class.forName(classname);
			Constructor<?> cons = c.getConstructor();
			return (BukkitVersionHelper) cons.newInstance();
		} catch (Exception x) {
			Log.severe("Error loading " + classname, x);
			return null;
		}
	}

	// Server#getMinecraftVersion() was added long after the ancient Bukkit API (1.10.2) that this
	// module compiles against, so it isn't a compile-time symbol here - called via reflection instead.
	private static String getMinecraftVersionReflective() {
		try {
			Method m = Bukkit.getServer().getClass().getMethod("getMinecraftVersion");
			return (String) m.invoke(Bukkit.getServer());
		} catch (Exception x) {
			Log.severe("Error calling Server#getMinecraftVersion()", x);
			return null;
		}
	}
    public static final BukkitVersionHelper getHelper() {
        if (BukkitVersionHelper.helper == null) {
        	String v = Bukkit.getServer().getVersion();
            Log.info("version=" + v);
            if (v.contains("MCPC")) {
                Log.severe("*********************************************************************************");
                Log.severe("* MCPC-Plus is no longer supported via the Bukkit version of Dynmap.            *");
                Log.severe("* Install the appropriate Forge version of Dynmap.                              *");
                Log.severe("* Add the DynmapCBBridge plugin to enable support for Dynmap-compatible plugins *");
                Log.severe("*********************************************************************************");
            }
            else if(v.contains("BukkitForge")) {
                Log.severe("*********************************************************************************");
                Log.severe("* BukkitForge is not supported via the Bukkit version of Dynmap.                *");
                Log.severe("* Install the appropriate Forge version of Dynmap.                              *");
                Log.severe("* Add the DynmapCBBridge plugin to enable support for Dynmap-compatible plugins *");
                Log.severe("*********************************************************************************");
            }
            else if(Bukkit.getServer().getClass().getName().contains("GlowServer")) {
                Log.info("Loading Glowstone support");
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.BukkitVersionHelperGlowstone");
            }
            else if (v.contains("(MC: 1.21)") || v.contains("(MC: 1.21.1)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121.BukkitVersionHelperSpigot121");
            }
            else if (v.contains("(MC: 1.21.2)") || v.contains("(MC: 1.21.3)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_3.BukkitVersionHelperSpigot121_3");
            }
            else if (v.contains("(MC: 1.21.4)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_4.BukkitVersionHelperSpigot121_4");
            }
            else if (v.contains("(MC: 1.21.5)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_5.BukkitVersionHelperSpigot121_5");
            }
            else if (v.contains("(MC: 1.21.6") || v.contains("(MC: 1.21.7") || v.contains("(MC: 1.21.8")) {
	            BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_6.BukkitVersionHelperSpigot121_6");
            }
            else if (v.contains("(MC: 1.21.9)") || v.contains("(MC: 1.21.10)")) {
	            BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_10.BukkitVersionHelperSpigot121_10");
            }
            else if (v.contains("(MC: 1.21.")) {	// Set up in case 1.21.12 works 'as is'
	            BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v121_11.BukkitVersionHelperSpigot121_11");
            }
            else if (v.contains("(MC: 1.20)") || v.contains("(MC: 1.20.1)")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120.BukkitVersionHelperSpigot120");
            }
            else if (v.contains("(MC: 1.20.2)")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120_2.BukkitVersionHelperSpigot120_2");
            }
            else if (v.contains("(MC: 1.20.3)") || v.contains("(MC: 1.20.4)")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120_4.BukkitVersionHelperSpigot120_4");
            }
            else if (v.contains("(MC: 1.20.")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120_5.BukkitVersionHelperSpigot120_5");
            }
            else if (v.contains("(MC: 1.19)") || v.contains("(MC: 1.19.1)") || v.contains("(MC: 1.19.2)")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119.BukkitVersionHelperSpigot119");
            }
            else if (v.contains("(MC: 1.19.3)")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119_3.BukkitVersionHelperSpigot119_3");
            }
            else if (v.contains("(MC: 1.19.")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119_4.BukkitVersionHelperSpigot119_4");
            }
            else if (v.contains("(MC: 1.18)") || (v.contains("(MC: 1.18.1)"))) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v118.BukkitVersionHelperSpigot118");
            }
            else if (v.contains("(MC: 1.18")) {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v118_2.BukkitVersionHelperSpigot118_2");
            }
            else if (v.contains("(MC: 1.17")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v117.BukkitVersionHelperSpigot117");
            }
            else if (v.contains("(MC: 1.16.1")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116.BukkitVersionHelperSpigot116");
            }
            else if (v.contains("(MC: 1.16.2)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_2.BukkitVersionHelperSpigot116_2");
            }
            else if (v.contains("(MC: 1.16.3)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_3.BukkitVersionHelperSpigot116_3");
            }
            else if (v.contains("(MC: 1.16.")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_4.BukkitVersionHelperSpigot116_4");
            }
            // Loading last to prevent the 1.16 contains to match all newer versions and load older helper incorrectly.
            else if (v.contains("(MC: 1.16")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116.BukkitVersionHelperSpigot116");
            }
            else if (v.contains("(MC: 1.15)") || v.contains("(MC: 1.15.")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v115.BukkitVersionHelperSpigot115");
            }
            else if (v.contains("(MC: 1.14)") || v.contains("(MC: 1.14.1)") || v.contains("(MC: 1.14.2)") ||
                v.contains("(MC: 1.14.3)") ||  v.contains("(MC: 1.14.4)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v114_1.BukkitVersionHelperSpigot114_1");
            }
            else if (v.contains("(MC: 1.13.2)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v113_2.BukkitVersionHelperSpigot113_2");
            }
            // Minecraft 26.1+ switched to year.release version numbers - the "(MC: x)" substring is
            // still present (confirmed on a real 26.2 server: "26.2-62-... (MC: 26.2)"), just with
            // the new-style version string inside it instead of the old "1.x.y" one.
            else if (v.contains("(MC: 26.2)")) {
                BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v26_2.BukkitVersionHelperSpigot26_2");
            }
            // Fallback in case some future server reports the Minecraft version differently (e.g. no
            // "(MC: x)" substring at all) - try the dedicated accessor before giving up.
            else if (!v.contains("(MC:")) {
            	String mcver = getMinecraftVersionReflective();
            	if ("26.2".equals(mcver)) {
            		BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v26_2.BukkitVersionHelperSpigot26_2");
            	}
            	else {
            		Log.severe("Unsupported Minecraft version (" + mcver + ") - Dynmap does not yet have a bukkit-helper for this version.");
            	}
            }
            else {
            	BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.BukkitVersionHelperCB");
            }
        }
        return BukkitVersionHelper.helper;
    }

}
