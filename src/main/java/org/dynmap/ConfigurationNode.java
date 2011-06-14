package org.dynmap;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigurationNode implements Map<String, Object> {
    public Map<String, Object> entries;
    
    public ConfigurationNode() {
        entries = new HashMap<String, Object>();
    }
    
    public ConfigurationNode(org.bukkit.util.config.ConfigurationNode node) {
        entries = new HashMap<String, Object>();
        for(String key : node.getKeys(null)) {
            entries.put(key, node.getProperty(key));
        }
    }
    
    public ConfigurationNode(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException();
        }
        entries = map;
    }
    
    @SuppressWarnings("unchecked")
    public Object getObject(String path) {
        if (path.isEmpty())
            return entries;
        int separator = path.indexOf('/');
        if (separator < 0)
            return get(path);
        String localKey = path.substring(0, separator);
        Object subvalue = get(localKey);
        if (subvalue == null)
            return null;
        if (!(subvalue instanceof Map<?, ?>))
            return null;
        Map<String, Object> submap;
        try {
            submap = (Map<String, Object>)subvalue;
        } catch (ClassCastException e) {
            return null;
        }
        
        String subpath = path.substring(separator + 1);
        return new ConfigurationNode(submap).getObject(subpath);
        
    }
    
    public Object getObject(String path, Object def) {
        Object o = getObject(path);
        if (o == null)
            return def;
        return o;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getGeneric(String path, T def) {
        Object o = getObject(path, def);
        try {
            return (T)o;
        } catch(ClassCastException e) {
            return def;
        }
    }
    
    public int getInteger(String path, int def) {
        return Integer.parseInt(getObject(path, def).toString());
    }
    
    public double getLong(String path, long def) {
        return Long.parseLong(getObject(path, def).toString());
    }
    
    public float getFloat(String path, float def) {
        return Float.parseFloat(getObject(path, def).toString());
    }
    
    public double getDouble(String path, double def) {
        return Double.parseDouble(getObject(path, def).toString());
    }
    
    public boolean getBoolean(String path, boolean def) {
        return Boolean.parseBoolean(getObject(path, def).toString());
    }
    
    public String getString(String path) {
        return getString(path, null);
    }
    
    public List<String> getStrings(String path, List<String> def) {
        Object o = getObject(path);
        if (!(o instanceof List<?>)) {
            return def;
        }
        ArrayList<String> strings = new ArrayList<String>();
        for(Object i : (List<?>)o) {
            strings.add(i.toString());
        }
        return strings;
    }
    
    public String getString(String path, String def) {
        Object o = getObject(path, def);
        if (o == null)
            return null;
        return o.toString();
    }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String path) {
        try {
            List<T> list = (List<T>)getObject(path, null);
            return list;
        } catch (ClassCastException e) {
            try {
                T o = (T)getObject(path, null);
                if (o == null) {
                    return new ArrayList<T>();
                }
                ArrayList<T> al = new ArrayList<T>();
                al.add(o);
                return al;
            } catch (ClassCastException e2) {
                return new ArrayList<T>();
            }
        }
    }
    
    public ConfigurationNode getNode(String path) {
        Map<String, Object> v = null;
        v = getGeneric(path, v);
        if (v == null)
            return null;
        return new ConfigurationNode(v);
    }
    
    @SuppressWarnings("unchecked")
    public List<ConfigurationNode> getNodes(String path) {
        List<Object> o = getList(path);

        if(o == null)
            return new ArrayList<ConfigurationNode>();
        
        ArrayList<ConfigurationNode> nodes = new ArrayList<ConfigurationNode>();
        for(Object i : (List<?>)o) {
            if (i instanceof Map<?, ?>) {
                Map<String, Object> map;
                try {
                    map = (Map<String, Object>)i;
                } catch(ClassCastException e) {
                    continue;
                }
                nodes.add(new ConfigurationNode(map));
            }
        }
        return nodes;
    }
    
    public void extend(Map<String, Object> other) {
        if (other != null)
            extendMap(this, other);
    }
    
    @SuppressWarnings("unchecked")
    private final static void extendMap(Map<String, Object> left, Map<String, Object> right) {
        ConfigurationNode original = new ConfigurationNode(left);
        for(Map.Entry<String, Object> entry : right.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                ConfigurationNode subnode = original.getNode(key);
                if (subnode == null) {
                    original.put(key, subnode = new ConfigurationNode());
                }
                extendMap(subnode, (Map<String, Object>)value);
            } else {
                original.put(key, value);
            }
        }
    }
    
    public <T> T createInstance(Class<?>[] constructorParameters, Object[] constructorArguments) {
        String typeName = getString("class");
        try {
            Class<?> mapTypeClass = Class.forName(typeName);
        
            Class<?>[] constructorParameterWithConfiguration = new Class<?>[constructorParameters.length+1];
            for(int i = 0; i < constructorParameters.length; i++) { constructorParameterWithConfiguration[i] = constructorParameters[i]; }
            constructorParameterWithConfiguration[constructorParameterWithConfiguration.length-1] = ConfigurationNode.class;
            
            Object[] constructorArgumentsWithConfiguration = new Object[constructorArguments.length+1];
            for(int i = 0; i < constructorArguments.length; i++) { constructorArgumentsWithConfiguration[i] = constructorArguments[i]; }
            constructorArgumentsWithConfiguration[constructorArgumentsWithConfiguration.length-1] = this;
            Constructor<?> constructor = mapTypeClass.getConstructor(constructorParameterWithConfiguration);
            @SuppressWarnings("unchecked")
            T t = (T)constructor.newInstance(constructorArgumentsWithConfiguration);
            return t;
        } catch (Exception e) {
            // TODO: Remove reference to MapManager.
            Log.severe("Error loading maptype", e);
            e.printStackTrace();
        }
        return null;
    }
    
    public <T> List<T> createInstances(String path, Class<?>[] constructorParameters, Object[] constructorArguments) {
        List<ConfigurationNode> nodes = getNodes(path);
        List<T> instances = new ArrayList<T>();
        for(ConfigurationNode node : nodes) {
            T instance = node.<T>createInstance(constructorParameters, constructorArguments);
            if (instance != null) {
                instances.add(instance);
            }
        }
        return instances;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return entries.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return entries.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return entries.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return entries.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return entries.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        entries.putAll(m);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public Set<String> keySet() {
        return entries.keySet();
    }

    @Override
    public Collection<Object> values() {
        return entries.values();
    }

    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return entries.entrySet();
    }
}
