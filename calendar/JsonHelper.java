package calendar;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    // {"success": true ...} 에서 success 값
    public static boolean getBoolean(String json, String key, boolean defaultVal) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return defaultVal;

        idx = json.indexOf(':', idx);
        if (idx < 0) return defaultVal;

        String sub = json.substring(idx + 1).trim();

        if (sub.startsWith("true")) return true;
        if (sub.startsWith("false")) return false;
        return defaultVal;
    }

    // {"message":"텍스트"} 에서 message
    public static String getString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        idx = json.indexOf(':', idx);
        if (idx < 0) return null;

        idx = json.indexOf('"', idx + 1);
        if (idx < 0) return null;

        int end = json.indexOf('"', idx + 1);
        if (end < 0) return null;

        String raw = json.substring(idx + 1, end);
        return raw.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // {"id": 3} 또는 {"user_id": 12, ...} 에서 숫자
    public static int getInt(String json, String key, int defaultVal) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return defaultVal;

        idx = json.indexOf(':', idx);
        if (idx < 0) return defaultVal;

        int i = idx + 1;
        // 공백 스킵
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

        int start = i;

        if (i < json.length() && (json.charAt(i) == '-' || Character.isDigit(json.charAt(i)))) {
            i++;
        } else {
            return defaultVal;
        }

        while (i < json.length() && Character.isDigit(json.charAt(i))) {
            i++;
        }

        String numberStr = json.substring(start, i);
        try {
            return Integer.parseInt(numberStr);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // {"calendars":[{...},{...}]} 에서 {...} 배열 추출
    public static String[] getObjectsArray(String json, String arrayKey) {
        String pattern = "\"" + arrayKey + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return new String[0];

        idx = json.indexOf('[', idx);
        if (idx < 0) return new String[0];

        int depth = 0;
        int i = idx + 1;
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;

        for (; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && json.charAt(i - 1) != '\\') {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{') {
                    if (depth == 0) current = new StringBuilder();
                    depth++;
                } else if (c == '}') {
                    depth--;
                    current.append(c);
                    if (depth == 0) {
                        list.add(current.toString());
                        continue;
                    }
                } else if (c == ']' && depth == 0) {
                    break;
                }
            }

            if (depth > 0) {
                current.append(c);
            }
        }

        return list.toArray(new String[0]);
    }
}
