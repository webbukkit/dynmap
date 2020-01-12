package org.dynmap;

import java.util.*;

public class ComponentManager {
    public Set<Component> components = new HashSet<>();
    public Map<String, List<Component>> componentLookup = new HashMap<>();

    public void add(Component c) {
        if (components.add(c)) {
            String key = c.getClass().toString();
            List<Component> clist = componentLookup.computeIfAbsent(key, k -> new ArrayList<>());
            clist.add(c);
        }
    }

    public void remove(Component c) {
        if (components.remove(c)) {
            String key = c.getClass().toString();
            List<Component> clist = componentLookup.get(key);
            if (clist != null) {
                clist.remove(c);
            }
        }
    }
    
    public void clear() {
        componentLookup.clear();
        components.clear();
    }
    
    public Iterable<Component> getComponents(Class<Component> c) {
        List<Component> list = componentLookup.get(c.toString());
        if (list == null)
            return new ArrayList<>();
        return list;
    }
}
