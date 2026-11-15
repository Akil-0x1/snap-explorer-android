package local.snapexplorer.mobile;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Offline, conservative summaries of exported record datasets. Only actual record
 * objects count: arrays of scalar values and wrapper labels are not invented events.
 */
public final class MetadataAnalytics {
    private static final int MAX_DEPTH = 32, MAX_RECORDS = 200000, MAX_NODES = 1000000;
    private static final int MAX_LABEL = 256;
    private static final String[] DATES = {"Date", "Created", "Timestamp", "Time", "Creation Time"};
    private static final String[] CATEGORIES = {"Media Type", "Type", "Event", "Action", "Status", "Source"};
    private static final String[] CONTACTS = {"From", "Sender", "Username", "Friend", "Recipient"};
    public static final class Result {
        public final Map<String,Long> daily = new LinkedHashMap<>();
        public final Map<String,Long> categories = new LinkedHashMap<>();
        public final Map<String,Long> contacts = new LinkedHashMap<>();
        public long records;
        public String label = "";
    }
    private static final class State {
        final Result result = new Result();
        final Map<String,Long> days = new TreeMap<>(), kinds = new TreeMap<>(), people = new TreeMap<>();
        final ZoneId zone;
        int nodes;boolean streaming;
        State(ZoneId zone) { this.zone = zone; }
    }
    public static Result analyze(String filename, String json, ZoneId zone) throws Exception {
        if (json == null) throw new IOException("Missing metadata payload.");
        if (json.length() > ArchiveImporter.MAX_ENTRY) throw new IOException("Metadata exceeds the 32 MB analysis limit.");
        checkDepth(json);
        State state = new State(zone == null ? ZoneId.of("UTC") : zone);
        state.result.label = label(filename);
        String trimmed = json.trim();
        if (trimmed.startsWith("{")) walk(new JSONObject(trimmed), state, 0, false);
        else if (trimmed.startsWith("[")) walk(new JSONArray(trimmed), state, 0, false);
        else throw new IOException("Metadata must contain a JSON object or array.");
        state.result.daily.putAll(state.days);
        copyRanked(state.kinds, state.result.categories);
        copyRanked(state.people, state.result.contacts);
        return state.result;
    }
    public static Result analyze(String filename,java.io.Reader reader,ZoneId zone)throws Exception{
        State state=new State(zone==null?ZoneId.of("UTC"):zone);state.streaming=true;state.result.label=label(filename);
        JsonRecords.read(reader,(group,record)->{state.nodes=0;walk(record,state,0,true);});
        state.result.daily.putAll(state.days);copyRanked(state.kinds,state.result.categories);copyRanked(state.people,state.result.contacts);return state.result;
    }
    private static void walk(Object value, State state, int depth, boolean arrayElement) throws Exception {
        if (depth > MAX_DEPTH) throw new IOException("Metadata nesting exceeds the analysis limit.");
        if (++state.nodes > MAX_NODES) throw new IOException("Metadata has too many nested values to analyze.");
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject || item instanceof JSONArray) walk(item, state, depth + 1, true);
            }
            return;
        }
        if (!(value instanceof JSONObject)) return;
        JSONObject object = (JSONObject) value;
        boolean known = hasKnownField(object);
        if (!arrayElement && hasNestedRecords(object) && firstScalar(object, DATES).isEmpty()) known = false;
        // An object already identified as a record owns its nested data. Nested
        // location structures and attachments must not inflate the event count.
        if (known || (arrayElement && hasScalar(object) && !hasNestedRecords(object))) {
            record(object, state);
            return;
        }
        // Objects containing only child objects/arrays are structural wrappers.
        for (String key : sortedKeys(object)) {
            Object child = object.opt(key);
            if (child instanceof JSONObject || child instanceof JSONArray) walk(child, state, depth + 1, false);
        }
    }
    private static boolean hasKnownField(JSONObject object) {
        return !firstScalar(object, DATES).isEmpty() || !firstScalar(object, CATEGORIES).isEmpty() || !firstScalar(object, CONTACTS).isEmpty();
    }
    private static boolean hasScalar(JSONObject object) {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object value = object.opt(keys.next());
            if (value instanceof String || value instanceof Number || value instanceof Boolean) return true;
        }
        return false;
    }
    private static boolean hasNestedRecords(JSONObject object) {
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object child = object.opt(keys.next());
            if (child instanceof JSONArray && ((JSONArray) child).length() > 0) {
                JSONArray array = (JSONArray) child;
                for (int i = 0; i < array.length(); i++) if (array.opt(i) instanceof JSONObject || array.opt(i) instanceof JSONArray) return true;
            }
        }
        return false;
    }
    private static void record(JSONObject object, State state) throws Exception {
        if (++state.result.records > (state.streaming?5000000:MAX_RECORDS)) throw new IOException("Metadata exceeds the 200,000-record analysis limit.");
        long epoch = dateOf(object);
        if (epoch != Long.MIN_VALUE) {
            try { increment(state.days, DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(epoch).atZone(state.zone))); }
            catch (java.time.DateTimeException ignored) { /* Preserve record count without inventing an invalid day. */ }
        }
        String category = firstScalar(object, CATEGORIES);
        if (!category.isEmpty()) increment(state.kinds, bounded(category));
        String contact = firstScalar(object, CONTACTS);
        if (!contact.isEmpty()) increment(state.people, bounded(contact));
    }
    private static long dateOf(JSONObject object) {
        List<String> keys = sortedKeys(object);
        for (String candidate : DATES) for (String key : keys) if (candidate.equalsIgnoreCase(key)) {
            Object raw = object.opt(key);
            if (raw instanceof Number) {
                double number = ((Number) raw).doubleValue();
                if (Double.isNaN(number) || Double.isInfinite(number) || number != Math.rint(number)) continue;
                long epoch = ((Number) raw).longValue();
                try { return epoch > -100000000000L && epoch < 100000000000L ? Math.multiplyExact(epoch,1000L) : epoch; }
                catch (ArithmeticException ignored) { continue; }
            }
            long epoch = parseDate(scalar(raw)); if (epoch != Long.MIN_VALUE) return epoch;
        }
        return Long.MIN_VALUE;
    }
    private static long parseDate(String value) {
        if (value.isEmpty()) return Long.MIN_VALUE;
        // Keep epoch zero distinct from an unrecognised date. Negative Unix
        // timestamps are allowed only when explicitly represented numerically.
        if (value.matches("-?[0-9]{10}|-?[0-9]{13}")) {
            try {
                long number = Long.parseLong(value);
                return value.replace("-", "").length() == 10 ? Math.multiplyExact(number,1000L) : number;
            } catch (ArithmeticException ignored) { return Long.MIN_VALUE; }
        }
        long parsed = ChatParser.timestamp(value);
        if (parsed != 0) return parsed;
        if (value.startsWith("1970-01-01") || value.startsWith("Jan 01, 1970") || value.startsWith("01/01/1970")) {
            // ChatParser's zero can be valid only for a successfully parsed epoch origin.
            try { return Instant.parse(value.replace(" UTC", "Z").replace(' ', 'T')).toEpochMilli(); }
            catch (java.time.DateTimeException ignored) {
                if (value.equals("1970-01-01") || value.equals("1970-01-01 00:00:00 UTC") || value.equals("1970-01-01 00:00:00") || value.equals("Jan 01, 1970 00:00:00") || value.equals("01/01/1970 00:00:00")) return 0;
            }
        }
        return Long.MIN_VALUE;
    }
    private static String firstScalar(JSONObject object, String[] candidates) {
        // Exact field names take precedence, followed by case-insensitive forms.
        for (String candidate : candidates) {
            String value = scalar(object.opt(candidate));
            if (!value.isEmpty()) return value;
        }
        List<String> keys = sortedKeys(object);
        for (String candidate : candidates) for (String key : keys) if (candidate.equalsIgnoreCase(key)) {
            String value = scalar(object.opt(key)); if (!value.isEmpty()) return value;
        }
        return "";
    }
    private static String scalar(Object value) {
        if (value instanceof String || value instanceof Number) return value.toString().trim();
        return "";
    }
    private static List<String> sortedKeys(JSONObject object) {
        List<String> keys = new ArrayList<>(); Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) keys.add(iterator.next());
        Collections.sort(keys); return keys;
    }
    private static void increment(Map<String,Long> counts, String key) { counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1L); }
    private static String bounded(String value) { return value.length() <= MAX_LABEL ? value : value.substring(0,MAX_LABEL); }
    private static void copyRanked(Map<String,Long> values, Map<String,Long> target) {
        List<Map.Entry<String,Long>> rows = new ArrayList<>(values.entrySet());
        Collections.sort(rows, (a,b) -> { int count = Long.compare(b.getValue(),a.getValue()); return count == 0 ? a.getKey().compareTo(b.getKey()) : count; });
        for (Map.Entry<String,Long> entry : rows) target.put(entry.getKey(),entry.getValue());
    }
    private static String label(String filename) {
        String name = filename == null ? "Metadata" : filename.replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceFirst("(?i)\\.json$", "").replace('_',' ').replace('-',' ').trim();
        if (name.isEmpty()) return "Metadata";
        return name.substring(0,1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
    /** Check before org.json recursively allocates nested objects. JSON strings are opaque. */
    private static void checkDepth(String json) throws IOException {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > MAX_DEPTH) throw new IOException("Metadata nesting exceeds the 32-level analysis limit."); }
            else if (c == '}' || c == ']') depth--;
        }
    }
    private MetadataAnalytics() { }
}
