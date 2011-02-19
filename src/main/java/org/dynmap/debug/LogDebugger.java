package org.dynmap.debug;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public class LogDebugger implements Debugger {
    protected static final Logger log = Logger.getLogger("Minecraft");
    private static String prepend = "dynmap: ";
    
    public LogDebugger(JavaPlugin plugin, Map<String, Object> configuration) {
    }
    
    @Override
    public void debug(String message) {
        log.info(prepend + message);
    }

    @Override
    public void error(String message) {
        log.log(Level.SEVERE, prepend + message);
    }

    @Override
    public void error(String message, Throwable thrown) {
        log.log(Level.SEVERE, prepend + message);
        thrown.printStackTrace();
    }

}
