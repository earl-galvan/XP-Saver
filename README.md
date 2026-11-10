# XP-Saver
**This branch is the Minecraft 26.2 version.** The 26.3 version is on `main`.

XP Saver helps you spend fewer levels at the anvil. It works out the cheapest order to combine your enchanted books, recommends the best enchantments for each item, and warns you before an anvil step wastes XP.

It runs on the client only: it works in single player, on LAN worlds and on servers (including vanilla servers), and servers don't need it.

## Features
- **Cheapest order** (anvil dropdown): tick the books to use, or click **Tick the best books** to let the mod pick them. It finds the cheapest mix and order, every step at or under 29 levels: it merges duplicate books (Sharpness IV + IV = V), can use a second enchanted copy of the item, and tells you which ticked books aren't needed. It checks your levels, marking steps you can't afford yet. **Next step** runs one step: it puts the step's items into the anvil and takes the result into your inventory.
- **Recommended enchantments** (anvil dropdown and next to the enchanting table): the best enchantments for the item, marked by what it already has. At the enchanting table, picking an enchantment moves the enchanted item into your inventory by itself.
- **Enchantment info (!)**: every enchantment in these lists has a small "!" icon. Hover it to see what the enchantment does, its max level, what it can't be combined with, and whether the enchanting table ever offers it.
- **Anvil check**: a ✔ or ✘ next to the output slot, with a tooltip explaining why (invalid, too expensive, cheaper if the inputs are swapped, or more levels than you have).
- **Two panel styles**: Dark (default), a see-through dark panel with light text, or Inventory, drawn like a Minecraft inventory window. Both fit any window size or GUI scale. On narrow screens the anvil buttons move to a row above the anvil and the panel text is drawn smaller.
- **Settings screen**: open it from Mod Menu (Mods → XP Saver → config button) to pick the panel style, move the panels, change their size, line spacing and colors, turn features off, or set the most levels one anvil step may cost. Changes apply right away and are saved to `config/xpsaver.json`.
- **Planner key (K by default)**: the cheapest way to give the held item the Minecraft Wiki's Best enchantments guide picks from your inventory's books and enchanted item copies, and whether your levels cover it. Change the key in the settings screen (Planner → Planner Key) or in Options → Controls → Key Binds → XP Saver.

## Requirements
- Minecraft 26.2
- Fabric Loader 0.20.1 or newer
- Fabric API (0.161.0+26.2 or newer)
- Java 25
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) (20.0.3 for 26.2) for the in-game settings button. Without it the mod works the same and settings live in `config/xpsaver.json`.

## Building
```
./gradlew build
```
The mod jar is written to `build/libs/`. `./gradlew test` runs the unit tests for the cost formula and the planner, and `./gradlew runClient` starts a development client. `TESTING.md` is the manual in-game checklist.

## Project layout
- `src/main/java/xpsaver/core`: the anvil cost formula and the planner, in plain Java with no Minecraft classes, so they can be unit tested.
- `src/client/java/xpsaver/client`: the screens, panels and key binding (Fabric API, no mixins).
- `src/test/java/xpsaver/core`: JUnit tests, including a brute-force check of the planner on every guide item and on random cases.

## Note
This mod's code was written with the help of generative AI.
