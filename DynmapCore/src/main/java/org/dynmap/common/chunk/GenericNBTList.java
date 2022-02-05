package org.dynmap.common.chunk;

// Generic interface for accessing an NBT Composite object
public interface GenericNBTList {
	public int size();
    public String getString(int idx);
    public GenericNBTCompound getCompound(int idx);
}
