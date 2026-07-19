package org.dynmap.debug;

public interface Debugger {
    void debug(String message);

    void error(String message);

    void error(String message, Throwable thrown);
}
