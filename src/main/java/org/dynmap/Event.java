package org.dynmap;

import java.util.LinkedList;
import java.util.List;

public class Event<T> {
    private List<Listener<T>> listeners = new LinkedList<Listener<T>>();

    public synchronized void addListener(Listener<T> l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener<T> l) {
        listeners.remove(l);
    }

    public synchronized void trigger(T t) {
        for (Listener<T> l : listeners) {
            l.triggered(t);
        }
    }

    public interface Listener<T> {
        void triggered(T t);
    }
}
