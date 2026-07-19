package org.dynmap;

public class DynmapChunk {
    public int x, z;

    public DynmapChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }
    @Override
    public boolean equals(Object o) {
        if(o instanceof DynmapChunk) {
            DynmapChunk dc = (DynmapChunk)o;
            return (dc.x == this.x) && (dc.z == this.z);
        }
        return false;
    }
    @Override
    public int hashCode() {
        return x ^ (z << 5);
    }
}
