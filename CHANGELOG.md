# Changelog

## 0.6.0 – Custom GUI, Route Diagnostics, and Block Sections — 2026-09-28

- Replaced the previous inventory and anvil-based menus with a unified custom client GUI for trains, components, timetable displays, lines, and timetables. The interface now supports buttons, pagination, icons, and text input, with the world rendered and blurred only once behind the panel.
- Menu data is provided through limited server-side snapshots with translatable text. Actions are validated using short-lived session IDs together with tool, distance, and object-state checks; sessions are invalidated when the menu is closed or the player disconnects.
- Minecarts can now be coupled entirely through the RailNet Tool GUI by selecting the first minecart, opening the second, and confirming the coupling. Chains remain available as an alternative.
- Train diagnostics now display the destination, current status, route progress, obstacles, pending departures, foreign block-section reservations, and the number of detected sections. A dedicated action can request a new route.
- Route finding now compresses continuous rail segments into directed edges for each route request and searches between endpoints, switches, crossings, and controllers. The previous limited rail search remains available as a fallback, and both systems use the current rail state from loaded chunks.
- Pairs of block controllers now define reservable track sections. Before entering, the server checks the complete section for loaded chunks, other minecarts, and existing reservations. Reservations apply in both directions and remain active until the final minecart has passed or the section has actually been cleared.
- Block controllers and signal controls now report **clear**, **reserved**, or **occupied** states in the GUI. Comparator level 8 also reflects section reservations.
- Waiting trains can periodically attempt an alternative route when track is missing. If no safe route is available, the train remains stopped, while blocked or locked routes avoid unnecessary repeated searches.
- Route finding was checked against deterministic rail layouts covering long straights, branches, loops, search limits, and reverse-direction routing. Java syntax and JSON resources were also statically validated.
- Textures added for all blocks and the rail tool

## 0.4.0 – Separate Components and RailNet Tool — 2026-09-27

- The six controller modes have been split into six individually registered blocks, each with its own recipes, items, block states, loot tables, names, and existing individual textures. The crossing rail remains a separate block.
- The craftable **RailNet Tool** is now required to open component and minecart GUIs. Right-clicking a minecart with the tool prevents the default vanilla interaction from mounting the cart. Both main-hand and off-hand interactions are supported, while chains remain available for coupling minecarts.
- A component's function is now determined by its block type. The controller mode switching buttons have been removed from the GUI, and each GUI now only displays controls relevant to that specific component.
- Existing `rail_controller` blocks and world data remain loadable. A tool button can transfer the previous settings and station ID when replacing a legacy controller with the corresponding new block. The legacy block no longer has a recipe and is no longer available in the Creative inventory.
- `/railnet` commands are now restricted to server operators and remain optional.

## 0.3.2 – Fix — 2026-09-27

- Fixed a crash when rendering or querying crossing rails. `CrossingBlock` now includes the `waterlogged` block property used by vanilla rails, with a default value of `false`.
- Updated the block state definitions to include both waterlogged states for every rail shape. Existing worlds can now load the new property using its default value.

## 0.3.1 – Fix — 2026-09-27

- Fixed a crash during Fabric initialization. The mixin is now located under `dev.cptgummiball.railnet.mixin`, preventing the mixin configuration from targeting the main class `dev.cptgummiball.railnet.RailNet`.

## 0.3.0 – Fixes and Features — 2026-09-27

### Fixed / Changed

- Chain interactions are now intercepted on the client side as well, ensuring that the second click on a minecart reaches the server. Train records are only created after successful server-side validation.
- Opening a menu or placing a minecart no longer creates a train automatically. A single-minecart train must now be created explicitly through the GUI.
- For managed minecarts, vanilla entity collisions, mutual pushing, and normal rail movement while the train is in motion are suppressed through mixins.
- Controllers are now detected beside a rail, directly below it, or two blocks below it. Hidden controllers can be accessed through the rail above them.

### Added

- Six controller modes, each with its own block texture: **Station**, **Switch**, **Block**, **Depot**, **Signal**, and **Timetable Display**.
- Server-validated vanilla-style GUIs for trains, controllers, destination stations, minecarts, lines, and timetables. Renaming uses an anvil-style text input field. German and English translations are included.
- Persistent lines with ordered stop sequences, stop durations, service intervals, and fixed departure times, including terminus, loop, and shuttle operation. When starting in the opposite direction, minecart order is automatically reversed when necessary.
- A timetable display controller mode with destination binding, train calling independent of the assigned line, and an updatable display showing trains currently dispatched to the selected destination.
- Redstone inputs can lock a switch when a signal is active or stop a train at a controller. Comparator output states are also refreshed after a train leaves.
- Orphaned trains are cleaned up after extended absence when their chunks are loaded.