package org.dynmap.utils;
import org.dynmap.DynmapLocation;
/**
 * Simple vector class
 */
public class Vector3D {
    public double x, y, z;
    
    public Vector3D() { x = y = z = 0.0; }
    
    public Vector3D(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }
    public Vector3D(Vector3D v) {
        this.x = v.x; this.y = v.y; this.z = v.z;
    }
    
    public final void setFromLocation(DynmapLocation l) { x = l.x; y = l.y; z = l.z; }

    public final Vector3D set(Vector3D v) {
        x = v.x; y = v.y; z = v.z;
    	return this;
    }
    
    public final Vector3D subtract(Vector3D v) {
        x = x - v.x; y = y - v.y; z = z - v.z;
    	return this;
    }

    public final Vector3D add(Vector3D v) {
        x = x + v.x; y = y + v.y; z = z + v.z;
    	return this;
    }

    public final double innerProduct(Vector3D v) {
        return (v.x * x) + (v.y * y) + (v.z * z);
    }
    
    public final Vector3D scale(double m) {
    	x = x * m; y = y * m; z = z * m;
    	return this;
    }
    
    public final double length() {
    	return Math.sqrt(x*x + y*y + z*z);
    }
    
    /* this = this X v */
    public final Vector3D crossProduct(Vector3D v) {
        double newx = (y*v.z) - (z*v.y);
        double newy = (z*v.x) - (x*v.z);
        double newz = (x*v.y) - (y*v.x); 
        x = newx; y = newy; z = newz;
    	return this;
    }
    
    public String toString() {
        return "{ " + x + ", " + y + ", " + z + " }";
    }
    
    @Override
    public boolean equals(Object v) {
        if (v instanceof Vector3D) {
            Vector3D vv = (Vector3D) v;
            return (vv.x == x) && (vv.y == y) && (vv.z == z);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        long v = Double.doubleToLongBits(x) ^ Double.doubleToLongBits(y) ^ Double.doubleToLongBits(z);
        return (int) (v ^ (v >> 32));
    }
}
