package org.dynmap.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class BufferOutputStream extends OutputStream {
    private static final int CHUNK_SIZE = 8192;
    
    public byte[] buf;
    public int len;
    
    public BufferOutputStream() {
        len = 0;
        buf = new byte[CHUNK_SIZE];
    }
    
    public void reset() {
        len = 0;
    }
            
    @Override
    public final void write(int v) throws IOException {
        if (len >= buf.length){
            buf = Arrays.copyOf(buf, buf.length + CHUNK_SIZE);
        }
        buf[len++] = (byte) v;
    }
    
    @Override
    public final void write(byte[] b, int off, int wlen) {
        if (wlen > 0) {
            if ((len + wlen - 1) >= buf.length) {
                int nlen = len + wlen + CHUNK_SIZE - 1;
                buf = Arrays.copyOf(buf, nlen - (nlen % CHUNK_SIZE));
            }
            for (int i = 0; i < wlen; i++) {
                buf[len++] = b[off++];
            }
        }
    }

    @Override
    public final void write(byte[] b) {
        write(b, 0, b.length);
    }
}
