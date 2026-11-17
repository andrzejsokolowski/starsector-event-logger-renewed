# Event Logger - Renewed

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history and test lists: the **Event Logger - Renewed board** in Andrzej's Notion, under
**Starsector mods** (`collection://41b20ca7-5d20-8117-a3af-000b4563e1ae`). Ticket ids start with ELR.
Read the board with `notion-query-data-sources` mode `view` on `view://41b20ca7-5d20-8182-bbcb-000cb20deea1`.
ELR-1 holds Andrzej's request. Update the board during the work.

## What it is

Every fleet log message (the cards at the bottom left of the campaign screen) is kept on an "Events" tab of
the Intel screen. A from-scratch remake of b2c's Event Logger
(https://git.dest-unreachable.net/d-u.net/starsector-eventlogger), which crashed, had wrong version
tracking and needed NativeUI. No code of the original is used. id `event_logger_renewed`, package
`eventloggerrenewed`, staging folder `EventLoggerRenewed`, no dependencies. Plain Java 17.

## Build

`./gradlew releaseZip` compiles against the game (`starsectorPath` in gradle.properties), runs the checks and
writes `EventLoggerRenewed.zip`. `checkNoForbiddenApis` fails the build on java.io, java.nio.file,
java.lang.reflect, or any named game class outside `api` except CampaignEngine, CampaignUIPersistentData and
IntelTabData. `checkLog` runs `test/src/.../LogCheck` (saved entries round trip).

## How it works

- `MessageWatcher`: every card goes through `CampaignState.getMessageList().getMessages()`, plain text lines too
  (`addMessage(String)` wraps them in a MessageIntel). Each frame the cards are compared with last frame's by
  identity; a new card is copied once from `card.getInfo()` (the card's own tooltip), label by label: first
  label = title with its colour, the rest = lines. If the text is not built yet it waits up to 30 frames, then
  falls back to the intel's title.
- `EventLog`: an `ArrayList<String>` in sector persistent data (`event_logger_renewed_log`), one string per
  entry, fields split by U+001E and lines by U+001F. Only Java types in the save, so removing the mod is safe.
  Newest 2000 kept.
- `EventsTab`: when the INTEL core tab is open (docked: the encounter dialog's core, as
  `CampaignState.getCurrentCoreTab` decides), finds the intel tab panel (`getCurrentTab`, has `getEventsPanel`),
  adds a button holder right of the last child in the tab button row and the `EventsPanel` page below it.
  The game picks its page from `IntelTabData.getSelectedTabIndex()` (0 Intel, 1 Planets, 2 Factions, anything
  else falls to Intel). Events uses 50. While selected, the game's three pages (`getEventsPanel`,
  `getPlanetsPanel`, `getFactionPanel`) are faded to 0, which also stops their input. A game button press
  fades its page in and sets its index itself. If no game page is visible 3 frames after leaving Events, the
  faded ones are given back.
- `Reflect`: method handles by name only. The game's UI class names change with every hotfix.
- Show button: `IntelTabData.setSelectedTabIndex(0)`, `setSelectedIntel`, then
  `showCoreUITab(INTEL, intel)`, the same path a fleet log card click takes.
