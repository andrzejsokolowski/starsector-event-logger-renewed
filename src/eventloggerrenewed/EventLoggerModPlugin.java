package eventloggerrenewed;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * Event Logger - Renewed: every fleet log message, kept on an Events tab of the Intel screen.
 *
 * Both scripts are transient, so nothing of this mod's own classes goes into the save. The log itself
 * is a plain list of strings in the sector's persistent data (see {@link EventLog}).
 */
public class EventLoggerModPlugin extends BaseModPlugin {

    @Override
    public void onGameLoad(boolean newGame) {
        EventLog.load();
        SectorAPI sector = Global.getSector();
        sector.removeTransientScriptsOfClass(MessageWatcher.class);
        sector.removeTransientScriptsOfClass(EventsTab.class);
        sector.addTransientScript(new MessageWatcher());
        sector.addTransientScript(new EventsTab());
    }
}
