package org.dynmap.web;

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
            int count = 0;
            for (int i = 0; i < l.size(); i++) {
                sb.append(count++ == 0 ? "[" : ",");
                sb.append(stringifyJson(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else {
            return "undefined";
        }
    }
}
