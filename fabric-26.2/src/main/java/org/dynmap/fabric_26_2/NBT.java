package org.dynmap.fabric_26_2;

import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.SimpleBitStorage;

public class NBT {

	public static class NBTCompound implements GenericNBTCompound {
		private final CompoundTag obj;
		public NBTCompound(CompoundTag t) {
			this.obj = t;
		}
		@Override
		public Set<String> getAllKeys() {
			return obj.keySet();
		}
		@Override
		public boolean contains(String s) {
			return obj.contains(s);
		}
		@Override
		public boolean contains(String s, int i) {
			// Like contains, but with an extra constraint on type
			Tag base = obj.get(s);
			if (base == null)
				return false;
			int type = base.getId();
			if (type == i)
				return true;
			else if (i != TAG_ANY_NUMERIC)
				return false;
			return type == TAG_BYTE || type == TAG_SHORT || type == TAG_INT || type == TAG_LONG || type == TAG_FLOAT
				|| type == TAG_DOUBLE;
		}
		@Override
		public byte getByte(String s) {
			return obj.getByteOr(s, (byte)0);
		}
		@Override
		public short getShort(String s) {
			return obj.getShortOr(s, (short)0);
		}
		@Override
		public int getInt(String s) {
			return obj.getIntOr(s, (int)0);
		}
		@Override
		public long getLong(String s) {
			return obj.getLongOr(s, (long)0);
		}
		@Override
		public float getFloat(String s) {
			return obj.getFloatOr(s, (float)0);
		}
		@Override
		public double getDouble(String s) {
			return obj.getDoubleOr(s, (double)0);
		}
		@Override
		public String getString(String s) {
			return obj.getStringOr(s, "");
		}
		@Override
		public byte[] getByteArray(String s) {
			return obj.getByteArray(s).orElseGet(() -> new byte[0]);
		}
		@Override
		public int[] getIntArray(String s) {
			return obj.getIntArray(s).orElseGet(() -> new int[0]);
		}
		@Override
		public long[] getLongArray(String s) {
			return obj.getLongArray(s).orElseGet(() -> new long[0]);
		}
		@Override
		public GenericNBTCompound getCompound(String s) {
			return new NBTCompound(obj.getCompoundOrEmpty(s));
		}
		@Override
		public GenericNBTList getList(String s, int i) {
			// i argument used to be used to constrain list type, but nbt lists no longer have types as of 1.21.5
			return new NBTList(obj.getListOrEmpty(s));
		}
		@Override
		public boolean getBoolean(String s) {
			return obj.getBooleanOr(s, false);
		}
		@Override
		public String getAsString(String s) {
			Tag t = obj.get(s);
			return (t != null) ? t.asString().orElseGet(() -> "") : "";
		}
		@Override
		public GenericBitStorage makeBitStorage(int bits, int count, long[] data) {
			return new OurBitStorage(bits, count, data);
		}
		public String toString() {
			return obj.toString();
		}
	}
	public static class NBTList implements GenericNBTList {
		private final ListTag obj;
		public NBTList(ListTag t) {
			obj = t;
		}
		@Override
		public int size() {
			return obj.size();
		}
		@Override
		public String getString(int idx) {
			return obj.getStringOr(idx, "");
		}
		@Override
		public GenericNBTCompound getCompound(int idx) {
			return new NBTCompound(obj.getCompoundOrEmpty(idx));
		}
		public String toString() {
			return obj.toString();
		}
	}
	public static class OurBitStorage implements GenericBitStorage {
		private final SimpleBitStorage bs;
		public OurBitStorage(int bits, int count, long[] data) {
			bs = new SimpleBitStorage(bits, count, data);
		}
		@Override
		public int get(int idx) {
			return bs.get(idx);
		}
	}
}
