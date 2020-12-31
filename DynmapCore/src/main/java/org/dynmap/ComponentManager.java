package org.dynmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public class ComponentManager {
    public Set<Component> components = new HashSet<Component>();
    public Map<String, List<Component>> componentLookup = new HashMap<String, List<Component>>();
    
    public void add(Component c) {
        if (components.add(c)) {
            String key = c.getClass().toString();
            List<Component> clist = componentLookup.get(key);
            if (clist == null) {
                clist = new ArrayList<Component>();
                componentLookup.put(key, clist);
            }
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
    
    public Iterable<Component> getComponents(Class<? extends Component> c) {
        List<Component> list = componentLookup.get(c.toString());
        if (list == null)
            return new ArrayList<Component>();
        return list;
    }

    public Boolean isLoaded(Class<? extends Component> c){
        return StreamSupport.stream(getComponents(c).spliterator(), false).count() > 0;
    }
}
