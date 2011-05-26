package org.dynmap.web;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public class Json {
    public static String stringifyJson(Object o) {
        StringBuilder sb = new StringBuilder();
        appendJson(o, sb);
        return sb.toString();
    }

    public static void appendJson(Object o, StringBuilder s) {
        if (o == null) {
            s.append("null");
        } else if (o instanceof Boolean) {
            s.append(((Boolean) o) ? "true" : "false");
        } else if (o instanceof String) {
            s.append("\"" + ((String)o).replace("\"", "\\\"") + "\"");
        } else if (o instanceof Integer || o instanceof Long || o instanceof Float || o instanceof Double) {
            s.append(o.toString());
        } else if (o instanceof Map<?, ?>) {
            Map<?, ?> m = (Map<?, ?>) o;
            s.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (first)
                    first = false;
                else
                    s.append(",");

                appendJson(entry.getKey(), s);
                s.append(": ");
                appendJson(entry.getValue(), s);
            }
            s.append("}");
        } else if (o instanceof List<?>) {
            List<?> l = (List<?>) o;
            s.append("[");
            int count = 0;
            for (int i = 0; i < l.size(); i++) {
                if (count++ > 0) s.append(",");
                appendJson(l.get(i), s);
            }
            s.append("]");
        } else if (o.getClass().isArray()) {
            int length = Array.getLength(o);
            s.append("[");
            int count = 0;
            for (int i = 0; i < length; i++) {
                if (count++ > 0) s.append(",");
                appendJson(Array.get(o, i), s);
            }
            s.append("]");
        } else if (o instanceof Object) /* TODO: Always true, maybe interface? */ {
            s.append("{");
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
                    s.append(",");
                appendJson(fieldName, s);
                s.append(": ");
                appendJson(fieldValue, s);
            }
            s.append("}");
        } else {
            s.append("undefined");
        }
    }
}
