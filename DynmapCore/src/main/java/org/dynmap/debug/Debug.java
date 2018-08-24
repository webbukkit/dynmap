package org.dynmap.debug;

import java.util.ArrayList;

public class Debug {
    private static ArrayList<Debugger> debuggers = new ArrayList<Debugger>();

    public synchronized static void addDebugger(Debugger d) {
        debuggers.add(d);
    }

    public synchronized static void removeDebugger(Debugger d) {
        debuggers.remove(d);
    }

    public synchronized static void clearDebuggers() {
        debuggers.clear();
    }

    public synchronized static void debug(String message) {
        for(int i = 0; i < debuggers.size(); i++) debuggers.get(i).debug(message);
    }

    public synchronized static void error(String message) {
        for(int i = 0; i < debuggers.size(); i++) debuggers.get(i).error(message);
    }

    public synchronized static void error(String message, Throwable thrown) {
        for(int i = 0; i < debuggers.size(); i++) debuggers.get(i).error(message, thrown);
    }
}
