package org.dynmap.common.chunk;

import java.util.Set;

// Generic interface for accessing an NBT Composite object
public interface GenericNBTCompound {
    public final byte TAG_END = 0;
    public final byte TAG_BYTE = 1;
    public final byte TAG_SHORT = 2;
    public final byte TAG_INT = 3;
    public final byte TAG_LONG = 4;
    public final byte TAG_FLOAT = 5;
    public final byte TAG_DOUBLE = 6;
    public final byte TAG_BYTE_ARRAY = 7;
    public final byte TAG_STRING = 8;
    public final byte TAG_LIST = 9;
    public final byte TAG_COMPOUND = 10;
    public final byte TAG_INT_ARRAY = 11;
    public final byte TAG_LONG_ARRAY = 12;
    public final byte TAG_ANY_NUMERIC = 99;

    public Set<String> getAllKeys();
    public boolean contains(String s);
    public boolean contains(String s, int i);
    public byte getByte(String s);
    public short getShort(String s);
    public int getInt(String s);
    public long getLong(String s);
    public float getFloat(String s);
    public double getDouble(String s);
    public String getString(String s);
    public byte[] getByteArray(String s);
    public int[] getIntArray(String s);
    public long[] getLongArray(String s);
    public GenericNBTCompound getCompound(String s);
    public GenericNBTList getList(String s, int i);
    public boolean getBoolean(String s);
    public String getAsString(String s); /// get(s).getAsString()
    // Factory for bit storage
    public GenericBitStorage makeBitStorage(int bits, int count, long[] data);
}
