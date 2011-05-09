package org.dynmap.debug;

import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

public class NullDebugger implements Debugger {
    public static final NullDebugger instance = new NullDebugger(null, null);

    public NullDebugger(JavaPlugin plugin, Map<String, Object> configuration) {
    }
    
    public void debug(String message) {
    }

    public void error(String message) {
    }

    public void error(String message, Throwable thrown) {
    }

}
