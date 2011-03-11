package org.dynmap;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsynchronousQueue<T> {
    protected static final Logger log = Logger.getLogger("Minecraft");

    private Object lock = new Object();
    private Thread thread;
    private LinkedList<T> queue = new LinkedList<T>();
    private Set<T> set = new HashSet<T>();
    private Handler<T> handler;
    private int dequeueTime;

    public AsynchronousQueue(Handler<T> handler, int dequeueTime) {
        this.handler = handler;
        this.dequeueTime = dequeueTime;
    }

    public boolean push(T t) {
        synchronized (lock) {
            if (set.add(t)) {
                queue.addLast(t);
                return true;
            }
            return false;
        }
    }

    private T pop() {
        synchronized (lock) {
            try {
                T t = queue.removeFirst();
                if (!set.remove(t)) {
                    // This should never happen.
                }
                return t;
            } catch (NoSuchElementException e) {
                return null;
            }
        }
    }

    public int size() {
        return set.size();
    }

    public void start() {
        synchronized (lock) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    running();
                }
            });
            thread.start();
            try {
                thread.setPriority(Thread.MIN_PRIORITY);
            } catch (SecurityException e) {
                log.info("Failed to set minimum priority for worker thread!");
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            if (thread == null)
                return;
            Thread oldThread = thread;
            thread = null;

            log.info("Stopping map renderer...");

            oldThread.interrupt();
            try {
                oldThread.join(1000);
            } catch (InterruptedException e) {
                log.info("Waiting for map renderer to stop is interrupted");
            }
        }
    }

    private void running() {
        try {
            while (Thread.currentThread() == thread) {
                T t = pop();
                if (t != null) {
                    handler.handle(t);
                }
                sleep(dequeueTime);
            }

        } catch (Exception ex) {
            log.log(Level.SEVERE, "Exception on rendering-thread", ex);
        }
    }

    private boolean sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }
}
