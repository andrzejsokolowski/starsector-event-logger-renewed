package eventloggerrenewed;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

/**
 * Copies every message of the fleet log (the cards at the bottom left of the campaign screen) into
 * the {@link EventLog}.
 *
 * Every card goes through one list owned by the campaign screen, plain text lines included (the game
 * wraps those in a MessageIntel). Each frame the cards in that list are compared with the ones seen
 * the frame before, and each new card is copied once: its icon, and the text of its own tooltip, read
 * label by label, so the log says exactly what the card said.
 */
final class MessageWatcher implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(MessageWatcher.class);
    /** Frames to wait for a new card to get its text before falling back to the intel's title. */
    private static final int MAX_WAIT_FRAMES = 30;

    /** Cards already copied that are still in the list. */
    private final Set<Object> handled = Collections.newSetFromMap(new IdentityHashMap<>());
    /** New cards whose text is not built yet, with the frames waited so far. */
    private final Map<Object, Waiting> waiting = new IdentityHashMap<>();
    private int failures;

    private static final class Waiting {
        final IntelInfoPlugin intel;
        final String date;
        int frames;

        Waiting(IntelInfoPlugin intel, String date) {
            this.intel = intel;
            this.date = date;
        }
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (failures >= 3) return;
        try {
            watch();
        } catch (Throwable t) {
            failures++;
            LOG.error("Event Logger - Renewed: could not read the fleet log"
                    + (failures >= 3 ? ", stopping for this session." : "."), t);
        }
    }

    private void watch() {
        Object list = Reflect.call(Global.getSector().getCampaignUI(), "getMessageList");
        Object cards = Reflect.call(list, "getMessages");
        if (!(cards instanceof List)) return;

        Set<Object> present = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> now = new ArrayList<>((List<?>) cards);
        present.addAll(now);

        // A card that left before its text was built still goes into the log, with the intel's title.
        for (Iterator<Map.Entry<Object, Waiting>> it = waiting.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Object, Waiting> e = it.next();
            if (present.contains(e.getKey())) continue;
            record(null, e.getValue());
            it.remove();
        }
        handled.retainAll(present);

        for (Object card : now) {
            if (card == null || handled.contains(card)) continue;
            Waiting w = waiting.get(card);
            if (w == null) {
                Object intel = Reflect.call(card, "getIntel");
                if (!(intel instanceof IntelInfoPlugin)) {
                    handled.add(card);
                    continue;
                }
                w = new Waiting((IntelInfoPlugin) intel, today());
                waiting.put(card, w);
            }
            if (record(card, w) || ++w.frames >= MAX_WAIT_FRAMES) {
                if (w.frames >= MAX_WAIT_FRAMES) record(null, w);
                waiting.remove(card);
                handled.add(card);
            }
        }
    }

    /**
     * Adds the card to the log. With a card, only when its text is there (returns false otherwise);
     * without one, from the intel's own title.
     */
    private boolean record(Object card, Waiting w) {
        List<LabelAPI> labels = new ArrayList<>();
        if (card != null) {
            Object info = Reflect.call(card, "getInfo");
            if (info instanceof UIPanelAPI) collectLabels((UIPanelAPI) info, labels, 0);
        }

        String title = null;
        int titleRgb = Color.WHITE.getRGB();
        List<String> lines = new ArrayList<>();
        for (LabelAPI label : labels) {
            String text = label.getText();
            if (text == null || text.trim().isEmpty()) continue;
            if (title == null) {
                title = text;
                Color c = label.getColor();
                if (c != null) titleRgb = c.getRGB();
            } else {
                lines.add(text);
            }
        }
        if (title == null) {
            if (card != null) return false;
            title = fallbackTitle(w.intel);
        }
        EventLog.add(new LogEntry(w.date, iconOf(w.intel), title, titleRgb, lines, w.intel));
        return true;
    }

    private static void collectLabels(UIPanelAPI panel, List<LabelAPI> out, int depth) {
        if (depth > 8) return;
        List<UIComponentAPI> children = panel.getChildrenCopy2();
        if (children == null) return;
        for (UIComponentAPI child : children) {
            if (child instanceof LabelAPI) {
                out.add((LabelAPI) child);
            } else if (child instanceof UIPanelAPI) {
                collectLabels((UIPanelAPI) child, out, depth + 1);
            }
        }
    }

    private static String fallbackTitle(IntelInfoPlugin intel) {
        try {
            String t = intel.getSmallDescriptionTitle();
            if (t != null && !t.trim().isEmpty()) return t;
        } catch (Throwable ignored) {
            // Some intel builds its title from state that is gone by now.
        }
        return "Message";
    }

    private static String iconOf(IntelInfoPlugin intel) {
        try {
            String icon = intel.getIcon();
            return icon == null ? "" : icon;
        } catch (Throwable t) {
            return "";
        }
    }

    static String today() {
        CampaignClockAPI clock = Global.getSector().getClock();
        return clock.getShortMonthString() + " " + clock.getDay() + ", c" + clock.getCycle();
    }
}
