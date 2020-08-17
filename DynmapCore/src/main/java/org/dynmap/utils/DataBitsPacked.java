package org.dynmap.utils;

// Pre 1.16 chunk section data array
public class DataBitsPacked {

    private final long[] values;
    private final int bitsperrec;
    private final long valuemask;
    private final int length;

    public static int calcLongCount(int i, int j) {
        if (j == 0) {
            return 0;
        } else if (i == 0) {
            return j;
        } else {
            if (i < 0) {
                j *= -1;
            }

            int k = i % j;

            return k == 0 ? i : i + j - k;
        }
    }

    public DataBitsPacked(int bitsperrec, int length, long[] values) {
        this.length = length;
        this.bitsperrec = bitsperrec;
        this.values = values;
        this.valuemask = (1L << bitsperrec) - 1L;
        int properlength = calcLongCount(length * bitsperrec, 64) / 64;

        if (values.length != properlength) {
            throw new IllegalArgumentException("Invalid length given for storage, got: " + values.length + " but expected: " + properlength);
        }
    }

    public int getAt(int offset) {
        int j = offset * this.bitsperrec;
        int k = j >> 6;
        int l = (offset + 1) * this.bitsperrec - 1 >> 6;
        int i1 = j ^ k << 6;

        if (k == l) {
            return (int) (this.values[k] >>> i1 & this.valuemask);
        } else {
            int j1 = 64 - i1;

            return (int) ((this.values[k] >>> i1 | this.values[l] << j1) & this.valuemask);
        }
    }
}
