package eventloggerrenewed;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Headless check: a log entry comes back from its saved string unchanged, and junk is refused. */
public final class LogCheck {

    public static void main(String[] args) {
        List<String> lines = Arrays.asList("    - Faction: Persean League", "- 71,000 reward, 81 days remaining", "  ");
        LogEntry e = new LogEntry("Jul 15, c206", "graphics/icons/intel/bounties.png",
                "Personal Bounty - Saturn Oyungax", 0xFFAABBCC, lines, null);
        LogEntry back = LogEntry.decode(e.encode());
        check(back != null, "decodes");
        check(back.date.equals("Jul 15, c206"), "date");
        check(back.icon.equals("graphics/icons/intel/bounties.png"), "icon");
        check(back.title.equals("Personal Bounty - Saturn Oyungax"), "title");
        check(back.titleRgb == 0xFFAABBCC, "colour " + Integer.toHexString(back.titleRgb));
        check(back.lines.equals(Arrays.asList("- Faction: Persean League", "- 71,000 reward, 81 days remaining")),
                "lines " + back.lines);
        check(back.searchText.contains("persean league"), "search text");

        LogEntry bare = LogEntry.decode(new LogEntry("Jan 1, c206", "", "Monthly income: 13,435", -1,
                Collections.emptyList(), null).encode());
        check(bare != null && bare.lines.isEmpty() && bare.icon.isEmpty(), "entry without lines or icon");

        String odd = "Odd \u001E title \u001F here";
        LogEntry cleaned = LogEntry.decode(new LogEntry("d", "i", odd, 0, Arrays.asList(odd), null).encode());
        check(cleaned != null && cleaned.lines.size() == 1, "separator characters in text are dropped");

        check(LogEntry.decode("not an entry") == null, "junk string refused");
        check(LogEntry.decode(42) == null, "non-string refused");
        System.out.println("LogCheck: all checks passed");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("LogCheck failed: " + what);
    }
}
