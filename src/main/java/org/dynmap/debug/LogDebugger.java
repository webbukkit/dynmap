package org.dynmap.debug;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LogDebugger implements Debugger {
    protected static final Logger log = Logger.getLogger("Minecraft");
    private static String prepend = "dynmap: ";
    
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
    }

}
