# XP-Saver
XP Saver helps you spend fewer levels at the anvil. It works out the cheapest order to combine your enchanted books, recommends the best enchantments for each item, and warns you before an anvil step wastes XP.

It runs on the client only: it works in single player, on LAN worlds and on servers (including vanilla servers), and servers don't need it.

## Features
- **Cheapest order** (anvil dropdown): tick the books to use and get the order with the lowest total cost, every step at or under 29 levels. **Next step** puts each step's items into the anvil for you.
- **Recommended enchantments** (anvil dropdown and next to the enchanting table): the best enchantments for the item, marked by what it already has.
- **Anvil check**: a ✔ or ✘ next to the output slot, with a tooltip explaining why (invalid, too expensive, or cheaper if the inputs are swapped).
- **Looks like the game**: every panel is drawn like a Minecraft inventory window and fits any window size or GUI scale. On narrow screens the anvil buttons move to a row above the anvil and the panel text is drawn smaller.
- **Planner key (K)**: the cheapest order for the held item using your inventory's books, chosen from the Minecraft Wiki's Best enchantments guide.

## Versions
| Minecraft | Branch |
|---|---|
| 26.3 | `main` |
| 26.2 | [`26.2`](../../tree/26.2) |

## Requirements
- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API (0.165.2+26.3 or newer)
- Java 25

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
