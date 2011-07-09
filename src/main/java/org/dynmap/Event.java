package org.dynmap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Event<T> {
    private List<Listener<T>> listeners = new LinkedList<Listener<T>>();
    private Object lock = new Object();

    public void addListener(Listener<T> l) {
        synchronized(lock) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener<T> l) {
        synchronized(lock) {
            listeners.remove(l);
        }
    }

    public void trigger(T t) {
        ArrayList<Listener<T>> iterlist;
        synchronized(lock) {
            iterlist = new ArrayList<Listener<T>>(listeners);
        }
        for (Listener<T> l : iterlist) {
            l.triggered(t);
        }
    }

    public interface Listener<T> {
        void triggered(T t);
    }
}
