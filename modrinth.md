# RailNet

**RailNet** expands Minecraft's Minecart system into a flexible train network while staying close to the Vanilla experience.

Couple Minecarts into trains, create stations, choose destinations and let your trains travel automatically across your railway network.

> **Current version: 0.2 Alpha**
>
> RailNet is still in active development. Bugs, unfinished features and unexpected behavior should be expected. Some planned features are not available yet.

## Current features

### 🚂 Create Trains

Connect Minecarts using **Vanilla Chains** and turn them into a single train.

Trains can consist of multiple Minecarts and can be split again when needed.

### 🛤️ Travel on Normal Rails

RailNet trains can travel on ordinary rails.

**Powered Rails are not required** to keep a train moving, so existing railway builds can be used without filling the entire track with Powered Rails.

### 🚉 Stations & Destinations

Create stations using Rail Controllers and give them custom names.

Select a destination from your train and start the journey. RailNet will guide the train through the available railway network.

### 🔀 Automatic Junctions

Rail Controllers can manage railway junctions.

When a train approaches a junction, the correct direction can be selected automatically based on its destination.

Junctions can also be locked when you want to keep a specific route selected.


### 🔄 Reverse Trains

Trains can reverse their logical direction without rebuilding or reconnecting the Minecarts.

---

# Planned Features

RailNet 0.2 is only the beginning. Several larger systems are planned for future versions.

## 🖥️ Full In-Game Interface

A proper graphical interface is planned for trains, stations and Rail Controllers.

This will make it possible to configure most RailNet features directly in-game instead of relying on text-based controls.

### ✚ Crossing Rails

RailNet adds a dedicated **Crossing Rail** for places where two railway lines cross.

Trains continue straight through the crossing instead of accidentally turning onto the other track.

### 🚦 Basic Traffic Protection

RailNet detect occupied and reserved track sections and prevent trains from entering conflicting routes.

Controllers also provide useful Redstone signals for railway automation.

## 🚆 Lines

Create named railway lines with multiple stations.

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

Trains will be able to repeatedly follow a line instead of requiring a new destination after every trip.

Planned line behaviors include:

- repeated routes
- loops
- reversing at terminal stations
- assigning trains to specific lines

## ⏱️ Timetables & Interval Services

Optional scheduling features are planned for automated services.

Examples include:

- departures every few minutes
- station stop times
- scheduled departure times
- recurring train services

Timetables will remain optional. Normal destination-based travel will continue to work without them.

## 🏭 Depots

Future Rail Controllers will be able to operate as depots.

Depots are planned to support things such as:

- parking trains
- assigning trains to lines
- starting automatic services
- dispatching trains when routes become available

## 📺 Dynamic Display Panels

Dynamic station displays are planned for future versions.

Possible displays include:

```text
Platform 2

Blue Line → Airport
Departure 01:42
```

and larger departure boards:

```text
Airport        2 min
University     5 min
Harbor         8 min
```

Vanilla Signs will still remain useful for static station names and directions.

## 🔴 More Redstone Integration

Rail Controllers are planned to receive additional configurable Redstone inputs and outputs.

Possible uses include:

- stopping trains
- disabling stations
- locking junctions
- closing railway sections
- dispatching trains
- detecting approaching trains
- detecting stopped trains
- displaying route errors

This will allow players to build their own signals and railway control systems using Vanilla Redstone components.

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

These systems are intended to stay optional so simple railway networks remain easy to use.

## 🚦 Improved Traffic Management

Traffic management will continue to be expanded with features such as:

- improved reservations
- safer braking behavior
- better handling of multiple trains
- automatic route recovery
- improved handling of busy junctions
- advanced deadlock handling

---

# Alpha Notice

RailNet **0.2 is an Alpha release**.

The core concept is playable, but the mod is **not feature-complete or considered stable yet**.

You may encounter:

- bugs
- unfinished features
- unusual Minecart behavior
- problems with complex crossings or collisions
- limitations on very large railway networks
- missing interfaces and configuration options

For now, using RailNet in an important world without a backup is not recommended.

Bug reports and feedback are especially valuable during the Alpha phase.

---

# Current Development Goal

The goal is to turn RailNet into a train system that remains simple for small Vanilla-style railways while also supporting larger automated networks.

A basic railway should remain as simple as:

**Build rails → Create stations → Couple Minecarts → Choose destination → Start**

More advanced systems such as lines, displays, schedules, depots, Redstone automation and traffic management can then be added when you need them.