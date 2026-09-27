# RailNet

**RailNet** expands Minecraft's Minecart system into a flexible train network while staying close to the Vanilla experience.

Couple Minecarts into trains, create stations, choose destinations and let your trains travel automatically across your railway network.

> **Current version: 0.6.0 Beta**
>
> RailNet is still in active development. Bugs, unfinished features and unexpected behavior should be expected. Some planned features are not available yet.

## Current features

### 🚂 Create Trains

Connect Minecarts using **Vanilla Chains** and turn them into a single train.

You can also couple Minecarts entirely through the **RailNet Tool**: select the first Minecart, open the second and confirm. Trains can consist of multiple Minecarts and can be split again when needed. A single-Minecart train is created explicitly through its menu.

### 🛤️ Travel on Normal Rails

RailNet trains can travel on ordinary rails.

**Powered Rails are not required** to keep a train moving, so existing railway builds can be used without filling the entire track with Powered Rails.

Route finding searches across connected stretches of track, switches, crossings and controllers. It reads the current rail state in loaded chunks, with a limited rail search as a fallback. If a rail is missing, a waiting train can periodically look for a safe alternative route. Without one, it remains stopped.

### 🚉 Stations & Destinations

Create stations using the dedicated **Station** block and give them custom names.

Select a destination from your train and start the journey. RailNet will guide the train through the available railway network.

### 🔀 Automatic Junctions

The dedicated **Switch** block can manage railway junctions.

When a train approaches a junction, the correct direction can be selected automatically based on its destination. Junctions can also be locked when you want to keep a specific route selected.

### 🔄 Reverse Trains

Trains can reverse their logical direction without rebuilding or reconnecting the Minecarts. When a line starts in the opposite direction, the Minecart order can be reversed automatically when needed.

### ✚ Crossing Rails

RailNet adds a dedicated **Crossing Rail** for places where two railway lines cross.

Trains continue straight through the crossing instead of accidentally turning onto the other track.

### 🚦 Basic Traffic Protection

Pairs of **Block** components define track sections that can be reserved for a train. Before allowing entry, the server checks the whole section for loaded chunks, other Minecarts and existing reservations. The reservation works in both directions and remains until the last Minecart passes or the section is actually clear.

Block and Signal components show **clear**, **reserved** or **occupied** in their menus. Comparator level 8 also reflects section reservations.

### 🚆 Lines

Create named railway lines with multiple stations and an ordered stop sequence.

For example:

```text
Blue Line

Central
↓
University
↓
Industrial District
↓
Airport
```

Trains can follow a line instead of requiring a new destination after every trip. Lines support stop durations, intervals, fixed departure times, loops and reversing at terminal stations.

### ⏱️ Timetables & Interval Services

Optional scheduling features let you set up automated services, including:

- departures at regular intervals
- station stop times
- fixed departure times
- recurring line services

Timetables are optional. Normal destination-based travel still works without them.

### 🏭 Depots

**Depot** is one of RailNet's dedicated component blocks. More depot automation is planned for future versions.

### 📺 Timetable Displays

A dedicated **Timetable Display** block can be bound to a destination. It can call a train independently of the train's line and refresh the list of trains dispatched to that destination.

Vanilla Signs remain useful for static station names and directions.

### 🔴 Redstone Integration

RailNet components provide Redstone controls for railway automation. An input can lock a switch when a signal is active or stop a train at a controller. Comparator output states update after a train leaves.

### 🖥️ In-Game Interface & Diagnostics

Use the craftable **RailNet Tool** to open Minecart and component menus. Right-clicking a Minecart with the tool opens its menu instead of mounting it; main-hand and off-hand use are supported. Chains remain available for coupling.

Trains, components, lines, timetables and displays use a custom interface with buttons, pages, icons and text input. The server validates menu actions against a short-lived session, the tool, distance and object state.

The train menu shows its destination, status, route progress, obstacles, pending departure, detected block sections and reservations held by other trains. You can also request a new route from the menu. German and English translations are included.

The `/railnet` commands remain available to server operators, but are optional for normal use.

### 🧱 Separate Components & Existing Worlds

The six former controller modes are now separate blocks: **Station**, **Switch**, **Block**, **Depot**, **Signal** and **Timetable Display**. Each has its own item, recipe, block states and texture. The Crossing Rail remains separate.

Existing `rail_controller` blocks and world data can still be loaded. The RailNet Tool offers a replacement action that transfers a legacy controller's settings and station ID to the matching new block. The old controller has no recipe and is no longer in the Creative inventory.

---

# Planned Features

RailNet will continue to grow beyond the current Alpha release.

## 🏭 More Depot Automation

Future depot features may include parking trains, assigning them to lines and dispatching them when routes become available.

## 📺 Larger Display Panels

The Timetable Display already shows trains dispatched to its chosen destination. Larger station and departure boards remain ideas for future versions, for example:

```text
Platform 2

Blue Line → Airport
Departure 01:42
```

or a board covering several destinations:

```text
Airport        2 min
University     5 min
Harbor         8 min
```

## 🚄 Train Categories

Planned advanced options include different train categories such as:

- Passenger
- Express
- Freight
- Maintenance

Railway sections and controllers may later be configured to allow or restrict certain trains or lines.

## 🧭 Advanced Route Options

Larger railway networks are planned to receive additional routing options such as:

- preferred routes
- avoided routes
- one-way tracks
- train and line restrictions
- priorities

A persistent rail-graph cache with local updates and routing on lines without paired Block components are also planned. These systems are intended to stay optional so simple railway networks remain easy to use.

## 🚦 Improved Traffic Management

Traffic management will continue to be expanded with features such as automatic resolution of opposing-traffic deadlocks and better handling of busy junctions. Larger networks also need further load testing.

---

# Beta Notice

RailNet **0.6.0 is an Beta release**.

The core concept is playable, but the mod is **not feature-complete or considered stable yet**.

You may encounter:

- bugs
- unfinished features
- unusual Minecart behavior
- problems with complex crossings or collisions
- limitations on very large railway networks

For now, using RailNet in an important world without a backup is not recommended.

Bug reports and feedback are especially valuable during the Beta phase.

---

# Current Development Goal

The goal is to turn RailNet into a train system that remains simple for small Vanilla-style railways while also supporting larger automated networks.

A basic railway should remain as simple as:

**Build rails → Create stations → Couple Minecarts → Choose destination → Start**

More advanced systems such as lines, displays, schedules, depots, Redstone automation and traffic management are available when you need them, with further improvements planned.