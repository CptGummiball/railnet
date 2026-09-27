# RailNet (Fabric, Minecraft 1.21.1)

Prototype implementation from the eleven design documents in `docs/`. Author: **cptgummiball**; Java namespace: `dev.cptgummiball`.

## Install / build

Install Fabric Loader and Fabric API for Minecraft 1.21.1 on both client and dedicated server. Build with Java 21: `./gradlew build` (Windows: `gradlew.bat build`). Put `build/libs/railnet-0.2.0.jar` into each instance's `mods` directory. The included Gradle wrapper downloads Gradle 8.10.2 on first use.

## Play

1. Craft two Rail Controllers using iron, copper and redstone; place them immediately beside ordinary rails. Right-click each controller to view its chat menu. Name each via the shown `/railnet controller x y z name ...` command.
2. Right-click cart A with a Vanilla Chain, then cart B within six blocks. Sneak-right-click a coupled cart with a Chain to separate that cart while stopped.
3. Sneak-right-click the leading cart with an empty hand. Click a loaded station, then **Start**. To reverse the logical cart order, use **Reverse** while stopped. A train can travel on normal rails without Powered Rails.
4. Put a controller adjacent to a branching rail and select **JUNCTION** in its menu. The route chooses its branch. **Lock** preserves the current branch and prevents incompatible routes.
5. Use the Crossing Rail where perpendicular rails overlap; its route logic allows straight passage only. All crossing directions share one safe conflict area. A comparator on a controller reads 15 for an occupied adjacent rail, 8 for a reserved rail, and 0 for a free rail.

The server saves cart UUIDs, train compositions, stations and destinations per dimension. Trains start stopped after a restart and need a new Start command. Routes are calculated only between loaded chunks, with an 8192-state search limit. An unsafe, missing or unloaded route stops the train. Admins can inspect known trains via `/railnet list`.

## Scope and known limitations

This is an early playable implementation, **not a 1.0 release**. It has no full-screen GUI, lines, timetables, display panel, interval service, route cache or compressed graph yet. Route search is bounded and on demand rather than world-wide or per tick. The train tick currently checks a short look-ahead and reserves occupied rail positions; it has not been load tested for dozens of trains or 16+ carts. The controlled carts still run Vanilla's entity tick before the server adjusts their positions, so crossing and collision behaviour need in-game verification. Long tracks with unloaded sections cannot be routed until their chunks are loaded. Station records are created when controllers are placed or opened. Do not use this prototype in an important world without a backup.

Priorities for a release are a truly stable per-cart movement integration, incremental compressed rail graph, braking/reservation tests, in-game screen, redstone signal settings, and server load testing. The original numbered concept documents remain in `docs/` to guide that work.
