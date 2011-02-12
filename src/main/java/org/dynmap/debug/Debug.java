package org.dynmap.debug;

import java.util.LinkedList;
import java.util.List;

public class Debug {
    private static List<Debugger> debuggers = new LinkedList<Debugger>();

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
        for(Debugger d : debuggers) d.debug(message);
    }

    public synchronized static void error(String message) {
        for(Debugger d : debuggers) d.error(message);
    }

    public synchronized static void error(String message, Throwable thrown) {
        for(Debugger d : debuggers) d.error(message, thrown);
    }
}
