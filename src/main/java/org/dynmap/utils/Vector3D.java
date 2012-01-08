package org.dynmap.utils;
import org.dynmap.DynmapLocation;
/**
 * Simple vector class
 */
public class Vector3D {
    public double x, y, z;
    
    public Vector3D() { x = y = z = 0.0; }
    
    public void setFromLocation(DynmapLocation l) { x = l.x; y = l.y; z = l.z; }
    
    public String toString() {
        return "{ " + x + ", " + y + ", " + z + " }";
    }
}
