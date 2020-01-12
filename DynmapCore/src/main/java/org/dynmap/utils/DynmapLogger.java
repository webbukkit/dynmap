package org.dynmap.utils;

public interface DynmapLogger {
    void info(String msg);

    void verboseinfo(String msg);

    void severe(Throwable e);

    void severe(String msg);

    void severe(String msg, Throwable e);

    void warning(String msg);

    void warning(String msg, Throwable e);
}
