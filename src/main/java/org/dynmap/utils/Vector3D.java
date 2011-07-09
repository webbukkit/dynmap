package org.dynmap.utils;
import org.bukkit.Location;
/**
 * Simple vector class
 */
public class Vector3D {
    public double x, y, z;
    
    public Vector3D() { x = y = z = 0.0; }
    
    public void setFromLocation(Location l) { x = l.getX(); y = l.getY(); z = l.getZ(); }
    
    public String toString() {
        return "{ " + x + ", " + y + ", " + z + " }";
    }
}
