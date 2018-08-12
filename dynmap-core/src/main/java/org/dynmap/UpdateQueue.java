package org.dynmap;

import java.util.ArrayList;
import java.util.HashMap;

public class UpdateQueue {
    public Object lock = new Object();
    private HashMap<UpdateRec,UpdateRec> updateSet = new HashMap<UpdateRec,UpdateRec>();
    private UpdateRec orderedlist = null;   /* Oldest to youngest */
    private static final long maxUpdateAge = 120000;
    private static final long ageOutPeriod = 5000;
    private long lastageout = 0;

    private static class UpdateRec {
        Client.Update u;
        UpdateRec next;
        UpdateRec prev;
        
        @Override
        public boolean equals(Object o) {
            if(o instanceof UpdateRec)
                return u.equals(((UpdateRec)o).u);
            return false;
        }
        @Override
        public int hashCode() {
            return u.hashCode();
        }
    }
    
    private void doAgeOut(long now) {
        /* If we're due */
        if((now < lastageout) || (now > (lastageout + ageOutPeriod))) {
            lastageout = now;
            long deadline = now - maxUpdateAge;
            while((orderedlist != null) && (orderedlist.u.timestamp < deadline)) {
                UpdateRec r = orderedlist;

                updateSet.remove(r);  /* Remove record from set */
                if(r.next == r) {
                    orderedlist = null;
                }
                else {
                    orderedlist = r.next;
                    r.next.prev = r.prev;
                    r.prev.next = r.next;
                }
                r.next = r.prev = null;
            }
        }
    }
    
    public void pushUpdate(Client.Update obj) {
        synchronized (lock) {
            /* Do inside lock - prevent delay between time and actual work */
            long now = System.currentTimeMillis();
            doAgeOut(now);  /* Consider age out */
            UpdateRec r = new UpdateRec();
            r.u = obj;
            r.u.timestamp = now; // Use our timestamp: makes sure order is preserved
            UpdateRec oldr = updateSet.remove(r);   /* Try to remove redundant event */
            if(oldr != null) {  /* If found, remove from ordered list too */
                if(oldr.next == oldr) { /* Only one? */
                    orderedlist = null;
                }
                else {
                    if(orderedlist == oldr) {   /* We're oldest? */
                        orderedlist = oldr.next;
                    }
                    oldr.next.prev = oldr.prev;
                    oldr.prev.next = oldr.next;
                }
                oldr.next = oldr.prev = null;
            }
            updateSet.put(r, r);
            /* Add to end of ordered list */
            if(orderedlist == null) {
                orderedlist = r;
                r.next = r.prev = r;
            }
            else {
                r.next = orderedlist;
                r.prev = orderedlist.prev;
                r.next.prev = r.prev.next = r;
            }
        }
    }

    private ArrayList<Client.Update> tmpupdates = new ArrayList<Client.Update>();

    public Client.Update[] getUpdatedObjects(long since) {
        Client.Update[] updates;
        synchronized (lock) {
            long now = System.currentTimeMillis();
            doAgeOut(now);  /* Consider age out */
            
            tmpupdates.clear();
            if(orderedlist != null) {
                UpdateRec r = orderedlist.prev; /* Get newest */
                while(r != null) {
                    if(r.u.timestamp >= since) {
                        tmpupdates.add(r.u);
                        if(r == orderedlist)
                            r = null;
                        else
                            r = r.prev;
                    }
                    else {
                        r = null;
                    }
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
