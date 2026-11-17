package eventloggerrenewed;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

/**
 * One fleet log message as it was shown: the date, the icon, the title line and the lines below it.
 *
 * Saved as one plain string, so the save never holds a class of this mod and stays loadable after the
 * mod is removed. The intel the message came from is kept for this session only, to open it on click.
 */
final class LogEntry {

    private static final char FIELD = '\u001E';
    private static final char LINE = '\u001F';

    final String date;
    final String icon;
    final String title;
    final int titleRgb;
    final List<String> lines;
    /** Lower case text of the whole entry, for the search box. */
    final String searchText;
    /** The intel this message is about. This session only, never saved. May be null. */
    transient IntelInfoPlugin intel;

    LogEntry(String date, String icon, String title, int titleRgb, List<String> lines, IntelInfoPlugin intel) {
        this.date = clean(date);
        this.icon = clean(icon);
        this.title = clean(title);
        this.titleRgb = titleRgb;
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String c = clean(line);
            if (!c.isEmpty()) kept.add(c);
        }
        this.lines = Collections.unmodifiableList(kept);
        this.intel = intel;
        StringBuilder sb = new StringBuilder(this.date).append(' ').append(this.title);
        for (String line : this.lines) sb.append(' ').append(line);
        this.searchText = sb.toString().toLowerCase(Locale.ROOT);
    }

    Color titleColor() {
        return new Color(titleRgb, true);
    }

    String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(date).append(FIELD).append(icon).append(FIELD).append(title).append(FIELD)
                .append(Integer.toHexString(titleRgb)).append(FIELD);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(LINE);
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** The entry a saved string holds, or null if the string is not one of ours. */
    static LogEntry decode(Object saved) {
        if (!(saved instanceof String)) return null;
        String[] parts = ((String) saved).split(String.valueOf(FIELD), -1);
        if (parts.length != 5) return null;
        int rgb;
        try {
            rgb = (int) Long.parseLong(parts[3], 16);
        } catch (NumberFormatException e) {
            rgb = 0xFFFFFFFF;
        }
        List<String> lines = new ArrayList<>();
        if (!parts[4].isEmpty()) Collections.addAll(lines, parts[4].split(String.valueOf(LINE), -1));
        return new LogEntry(parts[0], parts[1], parts[2], rgb, lines, null);
    }

    /** Drops the two separator characters, so a strange message can never break a saved entry. */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace(FIELD, ' ').replace(LINE, ' ').trim();
    }
}
