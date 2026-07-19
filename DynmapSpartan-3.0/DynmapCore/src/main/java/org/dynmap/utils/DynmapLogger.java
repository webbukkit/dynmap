package org.dynmap.utils;

public interface DynmapLogger {
    public void info(String msg);
    public void verboseinfo(String msg);
    public void severe(Throwable e);
    public void severe(String msg);
    public void severe(String msg, Throwable e);
    public void warning(String msg);
    public void warning(String msg, Throwable e);
}
