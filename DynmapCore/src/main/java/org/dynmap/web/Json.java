package org.dynmap.web;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Json {
    public static String stringifyJson(Object o) {
        StringBuilder sb = new StringBuilder();
        appendJson(o, sb);
        return sb.toString();
    }

    public static void escape(String s, StringBuilder s2) {
        for(int i=0;i<s.length();i++){
            char ch=s.charAt(i);
            switch(ch){
            case '"':
                s2.append("\\\"");
                break;
            case '\\':
                s2.append("\\\\");
                break;
            case '\b':
                s2.append("\\b");
                break;
            case '\f':
                s2.append("\\f");
                break;
            case '\n':
                s2.append("\\n");
                break;
            case '\r':
                s2.append("\\r");
                break;
            case '\t':
                s2.append("\\t");
                break;
            case '/':
                s2.append("\\/");
                break;
            default:
                if((ch>='\u0000' && ch<='\u001F') || (ch>='\u007F')){
                    String ss=Integer.toHexString(ch);
                    s2.append("\\u");
                    for(int k=0;k<4-ss.length();k++){
                        s2.append('0');
                    }
                    s2.append(ss.toUpperCase());
                }
                else{
                    s2.append(ch);
                }
            }
        }//for
    }

    public static void appendJson(Object o, StringBuilder s) {
        if (o == null) {
            s.append("null");
        } else if (o instanceof Boolean) {
            s.append(((Boolean) o) ? "true" : "false");
        } else if (o instanceof String) {
            s.append("\"");
            escape((String)o, s);
            s.append("\"");
        } else if (o instanceof Integer || o instanceof Long) {
            s.append(o.toString());
        } else if (o instanceof Float || o instanceof Double) {
            s.append(String.format(Locale.US, "%.2f",((Number)o).doubleValue()));
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
            s.append("}\n");
        } else if (o instanceof List<?>) {
            List<?> l = (List<?>) o;
            s.append("[");
            int count = 0;
            for (int i = 0; i < l.size(); i++) {
                if (count++ > 0) s.append(",");
                appendJson(l.get(i), s);
            }
            s.append("]\n");
        } else if (o.getClass().isArray()) {
            int length = Array.getLength(o);
            s.append("[");
            int count = 0;
            for (int i = 0; i < length; i++) {
                if (count++ > 0) s.append(",");
                appendJson(Array.get(o, i), s);
            }
            s.append("]\n");
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
            s.append("}\n");
        } else {
            s.append("undefined");
        }
    }
}
