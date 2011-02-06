package org.dynmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class UpdateQueue {
    public Object lock = new Object();
    private LinkedList<Update> updateQueue = new LinkedList<Update>();

    private static final int maxUpdateAge = 120000;

    public void pushUpdate(Object obj) {
        long now = System.currentTimeMillis();
        long deadline = now - maxUpdateAge;
        synchronized (lock) {
            ListIterator<Update> i = updateQueue.listIterator(0);
            while (i.hasNext()) {
                Update u = i.next();
                if (u.time < deadline || u.obj == obj)
                    i.remove();
            }
            updateQueue.addLast(new Update(now, obj));
        }
    }

    private ArrayList<Object> tmpupdates = new ArrayList<Object>();

    public Object[] getUpdatedObjects(long since) {
        long now = System.currentTimeMillis();
        long deadline = now - maxUpdateAge;
        Object[] updates;
        synchronized (lock) {
            tmpupdates.clear();
            Iterator<Update> it = updateQueue.descendingIterator();
            while (it.hasNext()) {
                Update u = it.next();
                if (u.time >= since) {
                    // Tile is new.
                    tmpupdates.add(u.obj);
                } else if (u.time < deadline) {
                    // Tile is too old, removing this one (will eventually decrease).
                    it.remove();
                    break;
                } else {
                    // Tile is old, but not old enough for removal.
                    break;
                }
            }
            
            // Reverse output.
            updates = new Object[tmpupdates.size()];
            for (int i = 0; i < updates.length; i++) {
                updates[i] = tmpupdates.get(updates.length-1-i);
            }
        }
        return updates;
    }

    public class Update {
        public long time;
        public Object obj;

        public Update(long time, Object obj) {
            this.time = time;
            this.obj = obj;
        }
    }
}
