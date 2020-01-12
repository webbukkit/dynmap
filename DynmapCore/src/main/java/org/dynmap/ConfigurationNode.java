package org.dynmap;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigurationNode implements Map<String, Object> {
    public Map<String, Object> entries;
    private File f;
    private Yaml yaml;

    public ConfigurationNode() {
        entries = new LinkedHashMap<>();
    }

    private void initparse() {
        if(yaml == null) {
            DumperOptions options = new DumperOptions();

            options.setIndent(4);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setVersion(DumperOptions.Version.V1_1);

            yaml = new Yaml(new SafeConstructor(), new EmptyNullRepresenter(), options);
        }
    }

    public ConfigurationNode(File f) {
        this.f = f;
        entries = new LinkedHashMap<>();
    }
    
    public ConfigurationNode(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException();
        }
        entries = map;
    }
    
    public ConfigurationNode(InputStream in) {
        load(in);
    }

    @SuppressWarnings("unchecked")
    public boolean load(InputStream in) {
        initparse();

        Object o = yaml.load(new UnicodeReader(in));
        if ((o instanceof Map))
            entries = (Map<String, Object>) o;
        return (entries != null);
    }
    
    @SuppressWarnings("unchecked")
    public boolean load() {
        initparse();

        try (FileInputStream fis = new FileInputStream(f)) {
            Object o = yaml.load(new UnicodeReader(fis));
            if ((o != null) && (o instanceof Map))
                entries = (Map<String, Object>) o;
            fis.close();
        } catch (YAMLException e) {
            Log.severe("Error parsing " + f.getPath() + ". Use http://yamllint.com to debug the YAML syntax.");
            throw e;
        } catch (IOException iox) {
            Log.severe("Error reading " + f.getPath());
            return false;
        }
        return (entries != null);
    }

    public boolean save() {
        return save(f);
    }
    
    public boolean save(File file) {
        initparse();

        FileOutputStream stream = null;

        File parent = file.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        try {
            stream = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            yaml.dump(entries, writer);
            return true;
        } catch (IOException ignored) {
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException ignored) {
            }
        }
        return false;
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
        ArrayList<String> strings = new ArrayList<>();
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
            return (List<T>) getObject(path, null);
        } catch (ClassCastException e) {
            try {
                T o = (T)getObject(path, null);
                if (o == null) {
                    return new ArrayList<>();
                }
                ArrayList<T> al = new ArrayList<>();
                al.add(o);
                return al;
            } catch (ClassCastException e2) {
                return new ArrayList<>();
            }
        }
    }

    public List<Map<String,Object>> getMapList(String path) {
        return getList(path);
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

        if (o == null)
            return new ArrayList<>();

        ArrayList<ConfigurationNode> nodes = new ArrayList<>();
        for (Object i : o) {
            if (i instanceof Map<?, ?>) {
                Map<String, Object> map;
                try {
                    map = (Map<String, Object>) i;
                } catch (ClassCastException e) {
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

    private static Object copyValue(Object v) {
        if (v instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mv = (Map<String, Object>) v;
            LinkedHashMap<String, Object> newv = new LinkedHashMap<>();
            for (Map.Entry<String, Object> me : mv.entrySet()) {
                newv.put(me.getKey(), copyValue(me.getValue()));
            }
            return newv;
        } else if (v instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> lv = (List<Object>) v;
            ArrayList<Object> newv = new ArrayList<>();
            for (Object o : lv) {
                newv.add(copyValue(o));
            }
            return newv;
        } else {
            return v;
        }
    }

    private static void extendMap(Map<String, Object> left, Map<String, Object> right) {
        ConfigurationNode original = new ConfigurationNode(left);
        for (Map.Entry<String, Object> entry : right.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            original.put(key, copyValue(value));
        }
    }
    
    public <T> T createInstance(Class<?>[] constructorParameters, Object[] constructorArguments) {
        String typeName = getString("class");
        try {
            Class<?> mapTypeClass = Class.forName(typeName);

            Class<?>[] constructorParameterWithConfiguration = new Class<?>[constructorParameters.length + 1];
            System.arraycopy(constructorParameters, 0, constructorParameterWithConfiguration, 0, constructorParameters.length);
            constructorParameterWithConfiguration[constructorParameterWithConfiguration.length - 1] = ConfigurationNode.class;

            Object[] constructorArgumentsWithConfiguration = new Object[constructorArguments.length + 1];
            System.arraycopy(constructorArguments, 0, constructorArgumentsWithConfiguration, 0, constructorArguments.length);
            constructorArgumentsWithConfiguration[constructorArgumentsWithConfiguration.length - 1] = this;
            Constructor<?> constructor = mapTypeClass.getConstructor(constructorParameterWithConfiguration);
            @SuppressWarnings("unchecked")
            T t = (T) constructor.newInstance(constructorArgumentsWithConfiguration);
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
        List<T> instances = new ArrayList<>();
        for(ConfigurationNode node : nodes) {
            T instance = node.createInstance(constructorParameters, constructorArguments);
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
    public void putAll(@NotNull Map<? extends String, ?> m) {
        entries.putAll(m);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return entries.keySet();
    }

    @NotNull
    @Override
    public Collection<Object> values() {
        return entries.values();
    }

    @NotNull
    @Override
    public Set<java.util.Map.Entry<String, Object>> entrySet() {
        return entries.entrySet();
    }

    private static class EmptyNullRepresenter extends Representer {

        public EmptyNullRepresenter() {
            super();
            this.nullRepresenter = new EmptyRepresentNull();
        }

        protected class EmptyRepresentNull implements Represent {
            public Node representData(Object data) {
                return representScalar(Tag.NULL, ""); // Changed "null" to "" so as to avoid writing nulls
            }
        }

        // Code borrowed from snakeyaml (http://code.google.com/p/snakeyaml/source/browse/src/test/java/org/yaml/snakeyaml/issues/issue60/SkipBeanTest.java)
        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            NodeTuple tuple = super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            Node valueNode = tuple.getValueNode();
            if (valueNode instanceof CollectionNode) {
                // Removed null check
                if (Tag.SEQ.equals(valueNode.getTag())) {
                    SequenceNode seq = (SequenceNode) valueNode;
                    if (seq.getValue().isEmpty()) {
                        return null; // skip empty lists
                    }
                }
                if (Tag.MAP.equals(valueNode.getTag())) {
                    MappingNode seq = (MappingNode) valueNode;
                    if (seq.getValue().isEmpty()) {
                        return null; // skip empty maps
                    }
                }
            }
            return tuple;
        }
        // End of borrowed code
    }

}
