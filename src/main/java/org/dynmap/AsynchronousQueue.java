package org.dynmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

public class AsynchronousQueue<T> {
    private Object lock = new Object();
    private Thread thread;
    private LinkedList<T> queue = new LinkedList<T>();
    private Set<T> set = new HashSet<T>();
    private Handler<T> handler;
    private int dequeueTime;
    private int accelDequeueTime;
    private int accelDequeueThresh;
    private int pendingcnt;
    private int pendinglimit;
    
    public AsynchronousQueue(Handler<T> handler, int dequeueTime, int accelDequeueThresh, int accelDequeueTime, int pendinglimit) {
        this.handler = handler;
        this.dequeueTime = dequeueTime;
        this.accelDequeueTime = accelDequeueTime;
        this.accelDequeueThresh = accelDequeueThresh;
        this.pendinglimit = pendinglimit;
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
            T t = queue.pollFirst();
            if(t != null)
                set.remove(t);
            return t;
        }
    }
    
    public boolean remove(T t) {
        synchronized (lock) {
            if (set.remove(t)) {
                queue.remove(t);
            	return true;
            }
        }
        return false;
    }

    public int size() {
        return set.size();
    }

    public List<T> popAll() {
        List<T> s;
        synchronized(lock) {
            s = new ArrayList<T>(queue);
            queue.clear();
            set.clear();
        }
        return s;
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
                Log.info("Failed to set minimum priority for worker thread!");
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            if (thread == null)
                return;
            Thread oldThread = thread;
            thread = null;

            Log.info("Stopping map renderer...");

            oldThread.interrupt();
            try {
                oldThread.join(1000);
            } catch (InterruptedException e) {
                Log.info("Waiting for map renderer to stop is interrupted");
            }
        }
    }

    private void running() {
        try {
            while (Thread.currentThread() == thread) {
            	synchronized(lock) {
            		while(pendingcnt >= pendinglimit) {
            			try {
            				lock.wait(accelDequeueTime);
            			} catch (InterruptedException ix) {
            				if(Thread.currentThread() != thread)
            					return;
            				throw ix;
            			}
            		}
            	}
                T t = pop();
                if (t != null) {
                	synchronized(lock) {
                		pendingcnt++;
                	}
                	Log.info("handle(" + t + ")");
                    handler.handle(t);
                }
                if(set.size() >= accelDequeueThresh)
                    sleep(accelDequeueTime);
                else
                    sleep(dequeueTime);
            }

        } catch (Exception ex) {
            Log.severe("Exception on rendering-thread", ex);
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
    
    public void done(T t) {
    	Log.info("done(" + t + ")");
        synchronized (lock) {
        	if(pendingcnt > 0) pendingcnt--;
        	lock.notifyAll();
        }
    }
}
