package org.dynmap.bukkit;

import java.lang.reflect.Constructor;

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

    private static int getDataVersion() {
        try {
            return Bukkit.getUnsafe().getDataVersion();
        } catch (NoSuchMethodError e) {
            return 0;
        }
    }

    public static BukkitVersionHelper getHelper() {
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
            switch (getDataVersion()) {
                case 3578: // 1.20.2
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120_2.BukkitVersionHelperSpigot120_2");
                    break;
                case 3465: // 1.20.1
                case 3463: // 1.20
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v120.BukkitVersionHelperSpigot120");
                    break;
                case 3337: // 1.19.4
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119_4.BukkitVersionHelperSpigot119_4");
                    break;
                case 3218: // 1.19.3
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119_3.BukkitVersionHelperSpigot119_3");
                    break;
                case 3120: // 1.19.2
                case 3117: // 1.19.1
                case 3105: // 1.19
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v119.BukkitVersionHelperSpigot119");
                    break;
                case 2975: // 1.18.2
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v118_2.BukkitVersionHelperSpigot118_2");
                    break;
                case 2865: // 1.18.1
                case 2860: // 1.18
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v118.BukkitVersionHelperSpigot118");
                    break;
                case 2730: // 1.17.1
                case 2724: // 1.17
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v117.BukkitVersionHelperSpigot117");
                    break;
                case 2586: // 1.16.5
                case 2584: // 1.16.4
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_4.BukkitVersionHelperSpigot116_4");
                    break;
                case 2580: // 1.16.3
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_3.BukkitVersionHelperSpigot116_3");
                    break;
                case 2578: // 1.16.2
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116_2.BukkitVersionHelperSpigot116_2");
                    break;
                case 2567: // 1.16.1
                case 2566: // 1.16
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v116.BukkitVersionHelperSpigot116");
                    break;
                case 2230: // 1.15.2
                case 2227: // 1.15.1
                case 2225: // 1.15
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v115.BukkitVersionHelperSpigot115");
                    break;
                case 1976: // 1.14.4
                case 1968: // 1.14.3
                case 1963: // 1.14.2
                case 1957: // 1.14.1
                case 1952: // 1.14
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v114_1.BukkitVersionHelperSpigot114_1");
                    break;
                case 1631: // 1.13.2
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.v113_2.BukkitVersionHelperSpigot113_2");
                    break;
                default: // everything else
                    BukkitVersionHelper.helper = loadVersionHelper("org.dynmap.bukkit.helper.BukkitVersionHelperCB");
                    break;
            }
        }
        return BukkitVersionHelper.helper;
    }

}
