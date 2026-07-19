package org.dynmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

public class AsynchronousQueue<T> {
    private Object lock = new Object();
    private Thread thread;
    private LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<T>();
    private Set<T> set = new HashSet<T>();
    private Handler<T> handler;
    private int dequeueTime;
    private int accelDequeueTime;
    public int accelDequeueThresh;
    private int pendingcnt;
    private int pendinglimit;
    private boolean normalprio;
    
    public AsynchronousQueue(Handler<T> handler, int dequeueTime, int accelDequeueThresh, int accelDequeueTime, int pendinglimit, boolean normalprio) {
        this.handler = handler;
        this.dequeueTime = dequeueTime;
        this.accelDequeueTime = accelDequeueTime;
        this.accelDequeueThresh = accelDequeueThresh;
        if(pendinglimit < 1) pendinglimit = 1;
        this.pendinglimit = pendinglimit;
        this.normalprio = normalprio;
    }

    public boolean push(T t) {
        synchronized (lock) {
            if (!set.add(t)) {
                return false;
            }
        }
        queue.offer(t);
        return true;
    }

    private T pop() {
        try {
            T t = queue.take();
            synchronized (lock) {
                set.remove(t);
            }
            return t;
        } catch (InterruptedException ix) {
            return null;
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
            thread.setDaemon(true);
            thread.start();
            try {
                if(!normalprio)
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
        synchronized (lock) {
        	if(pendingcnt > 0) pendingcnt--;
        	lock.notifyAll();
        }
    }
}
