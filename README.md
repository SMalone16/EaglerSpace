# EaglerSpace

A classroom Paper plugin for **Minecraft/Paper 1.21.11** that turns the upper atmosphere into a lightweight survival mechanic.

## Huxley's Space mechanic

When a player rises **above Y=220** in the normal Overworld, they enter **Space Mode**:

- their personal time is set to midnight;
- they receive an `☄ ENTERING SPACE ☄` title and an eerie ambient sound;
- subtle particles appear around them;
- an oxygen display appears above the hotbar;
- they have **10 seconds of oxygen** without protection;
- **Water Breathing acts as an oxygen supply** and keeps oxygen full;
- at 0 oxygen they take **1 heart of damage every 2 seconds**.

Dropping **below Y=220**, changing to a non-Overworld environment, or disabling the plugin exits Space Mode and restores normal player time.

The plugin safely handles repeated boundary crossings and logout/reconnect by tracking one `IN_SPACE` state per online player. A player at exactly Y=220 keeps their current state: entry requires `Y > 220`, while exit requires `Y < 220`.

## Build

Requirements:

- Java 21
- Maven 3.9+
- Paper API 1.21.11

```bash
mvn clean package
```

The compiled plugin is:

```
target/EaglerSpace-1.0.0.jar
```

GitHub Actions also keeps a selector-ready copy at:

```
dist/EaglerSpace-1.0.0.jar
```

## Install

Copy `EaglerSpace-1.0.0.jar` into the Paper server's `plugins/` directory and restart the server.

The intended classroom target is:

- https://github.com/SMalone16/Eaglercraft-1.21.11-Server

## Configuration

`src/main/resources/config.yml` exposes:

- space boundary
- oxygen duration
- damage interval and amount
- particle interval
- ambient sound interval
- title, particles, and sound toggles

Defaults match the design brief:

- boundary: Y=220
- oxygen: 10 seconds
- damage: 1 heart every 2 seconds once oxygen reaches 0
- Water Breathing: oxygen instantly refills to and remains at 10 seconds
