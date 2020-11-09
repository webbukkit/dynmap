package org.dynmap.forge_1_16_3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.dynmap.DynmapCore;
import org.dynmap.Log;

public class VersionCheck {
    private static final String VERSION_URL = "http://mikeprimm.com/dynmap/releases.php";
    public static void runCheck(final DynmapCore core) {
        new Thread(new Runnable() {
            public void run() {
                doCheck(core);
            }
        }).start();
    }
    
    private static int getReleaseVersion(String s) {
        int index = s.lastIndexOf('-');
        if(index < 0)
            index = s.lastIndexOf('.');
        if(index >= 0)
            s = s.substring(0, index);
        String[] split = s.split("\\.");
        int v = 0;
        try {
            for(int i = 0; (i < split.length) && (i < 3); i++) {
                v += Integer.parseInt(split[i]) << (8 * (2 - i)); 
            }
        } catch (NumberFormatException nfx) {}
        return v;
    }
    
    private static int getBuildNumber(String s) {
        int index = s.lastIndexOf('-');
        if(index < 0)
            index = s.lastIndexOf('.');
        if(index >= 0)
            s = s.substring(index+1);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfx) {
            return 99999999;
        }
    }
    
    private static void doCheck(DynmapCore core) {
        String pluginver = core.getDynmapPluginVersion();
        String platform = core.getDynmapPluginPlatform();
        String platver = core.getDynmapPluginPlatformVersion();
        if((pluginver == null) || (platform == null) || (platver == null))
            return;
        HttpURLConnection conn = null;
        String loc = VERSION_URL;
        int cur_ver = getReleaseVersion(pluginver);
        int cur_bn = getBuildNumber(pluginver);
        try {
            while((loc != null) && (!loc.isEmpty())) {
                URL url = new URL(loc);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Dynmap (" + platform + "/" + platver + "/" + pluginver);
                conn.connect();
                loc = conn.getHeaderField("Location");
            }
            BufferedReader rdr = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = null;
            while((line = rdr.readLine()) != null) {
                String[] split = line.split(":");
                if(split.length < 4) continue;
                /* If our platform and version, or wildcard platform version */
                if(split[0].equals(platform) && (split[1].equals("*") || split[1].equals(platver))) {
                    int recommended_ver = getReleaseVersion(split[2]);
                    int recommended_bn = getBuildNumber(split[2]);
                    if((recommended_ver > cur_ver) || ((recommended_ver == cur_ver) && (recommended_bn > cur_bn)))  { /* Newer recommended build */
                        Log.info("Version obsolete: new recommended version " + split[2] + " is available.");
                    }
                    else if(cur_ver > recommended_ver) {    /* Running dev or prerelease? */
                        int prerel_ver = getReleaseVersion(split[3]);
                        int prerel_bn = getBuildNumber(split[3]);
                        if((prerel_ver > cur_ver) || ((prerel_ver == cur_ver) && (prerel_bn > cur_bn))) {
                            Log.info("Version obsolete: new prerelease version " + split[3] + " is available.");
                        }
                    }
                }
            }
        } catch (Exception x) {
            Log.info("Error checking for latest version");
        } finally {
            if(conn != null) {
                conn.disconnect();
            }
        }
    }
}
