package org.dynmap.bukkit.helper.v115;

import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;

import net.minecraft.server.v1_15_R1.DataBits;
import net.minecraft.server.v1_15_R1.NBTTagCompound;
import net.minecraft.server.v1_15_R1.NBTTagList;

import java.util.Set;

public class NBT {

	public static class NBTCompound implements GenericNBTCompound {
		private final NBTTagCompound obj;
		public NBTCompound(NBTTagCompound t) {
			this.obj = t;
		}
		@Override
		public Set<String> getAllKeys() {
			return obj.getKeys();
		}
		@Override
		public boolean contains(String s) {
			return obj.hasKey(s);
		}
		@Override
		public boolean contains(String s, int i) {
			return obj.hasKeyOfType(s, i);
		}
		@Override
		public byte getByte(String s) {
			return obj.getByte(s);
		}
		@Override
		public short getShort(String s) {
			return obj.getShort(s);
		}
		@Override
		public int getInt(String s) {
			return obj.getInt(s);
		}
		@Override
		public long getLong(String s) {
			return obj.getLong(s);
		}
		@Override
		public float getFloat(String s) {
			return obj.getFloat(s);
		}
		@Override
		public double getDouble(String s) {
			return obj.getDouble(s);
		}
		@Override
		public String getString(String s) {
			return obj.getString(s);
		}
		@Override
		public byte[] getByteArray(String s) {
			return obj.getByteArray(s);
		}
		@Override
		public int[] getIntArray(String s) {
			return obj.getIntArray(s);
		}
		@Override
		public long[] getLongArray(String s) {
			return obj.getLongArray(s);
		}
		@Override
		public GenericNBTCompound getCompound(String s) {
			return new NBTCompound(obj.getCompound(s));
		}
		@Override
		public GenericNBTList getList(String s, int i) {
			return new NBTList(obj.getList(s, i));
		}
		@Override
		public boolean getBoolean(String s) {
			return obj.getBoolean(s);
		}
		@Override
		public String getAsString(String s) {
			return obj.get(s).asString();
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
		private final NBTTagList obj;
		public NBTList(NBTTagList t) {
			obj = t;
		}
		@Override
		public int size() {
			return obj.size();
		}
		@Override
		public String getString(int idx) {
			return obj.getString(idx);
		}
		@Override
		public GenericNBTCompound getCompound(int idx) {
			return new NBTCompound(obj.getCompound(idx));
		}
		public String toString() {
			return obj.toString();
		}
	}
	public static class OurBitStorage implements GenericBitStorage {
		private final DataBits bs;
		public OurBitStorage(int bits, int count, long[] data) {
			bs = new DataBits(bits, count, data);
		}
		@Override
		public int get(int idx) {
			return bs.a(idx);
		}
	}
}
