package eventloggerrenewed;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CampaignEngine;
import com.fs.starfarer.campaign.comms.IntelTabData;

/**
 * Adds the "Events" button next to Intel, Planets and Factions on the Intel screen, and shows the
 * {@link EventsPanel} when it is selected.
 *
 * The Intel screen picks its page by a number it keeps in IntelTabData (0 Intel, 1 Planets,
 * 2 Factions). Events uses {@link #EVENTS_INDEX}, far above those, and its button goes right of the
 * last button in that row, after any another mod added (Item Markers' "Markers"). When that number is
 * selected, the Events page is shown and the game's own page under it is faded out. Clicking a game button switches back on its own: the game
 * fades its page in and sets its number, and the Events page follows that number. Nothing is removed
 * from the game's screen.
 */
final class EventsTab implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(EventsTab.class);
    private static final String BUTTON_ID = "event_logger_renewed_tab";
    private static final float BUTTON_WIDTH = 130f;
    /**
     * The page number Events uses. Far above any the game or another mod counts up to, so it never
     * clashes. If this mod is removed while Events is selected, the game falls back to its Intel page.
     */
    static final int EVENTS_INDEX = 50;
    /** The Intel screen's getters for its own pages. Method names survive the game's scrambling. */
    private static final String[] PAGE_GETTERS = {"getEventsPanel", "getPlanetsPanel", "getFactionPanel"};

    private float rowTop;
    private float rowHeight;
    private UIPanelAPI tab;
    private ButtonAPI button;
    private CustomPanelAPI content;
    private EventsPanel page;
    private int index = -1;
    private int failures;
    private boolean wasSelected;
    private int restoreCheck;
    private final List<UIComponentAPI> hiddenByUs = new ArrayList<>();

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
            update();
        } catch (Throwable t) {
            failures++;
            forget();
            LOG.error("Event Logger - Renewed: could not show the Events tab"
                    + (failures >= 3 ? ", turning it off for this session." : "."), t);
        }
    }

    private void update() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentCoreTab() != CoreUITabId.INTEL) {
            forget();
            return;
        }
        UIPanelAPI current = findIntelTab(ui);
        if (current == null) {
            forget();
            return;
        }
        if (current != tab) {
            forget();
            add(current);
        }
        if (tab == null) return;

        IntelTabData data = intelData();
        boolean selected = data != null && data.getSelectedTabIndex() == index;
        if (selected) {
            content.setOpacity(1f);
            hideGamePages();
            button.setChecked(true);
            button.highlight();
            wasSelected = true;
        } else {
            content.setOpacity(0f);
            if (button.isChecked()) {
                button.setChecked(false);
                button.unhighlight();
            }
            if (wasSelected) {
                wasSelected = false;
                restoreCheck = 3;
            }
            // The game shows its next page itself. If after a few frames it has not (the screen was
            // not rebuilt), give back the page Events faded out, so the screen is never left empty.
            if (restoreCheck > 0) {
                if (anyGamePageVisible()) {
                    restoreCheck = 0;
                    hiddenByUs.clear();
                } else if (--restoreCheck == 0) {
                    for (UIComponentAPI c : hiddenByUs) c.setOpacity(1f);
                    hiddenByUs.clear();
                }
            }
        }
        page.update(selected);
    }

    private boolean anyGamePageVisible() {
        for (UIComponentAPI page : gamePages()) {
            if (page.getOpacity() > 0f) return true;
        }
        return false;
    }

    /** The Intel screen's panel: the docked one when docked, as the game itself decides. */
    private static UIPanelAPI findIntelTab(CampaignUIAPI ui) {
        Object core = null;
        Object dialog = Reflect.call(ui, "getEncounterDialog");
        if (ui.getCurrentInteractionDialog() != null && dialog != null) core = Reflect.call(dialog, "getCoreUI");
        if (core == null) core = Reflect.call(ui, "getCore");
        Object tab = Reflect.call(core, "getCurrentTab");
        if (tab instanceof UIPanelAPI && Reflect.has(tab, "getEventsPanel")) return (UIPanelAPI) tab;
        return null;
    }

    private static IntelTabData intelData() {
        CampaignEngine engine = CampaignEngine.getInstance();
        if (engine == null || engine.getUIData() == null) return null;
        return engine.getUIData().getIntelData();
    }

    private void add(UIPanelAPI target) {
        // The row of tab buttons: found from the game's own buttons, then every child sitting in that
        // row counts, so a button another mod added there (in its own wrapper) is seen too.
        List<UIComponentAPI> children = target.getChildrenCopy2();
        UIComponentAPI first = null;
        for (UIComponentAPI child : children) {
            if (unwrap(child) instanceof ButtonAPI) {
                first = child;
                break;
            }
        }
        if (first == null) return;
        rowTop = top(first);
        rowHeight = first.getPosition().getHeight();

        UIComponentAPI last = null;
        int buttons = 0;
        for (UIComponentAPI child : children) {
            if (!inButtonRow(child)) continue;
            buttons++;
            if (last == null || right(child) > right(last)) last = child;
        }
        if (last == null) return;

        PositionAPI tabPos = target.getPosition();
        PositionAPI lastPos = last.getPosition();
        float height = rowHeight;
        float x = right(last) - tabPos.getX() + 1f;
        float y = (tabPos.getY() + tabPos.getHeight()) - (lastPos.getY() + lastPos.getHeight());

        index = EVENTS_INDEX;
        String label = "Events";
        int key = -1;
        if (buttons < 9) {
            key = Keyboard.KEY_1 + buttons;
            label = "Events [" + (buttons + 1) + "]";
        }

        CustomPanelAPI holder = Global.getSettings().createCustom(BUTTON_WIDTH, height, new ButtonListener());
        TooltipMakerAPI t = holder.createUIElement(BUTTON_WIDTH, height, false);
        ButtonAPI b = t.addButton(label, BUTTON_ID, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TOP, BUTTON_WIDTH, height, 0f);
        if (key >= 0) b.setShortcut(key, false);
        holder.addUIElement(t).inTL(0f, 0f);
        target.addComponent(holder).inTL(x, y);

        float top = y + height + 2f;
        float w = tabPos.getWidth();
        float h = tabPos.getHeight() - top;
        EventsPanel newPage = new EventsPanel(w, h);
        CustomPanelAPI panel = Global.getSettings().createCustom(w, h, newPage);
        newPage.attach(panel);
        target.addComponent(panel).inTL(0f, top);
        panel.setOpacity(0f);

        tab = target;
        button = b;
        content = panel;
        page = newPage;
    }

    /**
     * Fades out the game's own page while Events is shown, and unchecks the game's tab buttons. Only
     * the three pages the game switches between are touched; it fades them back in on its own.
     */
    private void hideGamePages() {
        for (UIComponentAPI child : tab.getChildrenCopy2()) {
            Object inner = unwrap(child);
            if (inner instanceof ButtonAPI && inner != button && ((ButtonAPI) inner).isChecked()) {
                ((ButtonAPI) inner).setChecked(false);
            }
        }
        for (UIComponentAPI page : gamePages()) {
            if (page.getOpacity() <= 0f) continue;
            page.setOpacity(0f);
            if (!hiddenByUs.contains(page)) hiddenByUs.add(page);
        }
    }

    /** The Intel, Planets and Factions pages. */
    private List<UIComponentAPI> gamePages() {
        List<UIComponentAPI> pages = new ArrayList<>(3);
        for (String getter : PAGE_GETTERS) {
            Object page = Reflect.call(tab, getter);
            if (page instanceof UIComponentAPI) pages.add((UIComponentAPI) page);
        }
        return pages;
    }

    /** True for anything in the row of tab buttons: the game's, ours, or another mod's. */
    private boolean inButtonRow(UIComponentAPI c) {
        if (c == content) return false;
        if (c instanceof CustomPanelAPI && ((CustomPanelAPI) c).getPlugin() instanceof ButtonListener) return true;
        PositionAPI p = c.getPosition();
        return Math.abs(top(c) - rowTop) <= 3f && p.getHeight() <= rowHeight + 4f && p.getWidth() <= 400f;
    }

    private static float top(UIComponentAPI c) {
        PositionAPI p = c.getPosition();
        return p.getY() + p.getHeight();
    }

    private static float right(UIComponentAPI c) {
        PositionAPI p = c.getPosition();
        return p.getX() + p.getWidth();
    }

    /** Components another mod added through the game's wrapper are seen through it. */
    private static Object unwrap(UIComponentAPI c) {
        Object inner = Reflect.has(c, "getWrapped") ? Reflect.call(c, "getWrapped") : null;
        return inner != null ? inner : c;
    }

    private void forget() {
        tab = null;
        button = null;
        content = null;
        page = null;
        index = -1;
        wasSelected = false;
        restoreCheck = 0;
        hiddenByUs.clear();
    }

    private void select() {
        IntelTabData data = intelData();
        if (data == null || tab == null) return;
        data.setSelectedTabIndex(index);
    }

    /** Takes the click on the Events button. */
    private final class ButtonListener implements CustomUIPanelPlugin {
        @Override
        public void positionChanged(PositionAPI position) {}

        @Override
        public void renderBelow(float alphaMult) {}

        @Override
        public void render(float alphaMult) {}

        @Override
        public void advance(float amount) {}

        @Override
        public void processInput(List<InputEventAPI> events) {}

        @Override
        public void buttonPressed(Object buttonId) {
            if (BUTTON_ID.equals(buttonId)) select();
        }
    }
}
