/*
 * Json.java
 * ---------
 * A TINY helper for building JSON strings by hand, so we do not need any
 * external library (like Jackson or Gson). For a beginner project this keeps
 * everything in plain Java and easy to follow.
 *
 * It does two jobs:
 *   1. Build little pieces of JSON:  "key":"value"  or  "key":number
 *   2. "Escape" special characters in strings so the JSON stays valid
 *      (for example a " inside a value must be written as \").
 *
 * All methods are "static", meaning you call them on the class directly, e.g.
 *   Json.pair("channel", "web")   ->   "channel":"web"
 */
public class Json {

    /*
     * pair(): build a   "key":"value"   piece where the value is TEXT.
     * The value is wrapped in double quotes and escaped.
     */
    public static String pair(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
    }

    /*
     * pairNum(): build a   "key":value   piece where the value is a NUMBER.
     * Numbers are NOT wrapped in quotes in JSON.
     * We format money-style numbers to 2 decimal places so 55 shows as 55.00.
     */
    public static String pairNum(String key, double value) {
        return "\"" + escape(key) + "\":" + String.format("%.2f", value);
    }

    /*
     * pairRaw(): build a   "key":rawValue   piece where rawValue is ALREADY
     * valid JSON (for example a whole object or an array we built elsewhere).
     * We do NOT add quotes or escape it.
     */
    public static String pairRaw(String key, String rawJson) {
        return "\"" + escape(key) + "\":" + rawJson;
    }

    /*
     * escape(): make a piece of text safe to put inside JSON double quotes.
     * We handle the common cases a beginner is likely to meet.
     */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\"': sb.append("\\\""); break; // a double quote
                case '\\': sb.append("\\\\"); break; // a backslash
                case '\n': sb.append("\\n");  break; // a new line
                case '\r': sb.append("\\r");  break; // a carriage return
                case '\t': sb.append("\\t");  break; // a tab
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}
