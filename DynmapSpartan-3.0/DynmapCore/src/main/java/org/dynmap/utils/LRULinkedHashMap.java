package org.dynmap.utils;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class LRULinkedHashMap<T, K> extends LinkedHashMap<T, K> {
    private int limit;
    public LRULinkedHashMap(int lim) {
        super(16, (float)0.75, true);
        limit = lim;
    }
    protected boolean removeEldestEntry(Map.Entry<T, K> last) {
        return(size() >= limit);
    }
}
