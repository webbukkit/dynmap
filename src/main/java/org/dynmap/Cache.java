package org.dynmap;

import java.util.HashMap;

public class Cache<K, V> {
    private final int size;
    private int len;

    private CacheNode head;
    private CacheNode tail;

    private class CacheNode {
        public CacheNode prev;
        public CacheNode next;
        public K key;
        public V value;

        public CacheNode(K key, V value) {
            this.key = key;
            this.value = value;
            prev = null;
            next = null;
        }

        public void unlink() {
            if (prev == null) {
                head = next;
            } else {
                prev.next = next;
            }

            if (next == null) {
                tail = prev;
            } else {
                next.prev = prev;
            }

            prev = null;
            next = null;

            len--;
        }

        public void append() {
            if (tail == null) {
                head = this;
                tail = this;
            } else {
                tail.next = this;
                prev = tail;
                tail = this;
            }

            len++;
        }
    }

    private HashMap<K, CacheNode> map;

    public Cache(int size) {
        this.size = size;
        len = 0;

        head = null;
        tail = null;

        map = new HashMap<K, CacheNode>();
    }

    /*
     * returns value for key, if key exists in the cache otherwise null
     */
    public V get(K key) {
        CacheNode n = map.get(key);
        if (n == null)
            return null;
        return n.value;
    }

    /*
     * puts a new key-value pair in the cache if the key existed already, the
     * value is updated, and the old value is returned if the key didn't exist,
     * it is added; the oldest value (now pushed out of the cache) may be
     * returned, or null if the cache isn't yet full
     */
    public V put(K key, V value) {
        CacheNode n = map.get(key);
        if (n == null) {
            V ret = null;

            if (len >= size) {
                CacheNode first = head;
                first.unlink();
                map.remove(first.key);
                ret = first.value;
            }

            CacheNode add = new CacheNode(key, value);
            add.append();
            map.put(key, add);

            return ret;
        } else {
            n.unlink();
            V old = n.value;
            n.value = value;
            n.append();
            return old;
        }
    }
}
