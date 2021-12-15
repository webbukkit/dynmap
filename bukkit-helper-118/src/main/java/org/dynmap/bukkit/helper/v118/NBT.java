package org.dynmap.bukkit.helper.v118;

import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;

import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SimpleBitStorage;

public class NBT {

	public static class NBTCompound implements GenericNBTCompound {
		private final NBTTagCompound obj;
		public NBTCompound(NBTTagCompound t) {
			this.obj = t;
		}
		@Override
		public Set<String> getAllKeys() {
			return obj.d();
		}
		@Override
		public boolean contains(String s) {
			return obj.e(s);
		}
		@Override
		public boolean contains(String s, int i) {
			return obj.b(s, i);
		}
		@Override
		public byte getByte(String s) {
			return obj.f(s);
		}
		@Override
		public short getShort(String s) {
			return obj.g(s);
		}
		@Override
		public int getInt(String s) {
			return obj.h(s);
		}
		@Override
		public long getLong(String s) {
			return obj.i(s);
		}
		@Override
		public float getFloat(String s) {
			return obj.j(s);
		}
		@Override
		public double getDouble(String s) {
			return obj.k(s);
		}
		@Override
		public String getString(String s) {
			return obj.l(s);
		}
		@Override
		public byte[] getByteArray(String s) {
			return obj.m(s);
		}
		@Override
		public int[] getIntArray(String s) {
			return obj.n(s);
		}
		@Override
		public long[] getLongArray(String s) {
			return obj.o(s);
		}
		@Override
		public GenericNBTCompound getCompound(String s) {
			return new NBTCompound(obj.p(s));
		}
		@Override
		public GenericNBTList getList(String s, int i) {
			return new NBTList(obj.c(s, i));
		}
		@Override
		public boolean getBoolean(String s) {
			return obj.q(s);
		}
		@Override
		public String getAsString(String s) {
			return obj.c(s).e_();
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
			return obj.j(idx);
		}
		@Override
		public GenericNBTCompound getCompound(int idx) {
			return new NBTCompound(obj.a(idx));
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
			return bs.a(idx);
		}
	}
}
