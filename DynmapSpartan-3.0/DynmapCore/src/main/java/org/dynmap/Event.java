package org.dynmap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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
    
    /* Only use from main thread */
    public void trigger(T t) {
        ArrayList<Listener<T>> iterlist;
        synchronized(lock) {
            iterlist = new ArrayList<Listener<T>>(listeners);
        }
        for (Listener<T> l : iterlist) {
            l.triggered(t);
        }
    }
    
    /* Trigger on main thread */
    public boolean triggerSync(DynmapCore core, final T t) {
        Future<T> future = core.getServer().callSyncMethod(new Callable<T>() {
            @Override
            public T call() throws Exception {
                trigger(t);
                return t;
            }            
        });
        boolean success = false;
        try {
            if(future != null) {
                future.get();
                success = true;
            }
        } catch (ExecutionException ix) {
            Log.severe("Exception in triggerSync", ix.getCause());
        } catch (InterruptedException ix) {
        }
        return success;
    }

    public interface Listener<T> {
        void triggered(T t);
    }
}
