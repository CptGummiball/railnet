# Changelog

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
