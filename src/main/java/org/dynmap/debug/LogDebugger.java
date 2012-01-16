package org.dynmap.debug;

import org.dynmap.ConfigurationNode;
import org.dynmap.DynmapCore;
import org.dynmap.Log;

public class LogDebugger implements Debugger {
    public LogDebugger(DynmapCore core, ConfigurationNode configuration) {
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
