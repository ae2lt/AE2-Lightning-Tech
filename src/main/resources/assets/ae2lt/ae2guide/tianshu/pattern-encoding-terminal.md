---
navigation:
  title: Tianshu Pattern Encoding Terminal
  icon: ae2lt:tianshu_pattern_encoding_terminal
  parent: tianshu/tianshu-index.md
  position: 40
item_ids:
  - ae2lt:tianshu_pattern_encoding_terminal
  - ae2lt:closed_loop_pattern
---

# Tianshu Pattern Encoding Terminal

The <ItemLink id="ae2lt:tianshu_pattern_encoding_terminal" /> provides everything the normal Pattern Encoding Terminal does, plus enhanced processing-pattern encoding, pattern upload, closed-loop pattern authoring, and inventory-maintenance configuration. Attach it to ME cable like any terminal. **It does not require a Tianshu Supercomputer on the network**: pattern encoding, processing-mode enhancements, uploading to providers, and even closed-loop pattern authoring all work normally without one.

<RecipeFor id="ae2lt:tianshu_pattern_encoding_terminal" />

## Binding to a Tianshu

Only two features require a formed Tianshu Supercomputer: uploading closed-loop patterns into Closed-Loop Pattern Storage, and inventory maintenance. When a Tianshu exists on the network, the opened terminal locks onto the first available formed one; if none is available yet, it locks onto the first Tianshu that comes online.

The terminal never rebinds to a different Tianshu while it stays open. If the locked Tianshu goes offline or unforms, the related actions simply fail; close and reopen the terminal to pick a target again.

The Closed-Loop Pattern Storage installed in the bound Tianshu's cooling-compatible positions appears as an extra pattern inventory in the terminal's network-content list. The entry is shown only while the structure is formed and its port is online. Closed-loop patterns can be taken out of the list for inspection or editing; returning them to the storage requires the terminal's upload function.

## Encoding Modes

The terminal offers the normal terminal's crafting, processing, stonecutting, and smithing modes, plus an additional **Closed-Loop Pattern** mode (see below). Mode switching works the same as on the normal terminal.

## Processing-Mode Enhancements

In processing mode the terminal adds:

* **Multiplier buttons:** Multiply or divide all input and output amounts by 2, 4, 5, or 10 to rescale a recipe into whole batches.
* **Advanced encoding** (requires AdvancedAE): Configure an insertion side for each input; once configured, the next encode produces an advanced pattern.
* **Overload encoding:** Choose a match mode for each input and output — “Strict” requires identical item components, “ID only” ignores component differences and matches by item alone. Once configured, the next encode produces an overload pattern.

Advanced and overload encoding are one-shot configurations: they apply to the very next encode only, and switching modes or clearing the screen cancels them.

## Pattern Upload

With a pattern in the encoded-pattern slot, the **Upload** button sends it to its destination without manual carrying:

* **Crafting patterns** go automatically into the first Pattern Provider on the network that has a free slot and accepts them.
* **Processing patterns** open the provider-selection screen: all visible Pattern Providers on the network are listed grouped by name, showing provider counts and free slots per group, with search by name, item, or tooltip.
* **Closed-loop patterns** upload to the bound Tianshu's Closed-Loop Pattern Storage.

The provider-selection screen also supports **alias mappings**: map a recipe-source keyword to a provider alias so similar recipes locate their target provider faster. Shift + right-click a list entry to bind that machine's name as an alias directly.

The terminal settings configure an **upload trigger**: holding a chosen key (or no key) while encoding automatically enters the matching upload flow; it can also be set to manual upload only. The manual upload button always remains available.

## Closed-Loop Patterns

A closed-loop pattern bundles a set of interlocking patterns into one combined recipe that the Tianshu executes as a single job. The typical case is a production chain that consumes and then regenerates certain intermediates: these cycling items are called **seeds** — they are advanced once when the job starts, and the running loop sustains them afterwards.

A closed-loop pattern consists of:

* **Member patterns** (up to 27): the ordinary patterns forming the loop, each annotated with copies executed per cycle. All copy counts must form a minimal integer ratio — 2:4 is invalid and must be written as 1:2.
* **Primary and secondary outputs:** From the loop's net production, mark exactly 1 primary output (the crafting-request target) and up to 8 secondary outputs (additional declared net outputs).
* **External inputs:** Materials each cycle draws from the network, computed automatically and shown read-only.
* **Seeds:** Items that must be advanced to start the loop, computed automatically and shown read-only.

### Authoring Workflow

1. Switch to closed-loop mode and place (or encode) the ordinary pattern for the loop's main product in the encoded-pattern slot.
2. Click **Fill**: the terminal searches the network for pattern combinations that close the loop around that product. When several candidates exist, clicking again cycles between them.
3. Open the **Details** screen to review and adjust: add, remove, and reorder members, edit per-cycle copies, and mark primary and secondary outputs (click to toggle declaration, Shift-click to set primary).
4. Adjust the two multipliers on the settings page:
   * **Execution seed multiplier:** How many seed sets a single job borrows at start — i.e. how many loop waves run concurrently. Higher values increase per-job throughput and the startup advance.
   * **Stored task multiplier:** How many jobs' worth of seeds the Tianshu keeps pre-stocked, determining how many jobs the stored seeds can supply simultaneously.
5. When the status reads ready to encode, encode to obtain a <ItemLink id="ae2lt:closed_loop_pattern" />, then upload it into the Closed-Loop Pattern Storage. Authoring and encoding a closed-loop pattern needs no Tianshu; however, the pattern must be stored in some Tianshu's Closed-Loop Pattern Storage before it can execute.

Members can also be filled entirely by hand when no automatic candidate exists. An encoded closed-loop pattern can be re-inserted to load it for editing; encoding again updates the original pattern. A closed-loop pattern may itself be nested as a member of another loop; it is flattened during encoding, and the flattened member total must still not exceed 27.

The status line reports the specific reason a draft cannot encode — for example an unreadable member pattern, non-minimal copy ratios, a missing primary output, or a loop whose inputs and outputs do not balance.

## Inventory Maintenance

When the bound Tianshu supports inventory maintenance, the terminal can configure automatic restock rules per item: crafting starts when the stored amount drops **below** the lower bound, stops when it **reaches** the upper bound, and each job requests the configured batch size.

* The **Inventory Maintenance** button opens the overview listing every configured entry — including items whose current stock is zero. Shift-click an entry to edit its rule.
* The **Maintainable** view filters the terminal list to items with maintenance rules for focused review.
* The rule editor also shows the item's **crafting topology** (related items along its recipe chain) and lets you configure **reserved stock** for them: reserved amounts are never consumed as ingredients by maintenance jobs. Reserves come in two tiers — Tianshu-wide and rule-specific — with exact or component-ignoring matching.

The number of maintenance entries has a safety limit. When it is exceeded (usually after migrating an old save), the overview becomes a recovery page: zero out or delete old entries one by one and the remaining entries appear in turn.
