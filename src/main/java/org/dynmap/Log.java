package org.dynmap;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Log {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";
    public static boolean verbose = false;
    public static void info(String msg) {
        log.log(Level.INFO, LOG_PREFIX + msg);
    }
    public static void verboseinfo(String msg) {
        if(verbose)
            log.log(Level.INFO, LOG_PREFIX + msg);
    }
    public static void severe(Exception e) {
        log.log(Level.SEVERE, LOG_PREFIX + "Exception occured: ", e);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, LOG_PREFIX + msg);
    }
    public static void severe(String msg, Exception e) {
        log.log(Level.SEVERE, LOG_PREFIX + msg, e);
    }
    public static void warning(String msg) {
        log.log(Level.WARNING, LOG_PREFIX + msg);
    }
    public static void warning(String msg, Exception e) {
        log.log(Level.WARNING, LOG_PREFIX + msg, e);
    }
}
