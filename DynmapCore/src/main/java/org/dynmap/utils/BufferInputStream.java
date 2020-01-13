package org.dynmap.utils;

import java.io.IOException;
import java.io.InputStream;

public class BufferInputStream extends InputStream {
    private byte[] buf;
    private int len;
    private int off = 0;
    private int mark = 0;
    
    public BufferInputStream(byte[] b) {
        this.len = b.length;
        this.buf = b;
    }
    
    public BufferInputStream(byte[] b, int len) {
        this.len = len;
        this.buf = b;
    }

    public byte[] buffer() { return buf; }
    public int length() { return len; }
    
    @Override
    public int available() {
        return (len - off);
    }
    
    @Override
    public void mark(int readAheadLimit) {
        mark = off;
    }
    
    @Override
    public boolean markSupported() {
        return true;
    }
   
    @Override
    public void reset() {
        this.off = this.mark;
    }
    
    @Override
    public void close() {
    }
    
    @Override
    public int read() {
        if (off < len) {
            off++;
            return 0xFF & buf[off-1];
        }
        else {
            return -1;
        }
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new IOException("No data");
        }
        else if ((off < 0) || (len < 0)) {
            throw new IOException ("Offset out of bounds");
        }
        if (this.off >= this.len) {
            return -1;
        }
        if ((this.off + len) > this.len) {
            len = this.len - this.off;  // Remainder of buffer
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(buf, this.off, b, off, len);
        this.off += len;
        return len;
    }
    
    @Override
    public long skip(long n) {
        if ((this.off + n) > this.len) {
            n = this.len - this.off;
        }
        if (n < 0) {
            return 0;
        }
        this.off += n;
        return n;
    }
}
