package org.dynmap.bukkit.helper.v121_7;

import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;

import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
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
			return obj.e(); // CompoundTag.keySet
		}
		@Override
		public boolean contains(String s) {
			return obj.b(s); // CompoundTag.contains
		}
		@Override
		public boolean contains(String s, int i) {
			// Like contains, but with an extra constraint on type
			NBTBase base = obj.a(s); // CompoundTag.get
			if (base == null)
				return false;
			byte type = base.b(); // CompoundTag.getId
			if (type == i)
				return true;
			else if (i != TAG_ANY_NUMERIC)
				return false;
			return type == TAG_BYTE || type == TAG_SHORT || type == TAG_INT || type == TAG_LONG || type == TAG_FLOAT
					|| type == TAG_DOUBLE;
		}
		@Override
		public byte getByte(String s) {
			return obj.b(s, (byte)0); // CompoundTag.getByteOr
		}
		@Override
		public short getShort(String s) {
			return obj.b(s, (short)0); // CompoundTag.getShortOr
		}
		@Override
		public int getInt(String s) {
			return obj.b(s, 0); // CompoundTag.getIntOr
		}
		@Override
		public long getLong(String s) {
			return obj.b(s, 0L); // CompoundTag.getLongOr
		}
		@Override
		public float getFloat(String s) {
			return obj.b(s, 0.0f); // CompoundTag.getFloatOr
		}
		@Override
		public double getDouble(String s) {
			return obj.b(s, 0.0); // CompoundTag.getDoubleOr
		}
		@Override
		public String getString(String s) {
			return obj.b(s, ""); // CompoundTag.getDoubleOr
		}
		@Override
		public byte[] getByteArray(String s) {
			Optional<byte[]> byteArr = obj.j(s); // CompoundTag.getByteArray
			return byteArr.orElseGet(() -> new byte[0]);
		}
		@Override
		public int[] getIntArray(String s) {
			Optional<int[]> intArr = obj.k(s); // CompoundTag.getIntArray
			return intArr.orElseGet(() -> new int[0]);
		}
		@Override
		public long[] getLongArray(String s) {
			Optional<long[]> longArr = obj.l(s); // CompoundTag.getLongArray
			return longArr.orElseGet(() -> new long[0]);
		}
		@Override
		public GenericNBTCompound getCompound(String s) {
			return new NBTCompound(obj.n(s)); // CompoundTag.getCompoundOrEmpty
		}
		@Override
		public GenericNBTList getList(String s, int i) {
			// i argument used to be used to constrain list type, but nbt lists no longer have types as of 1.21.5
			return new NBTList(obj.p(s)); // CompoundTag.getListOrEmpty
		}
		@Override
		public boolean getBoolean(String s) {
			return getByte(s) != 0;
		}
		@Override
		public String getAsString(String s) {
			return obj.a(s).p_().orElseGet(() -> ""); // CompoundTag.get ; Tag.asString
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
			return obj.a(idx, ""); // ListTag.getStringOr
		}
		@Override
		public GenericNBTCompound getCompound(int idx) {
			return new NBTCompound(obj.b(idx)); // ListTag.getCompoundOrEmpty
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
