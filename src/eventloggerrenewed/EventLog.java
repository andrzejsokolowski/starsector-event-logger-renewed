package eventloggerrenewed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;

/**
 * The log of the current campaign, oldest first.
 *
 * Kept in the sector's persistent data as a plain list of strings, so it is saved with the game and the
 * save still loads after this mod is removed.
 */
final class EventLog {

    static final String KEY = "event_logger_renewed_log";
    /** How many messages a campaign keeps. The oldest go first. */
    static final int MAX_ENTRIES = 2000;

    private static List<Object> stored = new ArrayList<>();
    private static final List<LogEntry> entries = new ArrayList<>();
    /** Changes whenever the log does, so the screen knows when to rebuild. */
    private static int version;

    private EventLog() {}

    @SuppressWarnings("unchecked")
    static void load() {
        entries.clear();
        version++;
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object saved = data.get(KEY);
        if (!(saved instanceof ArrayList)) {
            saved = new ArrayList<String>();
            data.put(KEY, saved);
        }
        stored = (List<Object>) saved;
        List<Object> kept = new ArrayList<>();
        for (Object record : stored) {
            LogEntry entry = LogEntry.decode(record);
            if (entry == null) continue;
            entries.add(entry);
            kept.add(record);
        }
        if (kept.size() != stored.size()) {
            stored.clear();
            stored.addAll(kept);
        }
        trim();
    }

    static void add(LogEntry entry) {
        entries.add(entry);
        stored.add(entry.encode());
        trim();
        version++;
    }

    static void clear() {
        entries.clear();
        stored.clear();
        version++;
    }

    static List<LogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    static int size() {
        return entries.size();
    }

    static int version() {
        return version;
    }

    private static void trim() {
        int extra = entries.size() - MAX_ENTRIES;
        if (extra <= 0) return;
        entries.subList(0, extra).clear();
        stored.subList(0, Math.min(extra, stored.size())).clear();
    }
}
