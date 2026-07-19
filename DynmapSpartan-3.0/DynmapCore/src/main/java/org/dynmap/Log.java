package org.dynmap;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.dynmap.utils.DynmapLogger;

public class Log {
    private static Logger log = Logger.getLogger("Dynmap");
    private static String prefix = "";
    private static DynmapLogger dlog = null;
    public static boolean verbose = false;
    
    public static String safeString(String s) { return s.replaceAll("[\\${}]", "_"); }

    public static void setLogger(Logger logger, String pre) {
        log = logger;
        if((pre != null) && (pre.length() > 0))
            prefix = pre + " ";
        else
            prefix = "";
    }
    public static void setLogger(DynmapLogger logger) {
        dlog = logger;
    }
    public static void setLoggerParent(Logger parent) {
        log.setParent(parent);
    }
    public static void info(String msg) {
    	msg = safeString(msg);
        if (dlog != null) {
            dlog.info(msg);
        }
        else {
            log.log(Level.INFO, prefix + msg);
        }
    }
    public static void verboseinfo(String msg) {
        if(verbose) {
        	msg = safeString(msg);
            if (dlog != null) {
                dlog.info(msg);
            }
            else {
                log.log(Level.INFO, prefix + msg);
            }
        }
    }
    public static void severe(Throwable e) {
        if (dlog != null) {
            dlog.severe(e);
        }
        else {
            log.log(Level.SEVERE, prefix + "Exception occured: ", e);
        }
    }
    public static void severe(String msg) {
    	msg = safeString(msg);
        if (dlog != null) {
            dlog.severe(msg);
        }
        else {
            log.log(Level.SEVERE, prefix + msg);
        }
    }
    public static void severe(String msg, Throwable e) {
    	msg = safeString(msg);
        if (dlog != null) {
            dlog.severe(msg, e);
        }
        else {
            log.log(Level.SEVERE, prefix + msg, e);
        }
    }
    public static void warning(String msg) {
    	msg = safeString(msg);
        if (dlog != null) {
            dlog.warning(msg);
        }
        else {
            log.log(Level.WARNING, prefix + msg);
        }
    }
    public static void warning(String msg, Throwable e) {
    	msg = safeString(msg);
        if (dlog != null) {
            dlog.warning(msg, e);
        }
        else {
            log.log(Level.WARNING, prefix + msg, e);
        }
    }
}
