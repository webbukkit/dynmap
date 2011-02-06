package org.dynmap.web;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;

public class Json {
    public static String stringifyJson(Object o) {
        if (o == null) {
            return "null";
        } else if (o instanceof Boolean) {
            return ((Boolean) o) ? "true" : "false";
        } else if (o instanceof String) {
            return "\"" + ((String)o).replace("\"", "\\\"") + "\"";
        } else if (o instanceof Integer || o instanceof Long || o instanceof Float || o instanceof Double) {
            return o.toString();
        } else if (o instanceof LinkedHashMap<?, ?>) {
            LinkedHashMap<?, ?> m = (LinkedHashMap<?, ?>) o;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Object key : m.keySet()) {
                if (first)
                    first = false;
                else
                    sb.append(",");

                sb.append(stringifyJson(key));
                sb.append(": ");
                sb.append(stringifyJson(m.get(key)));
            }
            sb.append("}");
            return sb.toString();
        } else if (o instanceof List<?>) {
            List<?> l = (List<?>) o;
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int count = 0;
            for (int i = 0; i < l.size(); i++) {
                if (count++ > 0) sb.append(",");
                sb.append(stringifyJson(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (o.getClass().isArray()) {
            int length = Array.getLength(o);
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int count = 0;
            for (int i = 0; i < length; i++) {
                if (count++ > 0) sb.append(",");
                sb.append(stringifyJson(Array.get(o, i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (o instanceof Object) /* TODO: Always true, maybe interface? */ {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            
            Class<?> c = o.getClass();
            for(Field field : c.getFields()) {
                if (!Modifier.isPublic(field.getModifiers()))
                    continue;
                String fieldName = field.getName();
                Object fieldValue;
                try {
                     fieldValue = field.get(o);
                } catch (IllegalArgumentException e) {
                    continue;
                } catch (IllegalAccessException e) {
                    continue;
                }
                
                if (first)
                    first = false;
                else
                    sb.append(",");
                sb.append(stringifyJson(fieldName));
                sb.append(": ");
                sb.append(stringifyJson(fieldValue));
            }
            sb.append("}");
            return sb.toString();
        } else {
            return "undefined";
        }
    }
}
