package org.dynmap.debug;

public class NullDebugger implements Debugger {
    public static final NullDebugger instance = new NullDebugger();

    public void debug(String message) {
    }

    public void error(String message) {
    }

    public void error(String message, Throwable thrown) {
    }

}
