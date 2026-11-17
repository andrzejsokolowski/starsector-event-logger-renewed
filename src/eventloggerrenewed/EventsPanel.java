package eventloggerrenewed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.comms.IntelTabData;

/**
 * The Events page: a count, a Clear button, a search box, and the log newest first.
 *
 * Each row shows the message's icon, its date and title, and the lines the fleet log card showed
 * under the title. A row whose intel is still on the Intel screen gets a Show button that opens it
 * there.
 */
final class EventsPanel implements CustomUIPanelPlugin {

    /** Rows built at most. More would make the page slow to open; the search reaches older ones. */
    private static final int MAX_ROWS = 300;
    private static final float HEADER_HEIGHT = 30f;
    private static final float ICON_SIZE = 32f;
    private static final String CLEAR_ID = "event_logger_renewed_clear";
    /** Seconds the Clear button waits for its second click. */
    private static final float CLEAR_CONFIRM_SECONDS = 3f;
    private static final String FALLBACK_ICON = "graphics/icons/intel/fleet_log3.png";

    private final float width;
    private final float height;
    private CustomPanelAPI panel;
    private LabelAPI countLabel;
    private ButtonAPI clearButton;
    private TextFieldAPI search;
    private TooltipMakerAPI list;

    private int builtVersion = -1;
    private String builtQuery = null;
    private float clearArmed;
    /** Icons checked once per path: loaded and usable, or not. */
    private static final Map<String, Boolean> ICONS = new HashMap<>();

    EventsPanel(float width, float height) {
        this.width = width;
        this.height = height;
    }

    void attach(CustomPanelAPI panel) {
        this.panel = panel;

        TooltipMakerAPI count = panel.createUIElement(220f, HEADER_HEIGHT, false);
        countLabel = count.addPara("", Misc.getTextColor(), 0f);
        panel.addUIElement(count).inTL(10f, 7f);

        TooltipMakerAPI clear = panel.createUIElement(90f, HEADER_HEIGHT, false);
        clearButton = clear.addButton("Clear", CLEAR_ID, 80f, 22f, 0f);
        panel.addUIElement(clear).inTL(235f, 4f);

        TooltipMakerAPI searchLabel = panel.createUIElement(60f, HEADER_HEIGHT, false);
        searchLabel.addPara("Search", Misc.getGrayColor(), 0f);
        panel.addUIElement(searchLabel).inTL(335f, 7f);

        TooltipMakerAPI field = panel.createUIElement(320f, HEADER_HEIGHT, false);
        search = field.addTextField(300f, 22f, Fonts.DEFAULT_SMALL, 0f);
        search.setMaxChars(60);
        search.setUndoOnEscape(false);
        panel.addUIElement(field).inTL(395f, 4f);
    }

    /** Called every frame by {@link EventsTab}. Rebuilds the list only when something changed. */
    void update(boolean shown) {
        if (!shown || panel == null) return;
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (builtVersion != EventLog.version() || !query.equals(builtQuery)) {
            rebuild(query);
        }
    }

    private void rebuild(String query) {
        float scroll = 0f;
        if (list != null) {
            ScrollPanelAPI scroller = list.getExternalScroller();
            if (scroller != null && query.equals(builtQuery)) scroll = scroller.getYOffset();
            panel.removeComponent(list);
        }
        builtVersion = EventLog.version();
        builtQuery = query;

        List<LogEntry> all = EventLog.entries();
        List<LogEntry> shown = new ArrayList<>();
        int matches = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            LogEntry e = all.get(i);
            if (!query.isEmpty() && !e.searchText.contains(query)) continue;
            matches++;
            if (shown.size() < MAX_ROWS) shown.add(e);
        }

        if (query.isEmpty()) {
            countLabel.setText(all.size() == 1 ? "1 event" : all.size() + " events");
        } else {
            countLabel.setText(matches + " of " + all.size() + " events");
        }

        float listWidth = width - 20f;
        float listHeight = height - HEADER_HEIGHT - 10f;
        list = panel.createUIElement(listWidth, listHeight, true);
        if (shown.isEmpty()) {
            list.addPara(all.isEmpty() ? "Nothing yet. Messages from the fleet log show up here."
                    : "No event matches the search.", Misc.getGrayColor(), 10f);
        }
        for (LogEntry e : shown) addRow(list, e, listWidth);
        if (matches > shown.size()) {
            list.addPara("Showing the newest " + MAX_ROWS + ". Search to find older ones.",
                    Misc.getGrayColor(), 10f);
        }
        panel.addUIElement(list).inTL(10f, HEADER_HEIGHT + 6f);

        ScrollPanelAPI scroller = list.getExternalScroller();
        if (scroller != null && scroll > 0f) scroller.setYOffset(scroll);
    }

    private void addRow(TooltipMakerAPI list, LogEntry e, float listWidth) {
        float rowTop = list.getHeightSoFar();
        boolean openable = canOpen(e.intel);

        TooltipMakerAPI text = list.beginImageWithText(icon(e.icon), ICON_SIZE);
        String date = "[" + e.date + "]";
        LabelAPI title = text.addPara(date + "  " + e.title, e.titleColor(), 0f);
        title.setHighlight(date);
        title.setHighlightColor(Misc.getGrayColor());
        for (String line : e.lines) text.addPara(line, Misc.getGrayColor(), 2f);
        list.addImageWithText(8f);

        if (openable) {
            float after = list.getHeightSoFar();
            ButtonAPI show = list.addButton("Show", e, 70f, 20f, 0f);
            show.getPosition().inTL(listWidth - 80f, rowTop + 8f);
            list.setHeightSoFar(after);
        }
    }

    /** The intel is still listed on the Intel screen, so Show has somewhere to go. */
    private static boolean canOpen(IntelInfoPlugin intel) {
        if (intel == null) return false;
        try {
            return Global.getSector().getIntelManager().hasIntel(intel) && !intel.isHidden();
        } catch (Throwable t) {
            return false;
        }
    }

    /** The icon if the game has it loaded, otherwise the fleet log icon. */
    private static String icon(String path) {
        if (path == null || path.isEmpty()) return FALLBACK_ICON;
        Boolean ok = ICONS.get(path);
        if (ok == null) {
            try {
                SpriteAPI sprite = Global.getSettings().getSprite(path);
                if (sprite == null || sprite.getTextureId() == 0) Global.getSettings().loadTexture(path);
                sprite = Global.getSettings().getSprite(path);
                ok = sprite != null && sprite.getTextureId() != 0;
            } catch (Exception ex) {
                ok = false;
            }
            ICONS.put(path, ok);
        }
        return ok ? path : FALLBACK_ICON;
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (CLEAR_ID.equals(buttonId)) {
            if (clearArmed > 0f) {
                clearArmed = 0f;
                clearButton.setText("Clear");
                EventLog.clear();
            } else {
                clearArmed = CLEAR_CONFIRM_SECONDS;
                clearButton.setText("Sure?");
            }
            return;
        }
        if (buttonId instanceof LogEntry) open(((LogEntry) buttonId).intel);
    }

    /** Opens the Intel page on this entry, the same way clicking a fleet log card does. */
    private static void open(IntelInfoPlugin intel) {
        if (!canOpen(intel)) return;
        IntelTabData data = CampaignEngine.getInstance().getUIData().getIntelData();
        data.setSelectedTabIndex(0);
        data.setSelectedIntel(intel);
        Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, intel);
    }

    @Override
    public void advance(float amount) {
        if (clearArmed > 0f) {
            clearArmed -= amount;
            if (clearArmed <= 0f) {
                clearArmed = 0f;
                clearButton.setText("Clear");
            }
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}
}
