package org.dynmap.utils;

/**
 * Vector3D with index (used for indexed list of vectors for OBJ exporter)
 */
public class IndexedVector3D extends Vector3D {
    public int index = -1;  // -1 = unindexed

    public IndexedVector3D() {
    }
    
    public IndexedVector3D(double x, double y, double z, int index) {
        super(x,y,z);
        this.index = index;
    }
    
    public IndexedVector3D(Vector3D d) {
        super(d);
    }
}
