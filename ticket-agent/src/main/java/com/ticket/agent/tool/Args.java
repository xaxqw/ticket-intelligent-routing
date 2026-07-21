package com.ticket.agent.tool;

import java.util.Map;

/** 工具入参读取助手（带默认值与类型容错）。 */
public final class Args {

    private Args() {}

    public static String str(Map<String, Object> args, String key, String def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        return v.toString().trim();
    }

    public static int integer(Map<String, Object> args, String key, int def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean bool(Map<String, Object> args, String key, boolean def) {
        if (args == null) return def;
        Object v = args.get(key);
        if (v == null) return def;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString().trim());
    }
}
