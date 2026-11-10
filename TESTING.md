# XP Saver: manual test checklist

Setup (26.3 renders with OpenGL or Vulkan; any normal graphics driver works): Minecraft 26.3 with Fabric Loader 0.20.1+, Fabric API 0.165.2+26.3 and `xp-saver-1.3.0+26.3.jar` in `mods/`.
Create a **Survival** world with **Allow Commands: ON** and set `/difficulty peaceful`, then run:

```
/give @s diamond_sword 2
/give @s enchanted_book[stored_enchantments={"minecraft:sharpness":5}]
/give @s enchanted_book[stored_enchantments={"minecraft:smite":5}]
/give @s enchanted_book[stored_enchantments={"minecraft:looting":3}]
/give @s enchanted_book[stored_enchantments={"minecraft:unbreaking":3}]
/give @s enchanted_book[stored_enchantments={"minecraft:mending":1}]
/give @s enchanted_book[stored_enchantments={"minecraft:fire_aspect":2}]
/give @s lapis_lazuli 64
/xp add @s 100 levels
/setblock ~ ~ ~2 anvil
/setblock ~ ~ ~-2 enchanting_table
```

## Items that already have enchantments
```
/give @s diamond_sword[enchantments={"minecraft:sharpness":4,"minecraft:unbreaking":3,"minecraft:mending":1},repair_cost=3]
```
plus books Sharpness IV, Looting III, Mending, Smite V, Unbreaking II, Sharpness III, and `/xp set @s 100 levels`.
- [ ] Anvil, sword in the left slot, **Recommended**: "↑ Sharpness IV → V", "✘ Smite V (blocked by Sharpness)",
      "✔ Mending", "✔ Unbreaking III" (green, already at max level); Looting, Fire Aspect, Knockback, Sweeping Edge as plain bullets.
- [ ] **Cheapest order**: under Sharpness IV "→ Sharpness V (now IV)"; Sharpness III in yellow "item has Sharpness IV: no gain
      alone". At the bottom, under "Won't help this item:", greyed with ✘ and the reason: Mending and Unbreaking II "already on
      the item", Smite V "blocked by Sharpness". Clicking them does nothing.
- [ ] Tick Sharpness IV and Looting III: 2 steps, 4 + 15 = **19 levels** (one book at a time would be 21). Run both steps:
      XP 100 → 81, sword has Sharpness V, Unbreaking III, Mending, Looting III.
- [ ] K planner, holding `diamond_sword[enchantments={"minecraft:smite":5,"minecraft:unbreaking":2},repair_cost=1]` with books
      Unbreaking II, Sharpness V, Mending, Looting III: 7 + 2 + 9 = **18 levels** (Unbreaking II + II = III), and
      "Can't be added (conflict): Sharpness (Smite V is on it)".

## Where it runs (client only)
Install XP Saver on your own game only. Servers and LAN hosts don't need it, and players without it can join the same world.
- [ ] Single player: every section below works.
- [ ] Open to LAN: the host and a guest who both have the mod each see their own panels, and a guest without the mod can still join.
- [ ] Dedicated server with no mods (plain vanilla 26.3): join with the mod and repeat the anvil dropdown run below.
      The server takes exactly the planned levels (check with `/xp query @s levels`) and the sword gets all seven enchantments.
- [ ] Put the jar in a Fabric **server**'s `mods/` folder by mistake: the server starts and XP Saver is not in its
      "Loading N mods" list (Fabric skips client-only mods there).

## F1: Enchanting table
- [ ] Put a fresh diamond sword and lapis in the table. A "Recommended" panel appears to the LEFT of the GUI (clear of JEI on the right) and does not cover it.
- [ ] The panel lists "Pick one (taste)" with Bane of Arthropods V / Sharpness V / Smite V, then Fire Aspect II, Knockback II, Looting III, Mending, Sweeping Edge III, Unbreaking III. No curses.
- [ ] Under "Table offers", each visible offer shows the same enchantment as the vanilla hover clue, with a green ✔ (and "(choice)" for Sharpness/Smite/Bane).
- [ ] With a book in the slot, the panel says "A book accepts any enchantment."
- [ ] Click an offer: once the item comes back enchanted it moves into your inventory by itself (the lapis stays), so the next
      item can go straight in. Same for a book (it comes out as an enchanted book). With too few levels or lapis nothing happens.
- [ ] Try a bow: Infinity and Mending appear together under "Pick one". Try a pickaxe: Fortune and Silk Touch do.
- [ ] Try a trident: "Pick one" offers "Channeling + Loyalty III" or "Riptide III" (Loyalty and Channeling go together;
      Riptide excludes both).

## F2: Anvil ✔/✘ (hover the mark to the right of the output slot)
- [ ] Fresh sword + Sharpness V book: green ✔, tooltip "Valid, cheapest order: 5 levels", and the game shows "Enchantment Cost: 5".
- [ ] Take the result (100 → 95 levels), then that sword + Smite V book: red ✘, "Invalid: the enchantments conflict or don't fit this item".
- [ ] Sharpness V book (left) + fresh sword (right): red ✘, "Invalid this way round. Swap the inputs: 5 levels".
- [ ] Mending book (left) + Looting book (right): red ✘, "Cheaper order exists: swap the inputs (2 instead of 6 levels)". Swap them: green ✔ at 2 levels.
- [ ] Too Expensive: `/give @s diamond_sword[repair_cost=31]`, then add a book with `repair_cost=7`, e.g. `/give @s enchanted_book[stored_enchantments={"minecraft:sharpness":5},repair_cost=7]`. Expect a red ✘ "Too Expensive! 43 levels (the anvil allows at most 39)", matching vanilla's "Too Expensive!".
- [ ] Same sword with a `repair_cost=3` Sharpness V book costs 39: green ✔ (the 26.3 cutoff is 40, not 39).
- [ ] Damaged sword + diamond (material repair): green ✔ at the game's cost.

## Anvil dropdown: Recommended enchantments
- [ ] Open an anvil with a diamond sword in the **left** slot. Two buttons sit left of the anvil: **Recommended enchantments ▼**
      on top and **Cheapest order ▼** under it (labels shorten when the buttons are narrow; on narrow screens the buttons
      sit in a row above the anvil instead, see "Look and screen sizes").
- [ ] Click Recommended: "For: Diamond Sword", then the same groups as the enchanting-table panel (always pick, pick one).
      Opening it closes Cheapest order, and the reverse.
- [ ] Empty left slot: "Put an item in the left slot to see its recommended enchantments." A book: "A book accepts any enchantment."

## Enchantment info icons ("!")
- [ ] Every enchantment entry has a small boxed "!" at the right end of its first line: anvil Recommended list, F1 table
      Recommended list and Table offers, and the book rows in Cheapest order. Headings, hints and step lines have none.
- [ ] Hover a "!": a tooltip with the name (gold; red for curses), a short description, "Max level: …", "Can't be combined
      with: …" in red when it has clashes (Sharpness: Bane of Arthropods, Breach, Density, Impaling, Smite), and for
      Mending, Frost Walker, Soul Speed, Swift Sneak, Wind Burst and the curses a line saying the table never offers it.
- [ ] A "Pick one" line holding two enchantments (trident: Loyalty + Channeling) shows both in one tooltip, split by a blank line.
- [ ] Entry text never runs under the icon; long names wrap instead. Clicking a book row's icon still ticks the book.
- [ ] Both styles: the icon is drawn in the style's text color (light on Dark, dark gray on Inventory).

## Look and screen sizes (all panels: F1 table, anvil dropdowns, planner)
- [ ] Default (Panel Style: Dark): every panel is a see-through dark box with no frame, shadowed text and a thin scrollbar
      on the right. Headings gold, normal text and step titles white, step details gray, ticked books and the total green,
      the step in the anvil and "Step N loaded" yellow, warnings light red, done or blocked rows mid gray.
- [ ] Panel Style: Inventory: every panel looks like a vanilla container window: light gray face, white top-left and dark gray
      bottom-right edge, black outline with cut corners, dark gray text without a shadow, and a sunken scrollbar with a raised thumb.
- [ ] In both styles the F2 mark is a dark green ✔ or dark red ✘: it sits on the anvil's own light window.
- [ ] Resize the window with the anvil open (or change Options → Video Settings → GUI Scale) and check each size:
  - [ ] 1920×1080 (GUI scale 4) and 1280×720 (scale 3): the buttons are stacked left of the anvil, the panel below them, text at full size.
  - [ ] 1024×768 (scale 3) and 1280×1024 (scale 4), narrow 4:3 and 5:4 screens: the three buttons move to a row above the
        anvil (Recommended, Cheapest order, Next step), and the panel left of the anvil draws its text smaller but sharp.
  - [ ] 640×480 (scale 2): same row layout; the panel text is small (like GUI scale 1) but every line fits.
  - [ ] At every size nothing covers the anvil or the inventory, clicks on book rows tick the row under the cursor,
        and a full 7-book run still costs exactly 49 levels.
- [ ] The F1 table panel follows the same rules (full size where it fits, smaller on narrow screens) and scrolls with the
      mouse wheel when its list is taller than the table window.
- [ ] The planner is a window centered on the screen; resize it and the lines re-wrap to fit. On a short screen the wheel scrolls it.

## Config screen (Mod Menu 21.0.0 for 26.3; optional)
- [ ] Without Mod Menu the game starts normally and `config/xpsaver.json` is created with the defaults.
- [ ] With Mod Menu: Mods → XP Saver has a config button. It opens "XP Saver Settings" in the game's own options style,
      grouped as Features, Position and Size, Text and Readability, Look and Colors, Planner. Every control has a tooltip
      with its default and range.
- [ ] Set Panel Side to Right and Panel Y Offset to about 40, press Done, open an enchanting table with a sword: the panel is now
      right of the window and lower. Open an anvil: its buttons and panel moved the same way. Nothing needed a restart.
- [ ] Quit the game, start it again: the panel is still on the right and lower (`config/xpsaver.json` holds `"anchor": "RIGHT"`).
- [ ] Turn off a feature (e.g. Anvil ✔/✘ Mark): it disappears at once; turn it back on: it returns.
- [ ] Max Levels per Anvil Step 20: the Cheapest order plan re-plans with every step ≤ 20 and the Total line says "every step ≤ 20".
- [ ] Panel Style switches between Dark and Inventory at once; the screen stays at the same place and the color rows below
      change to that style's colors (each style keeps its own). Reset to Defaults goes back to Dark.
- [ ] With Panel Style Inventory, type `9090` in the Text color box: the code turns red and nothing changes. Type `909090`: the
      preview shows 1.9:1 ⚠ in red and the tooltip says the contrast is too low. Default colors of both styles show 4.5:1 or more.
- [ ] Lower Panel Opacity: the ratios drop (they count the brightest and darkest world behind a see-through panel).
- [ ] Reset to Defaults asks first; Yes puts every value back (and the file); No keeps them.
- [ ] Hand-edit the file with a bad value (`"offsetX": 999`, `"anchor": "UP"`): on the next start they come back as 100 and Left.

## Readability (defaults)
- [ ] Every default text color has at least 4.5:1 contrast on its style's panel, even with the brightest or darkest world behind
      the 90% Dark panel (see the ratios on the config screen).
- [ ] Hovering a book row in Cheapest order highlights it (both lines when it wraps) and shows the pointing-hand cursor;
      header and step rows do not highlight.
- [ ] The steps of the cheapest order are separated by a blank line.

## Anvil dropdowns: scrolling and readable text
- [ ] Long lines wrap onto an indented next line; no word is cut off.
- [ ] Ticking or unticking a long book name (Sweeping Edge III) does not move the rows below it.
- [ ] When the list is taller than the screen, a scrollbar appears on the right; the mouse wheel over the panel scrolls it,
      and the wheel elsewhere does nothing to it. Ticking a book still hits the row under the cursor after scrolling.

## Anvil dropdown: tick books, then load the cheapest order step by step
- [ ] Open an anvil and put a fresh diamond sword in the **left** slot. Click **Cheapest order ▼** (left of the anvil).
      The panel lists every enchanted book in your inventory with a `[ ]` box, the most useful first (guide order, higher
      levels first), and **Next step ▶** shows greyed out below the buttons straight away.
- [ ] Tick Unbreaking III, Mending, Sharpness V, Sweeping Edge III, Looting III, Fire Aspect II and Knockback II (leave Smite).
      The order updates after each tick and ends at 7 steps, **Total: 49 levels**, every step ≤ 29.
- [ ] Press **Next step ▶**. Step 1's two inputs move into the anvil, the cost matches the list (6), and the result goes into
      your inventory by itself: "Step 1 done (6 levels)." The button stays greyed out until the result is in.
- [ ] Press Next step 6 more times (no shift-clicks) until "Done: all 7 steps applied". Your level drops by exactly 49, and the
      sword has all seven enchantments. Smite V stays in your inventory.
- [ ] Untick a book mid-way: the plan resets and is recomputed from what is now in the anvil and inventory.
- [ ] Press Next step while holding an item on the cursor: red "Put down the item on your cursor first."
- [ ] With fewer levels than a step costs, Next step loads it but takes nothing: red "Step N loaded, but it costs X levels and you
      have Y." After `/xp add @s 30 levels`, keep pressing Next step: the run never gets stuck. A step whose inputs are all there is loaded and taken, and a skipped step is loaded again once a later one needs its result.
- [ ] Inventory full when a result is ready: red "no room in your inventory. Take the result by hand." Nothing is lost.
- [ ] Finishing still costs exactly the plan's total (4 books on a fresh sword: 23 levels, 100 → 77).
- [ ] Type a new name in the rename box, then press Next step: the name resets with the new item, so no rename level is added.
- [ ] Pick up an item on the cursor and click the open panel or the dropdown buttons: they ignore the click, the item stays
      on the cursor (not thrown), and the very next click on a slot puts it down.
- [ ] After clicking any dropdown button, typing goes into the rename box (the anvil does not close on E).
- [ ] Put an enchanted book in the left slot: the order panel says "A book is in the left slot: put the item to enchant there instead."
- [ ] Tick books quickly one after another: the game never stutters; "Working out the cheapest order…" may flash for a moment.

## 1.6.0: best books, XP check, tidier list
Same setup as above (fresh sword, the eight books), plus two Sharpness IV books, Unbreaking II, Protection IV, a book with
Looting III + Curse of Vanishing, `diamond_sword[enchantments={"minecraft:unbreaking":3,"minecraft:mending":1}]`, and
`/xp set @s 30 levels`.
- [ ] While the steps run, a ticked book disappears from the list once a step uses it; when all steps are done, no
      "Air with" rows or empty ticked rows are left.
- [ ] Ticking the first book doesn't move the list: Next step is already there (greyed out) and becomes active with a plan.
- [ ] Put a Sharpness V book in the anvil's right slot by hand, then open Cheapest order: it is listed (and can be ticked).
- [ ] **» Tick the best books for this item** ticks Unbreaking III, Mending, Sharpness V, Sweeping Edge III, Looting III,
      Fire Aspect II and Knockback II, and the order is the K planner's: 7 steps, **49 levels**.
- [ ] Protection IV ("can't go on this item") and the cursed book ("would add Curse of Vanishing") are under "Won't help this
      item:" at the bottom, not tickable. The enchanted sword copy is listed and can be ticked.
- [ ] Tick both Sharpness IV books as well: still 49 levels, plus "Not needed (cheaper without): Sharpness IV book; Sharpness IV book".
- [ ] With 30 levels: steps 5 to 7 show ✘ in red, and "You have 30 levels: enough up to step 4. Need 19 more." With 60 levels:
      "You have 60 levels: enough." Run every step: 60 → 11 and the sword has all seven enchantments, no curse.
- [ ] Press Next step with fewer levels than the step costs: red "Step 1 loaded, but it costs 6 levels and you have 5."
- [ ] Anvil ✔/✘ with 2 levels and a 4-level combine: red ✘, "Cheapest order, but it costs 4 levels and you have 2".
- [ ] K planner with the same inventory: the same 7 steps, red "(not enough XP)" on steps you can't afford yet, the XP line,
      and no "approximate" note (up to 11 useful books and item copies are checked exactly).
- [ ] K planner with only two Sharpness IV books and a fresh sword: both books used, 2 steps, and the sword ends with Sharpness V (IV + IV).
- [ ] With JEI installed, the dropdown and the JEI list don't overlap, and the dropdown ends level with the bottom of the
      anvil window, so JEI's bookmark/config buttons in the bottom-left corner never cover its last lines (scroll to see them).

## Cheapest order for every item (automated: `AllItemsOptimalTest`)
For each item in the guide (fresh, and with 1 to 4 earlier anvil uses) the planner is compared with a separate brute-force
search over every possible sequence of anvil steps. `SelectorTest` checks the choice of books too: for 1,500 random inventories
(duplicates, merge chains, enchanted item copies, prior work) it must match a brute force over every subset of the books and
every merge order. Both must agree on: lowest total levels, then the lowest prior-work
penalty left on the finished item, then the smallest single step. The wiki's own orders are replayed through the game's
cost formula, and the planner must never cost more than they do. `RandomizedPlannerTest` does the same for 400 random
situations (existing enchantments, prior work, multi-enchantment, conflicting and unsupported books) and checks that every
step uses pieces that exist, stays at or under 29 levels and adds up to the total.

## F3: Planner (default key K; change it in XP Saver settings → Planner → Planner Key, or Options → Controls → Key Binds → XP Saver)
Books are chosen from the Minecraft Wiki "Best enchantments guide" (Sharpness over Smite, Protection IV, Depth Strider, ...).
No anvil step in a plan may cost more than `Planner.MAX_STEP_COST` = 29 levels.
- [ ] `/give` a fresh diamond sword plus books: Unbreaking III, Mending, Sharpness V, Sweeping Edge III, Looting III, Fire Aspect II, Knockback II, Smite V.
      Hold the sword and press K. Expect 7 steps costing 6, 3, 11, 2, 11, 2, 14 = **49 levels**: the guide's own order (biggest step 14). Smite is not used.
- [ ] Follow the steps in an anvil. Each step's "Enchantment Cost" matches the list.
- [ ] Swap in a worn sword: `/clear @s diamond_sword` then `/give @s diamond_sword[repair_cost=15]`. Press K: 3 steps (3, 3, 28) = 34 levels,
      and a red "Left out…" list naming Sweeping Edge, Looting, Fire Aspect and Knockback (the guide's lowest priorities).
- [ ] Hold an item the guide doesn't cover (e.g. a book): "The best enchantments guide has no section for this item."
- [ ] Rebind K to another key in Controls, and the new key opens the panel.
- [ ] XP Saver settings → Planner → **Planner Key: K**. Click it: it shows `> K <`. Press J: it shows `Planner Key: J` and Reset
      becomes active. Close the screen: `options.txt` has `key_key.xpsaver.planner:key.keyboard.j`, J opens the planner, K doesn't.
- [ ] Bind it to a key the game already uses (P): the button shows `[ P ]` in yellow and its tooltip names the other bindings.
- [ ] Esc while it shows `> K <` leaves it unbound; a mouse button can be bound too. Reset (beside it) or Reset to Defaults brings K back.
- [ ] Options → Controls → Key Binds → XP Saver shows the same key as the settings screen, both ways.

## Log
- [ ] `logs/latest.log` has no errors or exceptions mentioning `xpsaver`.
