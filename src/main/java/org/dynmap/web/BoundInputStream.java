package org.dynmap.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class BoundInputStream extends InputStream {
    protected static final Logger log = Logger.getLogger("Minecraft");
    protected static final String LOG_PREFIX = "[dynmap] ";
    private InputStream base;
    private long bound;

    public BoundInputStream(InputStream base, long bound) {
        this.base = base;
        this.bound = bound;
    }

    @Override
    public int read() throws IOException {
        if (bound <= 0) return -1;
        int r = base.read();
        if (r >= 0)
            bound--;
        return r;
    }

    @Override
    public int available() throws IOException {
        return (int)Math.min(base.available(), bound);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bound <= 0) return -1;
        len = (int)Math.min(bound, len);
        int r = base.read(b, off, len);
        bound -= r;
        return r;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public long skip(long n) throws IOException {
        long r = base.skip(Math.min(bound, n));
        bound -= r;
        return r;
    }

    @Override
    public void close() throws IOException {
        base.close();
    }
}
