# Porthole Quest Agent Guide

## Project Baseline

- Target Minecraft `1.21.1`, NeoForge `21.1.256`, Java `21`.
- Keep one NeoForge source set. Do not add Gradle subprojects or a multi-loader abstraction.
- FTB Quests `2101.1.27` owns quest definitions, visibility, dependencies, progress, rewards, claims, tracking, synchronization, and server validation.
- ApricityUI `1.2.7` renders the client journal. The journal UI is a presentation and navigation layer over FTBQ; the journal item itself is registered as shared mod content.
- The formal design is `docs/design/porthole-quest-design.md`; art-specific requirements are in `docs/design/porthole-quest-art-requirements.md`. Treat those documents as product contracts. The formal design is still `in-design`; its Gate has not passed.
- The quest journal item `portholequest:quest_journal` is in v1. It opens the same journal as J, has no default recipe, and is available in Tools & Utilities or through commands for modpack authors to use as an FTBQ reward.
- Remove remaining NeoForge MDK example blocks, tabs, sample logs, and sample configuration. Add only product behavior defined in the design.

## Planned Package Structure

Keep the current base package `ink.myumoon.portholequest`. Retain the mod entrypoint and shared configuration there, then add only packages needed by an accepted vertical slice:

```text
ink.myumoon.portholequest
├── PortholeQuest.java
├── PortholeQuestClient.java
├── Config.java                 # add when implementing confirmed config options
├── registry
│   ├── PQRegistries
│   └── PQItems
├── item            # shared journal item; emits client open intent without Screen imports
├── event           # shared open-intent event consumed by the physical-client entrypoint
├── ftbq           # FTBQ adapter boundary; client adapters load only from client entrypoints
│   ├── FtbqBridge
│   ├── FtbqActionDispatcher
│   └── FtbqStateWatcher
├── model          # immutable, UI-independent QuestLogSnapshot records
└── client
    ├── input       # independent J, X, and Space key mappings
    ├── session     # GuiSessionCache for the current world session
    ├── screen      # PortholeQuestScreen, ScreenViewState, ScreenStateHistory
    ├── sync        # QuestLogRefreshCoordinator
    └── ui          # ApricityUI DOM rendering and refreshed-document bindings
```

Create a package when its first real class is introduced; do not add empty packages or split a small cohesive responsibility into many one-class packages. Class names may change when implementation evidence warrants it, but responsibility boundaries must remain explicit.

Resources follow the design's ApricityUI layout:

```text
src/main/resources/assets/apricityui/apricity/
├── screens/
├── css/
└── textures/
src/main/resources/assets/portholequest/textures/gui/
```

Use logical ApricityUI paths such as `screens/quest_log.html` in Java. Keep authored resources separate from generated resources under `src/generated/resources`.

## Dependency Direction and Ownership

- `client.screen` and `client.ui` may depend on `model`; they must not depend on FTBQ types.
- `ftbq` may depend on `model` to produce snapshots and accept semantic actions; it must not depend on screen or DOM classes.
- `client.session` stores UI-independent identifiers and primitive state. Keep it independent from FTBQ and ApricityUI.
- `client.sync` coordinates snapshot refresh and state restoration through narrow interfaces. Do not introduce a package cycle to connect the screen and FTBQ adapter.
- Entrypoints and input handlers forward lifecycle or user intent to the owning client component; they do not implement quest or presentation logic.

- `ftbq` is the only package that reads FTBQ client quest objects, `TeamData`, or sends FTBQ action messages. This top-level location was requested by the user; it does not make adapters that import `ClientQuestFile` server-loadable. Load those adapters only from physical-client entrypoints. FTBQ itself owns server-side action handlers.
- `FtbqBridge` reads authoritative FTBQ state and produces immutable snapshots. It does not mutate FTBQ data.
- `model` contains no FTBQ classes and no ApricityUI DOM objects.
- `FtbqActionDispatcher` accepts semantic actions and sends FTBQ's native submit, claim, or tracking messages. Do not implement optimistic completion or duplicate server rules.
- `PortholeQuestScreen` owns the active view and delegates rendering, navigation, refresh, and actions. Use one Minecraft `Screen`; details and popups are internal view states.
- `client.ui` renders snapshots and view state. DOM nodes and listeners are reacquired after document refresh; they are not long-lived state.
- `GuiSessionCache` stores only current-world client GUI state. Clear it on world exit, disconnect, and save switch. Do not persist it to world data, config, or a custom network packet.
- Keep client-only Minecraft and ApricityUI references out of common entrypoints and classes loadable on a dedicated server.
- Avoid cyclic package dependencies. If coordination needs both sides, place the coordinator in `client.sync` and expose narrow interfaces or semantic commands.

## Implementation Order

Follow the design's R roadmap stages for scope and completion criteria. B blocks describe implementation responsibilities and dependencies within those stages, rather than a second milestone sequence. Each stage must compile, launch, and have its stated evidence before expanding scope. The block order below guides implementation without expanding the current R stage to all work in a block.

1. **R0.1 ApricityUI probe:** build a development-only page and verify the actual `ApricityScreen` host, resource paths, texture rendering, layout, scroll, input interception, Java DOM updates, resize/refresh behavior, and listener rebinding in-game. Record limitations. Do not add a Gradle module or ship the probe as player functionality.
2. **B1 entry and client lifecycle:** shared journal item registration and right-click entry, client key mappings, shared open preconditions, pause behavior, and world/disconnect cleanup.
3. **B2 FTBQ bridge:** synchronized-data readiness, visibility and filters, stable IDs, QuestLink, and immutable snapshots.
4. **B3 screen/session state:** the single Screen, view state, detail history, and bounded session cache.
5. **B4 read-only journal:** tabs, chapter list, filters, expansion, tooltips, details, and placeholder textures.
6. **B5 detail and popup UI:** description pages, flowchart, scrolling, and internal navigation.
7. **B6 FTBQ actions and refresh:** task submit, reward claim, tracking, pending state, authoritative refresh, and replacement handling.
8. **B7 assets and runtime validation:** final textures, pixel scaling, window sizes, multiplayer and regression scenarios, and Gate evidence.

R0.1 was accepted on 2026-10-13. The next roadmap stage is R1: required B1 entry/lifecycle work, B2 readiness/immutable snapshots, and a minimal static list/Blank Detail Screen. Defer full filtering, navigation, and actions to their R stages. Retain `client.probe` and its resources for future development tests; keep command registration behind `!FMLEnvironment.production`. Consult `docs/implementation/r0.1-apricityui-probe.md` when changing AUI hosting, input, refresh, scaling, or fonts. Use placeholders before final art and verify each slice with the pinned dependencies.

## Non-Negotiable Behavior

- Do not change FTBQ task semantics or infer state from UI appearance.
- Apply FTBQ visibility before Porthole filters. The active filter includes executable unfinished quests and completed quests with FTBQ-confirmed unclaimed rewards; these can also appear in the completed filter.
- Build no journal Screen before the client quest file, world/player, and authoritative team data are ready.
- The journal item and J key use the same Screen-opening path and readiness/locked-state handling. The item has no default recipe.
- The journal item is common registered content, so Porthole Quest and FTB Quests are required on both client and server. ApricityUI is a client-side dependency; keep its classes out of dedicated-server load paths. `neoforge.mods.toml` declares these dependency sides and minimum versions; the generated metadata and Java 21 build were verified in R0.
- Treat server synchronization as authoritative. A submitted action remains pending until FTBQ state changes; rejection must not leave a false completed, claimed, or tracked state.
- Keep detail history inside the current Screen. Session cache excludes popup instances, popup pages, and return history.
- Do not use FTBQ quest coordinates for Porthole flowchart layout.
- Do not assume browser behavior in ApricityUI. It is its own HTML/CSS/DOM runtime, not Chromium. Verify CSS, input, scaling, and rendering in-game.
- For ApricityUI refresh or Screen rebuild, discard old DOM references/listeners and bind against the new document. Keep persistent state in Java and guard stale asynchronous callbacks with the supported generation checks.
- Do not introduce custom networking, sidecar quest metadata, FTBQ hooks, or private API dependencies unless the design is revised with evidence first.

## Change and Verification Rules

- Before using a dependency API, verify it against the pinned FTBQ `2101.1.27` or ApricityUI `1.2.7` artifact/source. Do not use the unrelated newer `reference/FTB-Quests` working-tree HEAD as API evidence.
- If implementation evidence conflicts with the design, stop at the affected boundary, record the discrepancy and evidence, and revise the design before continuing. Do not silently reinterpret product behavior.
- Keep changes scoped to the active slice. Do not reorganize existing packages, change resource/data-generation strategy, or edit unrelated template configuration as cleanup.
- Run the relevant Gradle build/check for code changes. A successful build proves compilation and packaging only; GUI, synchronization, actions, and pixel rendering require in-game evidence from the design scenarios.
- Before marking a slice complete, record its acceptance scenario and evidence (logs, screenshots, or runtime observation as applicable).

## Current Entry Point

R0 and R0.1 are complete; the overall formal design remains `in-design`, not Gate-approved. R1 is in progress: the read-only bridge, detached list snapshot, shared J/item opening path, and static list/Blank Detail Screen compile and run. Positive entry/list behavior has user and log evidence; missing/loading/locked rejection remains pending. Consult `docs/implementation/r1-data-bridge.md` before extending R1 or declaring it complete. Preserve the accepted probe as a reusable development tool. CSS background textures, nine-slice seams, final centering/clipping, and final-art regression are follow-up resource checks recorded in the R0.1 report.
