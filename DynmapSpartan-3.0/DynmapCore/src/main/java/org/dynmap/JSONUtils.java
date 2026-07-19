package org.dynmap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class JSONUtils {
    
    // Gets a value at the specified path.
    public static Object g(JSONObject o, String path) {
        int index = path.indexOf('/');
        if (index == -1) {
            return o.get(path);
        } else {
            String key = path.substring(0, index);
            String subpath = path.substring(index+1);
            Object oo = o.get(key);
            JSONObject subobject;
            if (oo == null) {
                return null;
            } else /*if (oo instanceof JSONObject)*/ {
                subobject = (JSONObject)o;
            }
            return g(subobject, subpath);
        }
    }
    
    // Sets a value on the specified path. If JSONObjects inside the path are missing, they'll be created.
    @SuppressWarnings("unchecked")
    public static void s(JSONObject o, String path, Object value) {
        int index = path.indexOf('/');
        if (index == -1) {
            o.put(path, value);
        } else {
            String key = path.substring(0, index);
            String subpath = path.substring(index+1);
            Object oo = o.get(key);
            JSONObject subobject;
            if (oo == null) {
                subobject = new JSONObject();
                o.put(key, subobject);
            } else /*if (oo instanceof JSONObject)*/ {
                subobject = (JSONObject)oo;
            }
            s(subobject, subpath, value);
        }
    }
    
    // Adds a value to the list at the specified path. If the list does not exist, it will be created.
    @SuppressWarnings("unchecked")
    public static void a(JSONObject o, String path, Object value) {
        Object oo = g(o, path);
        JSONArray array;
        if (oo == null) {
            array =new JSONArray();
            s(o, path, array);
        } else {
            array = (JSONArray)oo;
        }
        if(value != null)
            array.add(value);
    }
    
    // Simply creates a JSONArray.
    @SuppressWarnings("unchecked")
    public static JSONArray l(Object... items) {
        JSONArray arr = new JSONArray();
        for(Object item : items) {
            arr.add(item);
        }
        return arr;
    }
}
