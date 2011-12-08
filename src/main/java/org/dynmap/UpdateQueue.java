package org.dynmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

public class UpdateQueue {
    public Object lock = new Object();
    private LinkedList<Client.Update> updateQueue = new LinkedList<Client.Update>();

    private static final long maxUpdateAge = 120000;
    private static final long ageOutPeriod = 5000;
    
    private long lastageout = 0;

    private void doAgeOut(long now) {
        /* If we're due */
        if((now < lastageout) || (now > (lastageout + ageOutPeriod))) {
            lastageout = now;
            long deadline = now - maxUpdateAge;
            ListIterator<Client.Update> i = updateQueue.listIterator(0);
            while (i.hasNext()) {
                Client.Update u = i.next();
                if (u.timestamp < deadline)
                    i.remove();
                else
                    break;
            }
        }
    }
    
    public void pushUpdate(Client.Update obj) {
        synchronized (lock) {
            /* Do inside lock - prevent delay between time and actual work */
            long now = System.currentTimeMillis();
            doAgeOut(now);  /* Consider age out */
            updateQueue.addLast(obj);
        }
    }

    private ArrayList<Client.Update> tmpupdates = new ArrayList<Client.Update>();

    public Client.Update[] getUpdatedObjects(long since) {
        Client.Update[] updates;
        synchronized (lock) {
            long now = System.currentTimeMillis();
            doAgeOut(now);  /* Consider age out */
            
            tmpupdates.clear();
            Iterator<Client.Update> it = updateQueue.descendingIterator();
            while (it.hasNext()) {
                Client.Update u = it.next();
                if (u.timestamp >= since) {
                    // Tile is new.
                    tmpupdates.add(u);
                }
                else {
                    break;
                }
            }

            // Reverse output.
            updates = new Client.Update[tmpupdates.size()];
            for (int i = 0; i < updates.length; i++) {
                updates[i] = tmpupdates.get(updates.length-1-i);
            }
        }
        return updates;
    }
}
