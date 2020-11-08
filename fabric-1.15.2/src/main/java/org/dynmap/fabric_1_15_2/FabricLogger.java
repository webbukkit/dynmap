package org.dynmap.fabric_1_15_2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dynmap.utils.DynmapLogger;

public class FabricLogger implements DynmapLogger {
    Logger log;
    public static final String DM = "[Dynmap] ";

    FabricLogger() {
        log = LogManager.getLogger("Dynmap");
    }

    @Override
    public void info(String s) {
        log.info(DM + s);
    }

    @Override
    public void severe(Throwable t) {
        log.fatal(t);
    }

    @Override
    public void severe(String s) {
        log.fatal(DM + s);
    }

    @Override
    public void severe(String s, Throwable t) {
        log.fatal(DM + s, t);
    }

    @Override
    public void verboseinfo(String s) {
        log.info(DM + s);
    }

    @Override
    public void warning(String s) {
        log.warn(DM + s);
    }

    @Override
    public void warning(String s, Throwable t) {
        log.warn(DM + s, t);
    }
}