package org.dynmap.debug;

import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.ConfigurationNode;
import org.dynmap.Log;

public class LogDebugger implements Debugger {
    public LogDebugger(JavaPlugin plugin, ConfigurationNode configuration) {
    }

    @Override
    public void debug(String message) {
        Log.info(message);
    }

    @Override
    public void error(String message) {
        Log.severe(message);
    }

    @Override
    public void error(String message, Throwable thrown) {
        Log.severe(message);
        thrown.printStackTrace();
    }

}
