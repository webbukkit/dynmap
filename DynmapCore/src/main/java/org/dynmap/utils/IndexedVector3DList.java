package org.dynmap.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Indexed list of Vector3D values: used for managing Vector3D sets for OBJ export
 */
public class IndexedVector3DList {
    private int nextIndex = 1;      // Next index value for list
    private HashMap<Vector3D, IndexedVector3D> set = new HashMap<Vector3D, IndexedVector3D>();  // Set of values

    public interface ListCallback {
        public void elementAdded(IndexedVector3DList list, IndexedVector3D newElement);
    }
    private ListCallback callback;  // Callback for new elements added to list
    
    public IndexedVector3DList(ListCallback cb) {
        callback = cb;
    }
    /**
     * Reset set of vectors: does NOT reset index values (need to be global)
     */
    public void resetSet() {
        set.clear();    // Drop all of them
    }
    /**
     * Reset vectors within given range (assume no longer needed) - does not reset index
     * 
     * @param minx - minimum X (inclusive)
     * @param miny - minimum Y (inclusive)
     * @param minz - minimum Z (inclusive)
     * @param maxx - maximum X (exclusive)
     * @param maxy - maximum Y (exclusive)
     * @param maxz - maximum Z (exclusive)
     */
    public void resetSet(double minx, double miny, double minz, double maxx, double maxy, double maxz) {
        Iterator<Map.Entry<Vector3D, IndexedVector3D>> iter = set.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Vector3D, IndexedVector3D> ne = iter.next();
            Vector3D n = ne.getKey();
            if ((n.x >= minx) && (n.x < maxx) && (n.y >= miny) && (n.y < maxy) && (n.z >= minz) && (n.z < maxz)) {
                iter.remove();
            }
        }
    }
    
    /**
     * Get index of given vector
     * @param x - x value
     * @param y - y value
     * @param z - z value
     * @return index
     */
    public int getVectorIndex(double x, double y, double z) {
        IndexedVector3D v = new IndexedVector3D(x, y, z, nextIndex);
        IndexedVector3D existingv = set.get(v); // Find existing, if present
        if (existingv != null) {    // Found match
            return existingv.index;
        }
        set.put(v, v);  // Add new record to set
        nextIndex++;    // Bump index
        if (callback != null) {
            callback.elementAdded(this, v);
        }
        return v.index;
    }
}
