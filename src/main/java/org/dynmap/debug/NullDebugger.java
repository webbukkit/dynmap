package org.dynmap.debug;

import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.ConfigurationNode;

public class NullDebugger implements Debugger {
    public static final NullDebugger instance = new NullDebugger(null, null);

    public NullDebugger(JavaPlugin plugin, ConfigurationNode configuration) {
    }

    public void debug(String message) {
    }

    public void error(String message) {
    }

    public void error(String message, Throwable thrown) {
    }

}
