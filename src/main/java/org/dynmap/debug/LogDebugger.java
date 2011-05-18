package org.dynmap.debug;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.ConfigurationNode;

public class LogDebugger implements Debugger {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";

    public LogDebugger(JavaPlugin plugin, ConfigurationNode configuration) {
    }

    @Override
    public void debug(String message) {
        log.info(LOG_PREFIX + message);
    }

    @Override
    public void error(String message) {
        log.log(Level.SEVERE, LOG_PREFIX + message);
    }

    @Override
    public void error(String message, Throwable thrown) {
        log.log(Level.SEVERE, LOG_PREFIX + message);
        thrown.printStackTrace();
    }

}
