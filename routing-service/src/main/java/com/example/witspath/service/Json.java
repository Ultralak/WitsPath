package com.example.witspath.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader and writer, so the service needs no dependencies. */
final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.ws();
        if (j.i != text.length()) throw new IllegalArgumentException("Trailing characters in JSON");
        if (!(v instanceof Map)) throw new IllegalArgumentException("Expected a JSON object");
        return (Map<String, Object>) v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private char peek() {
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        return s.charAt(i);
    }

    private Object value() {
        ws();
        char c = peek();
        if (c == '{') {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                if (peek() != ':') throw new IllegalArgumentException("Expected ':' at " + i);
                i++;
                m.put(k, value());
                ws();
                char n = s.charAt(i++);
                if (n == '}') return m;
                if (n != ',') throw new IllegalArgumentException("Expected ',' or '}' at " + (i - 1));
            }
        }
        if (c == '[') {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char n = s.charAt(i++);
                if (n == ']') return l;
                if (n != ',') throw new IllegalArgumentException("Expected ',' or ']' at " + (i - 1));
            }
        }
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalArgumentException("Unexpected character at " + i);
        return Double.parseDouble(s.substring(st, i));
    }

    private String string() {
        if (peek() != '"') throw new IllegalArgumentException("Expected string at " + i);
        StringBuilder b = new StringBuilder();
        i++;
        while (peek() != '"') {
            char c = s.charAt(i++);
            if (c == '\\') {
                char n = s.charAt(i++);
                switch (n) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(n);
                }
            } else {
                b.append(c);
            }
        }
        i++;
        return b.toString();
    }

    static String write(Object o) {
        StringBuilder b = new StringBuilder();
        write(o, b);
        return b.toString();
    }

    private static void write(Object o, StringBuilder b) {
        if (o == null) {
            b.append("null");
        } else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), b);
                b.append(':');
                write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object v : (List<?>) o) {
                if (!first) b.append(',');
                first = false;
                write(v, b);
            }
            b.append(']');
        } else if (o instanceof String) {
            quote((String) o, b);
        } else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(d);
        } else {
            b.append(o); // Integer, Long, Boolean
        }
    }

    private static void quote(String v, StringBuilder b) {
        b.append('"');
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
