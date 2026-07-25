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

The <ItemLink id="ae2lt:tianshu_pattern_encoding_terminal" /> provides everything the normal Pattern Encoding Terminal does, plus enhanced processing-pattern encoding, pattern upload, closed-loop pattern authoring, and inventory-maintenance configuration. Attach it to ME cable like any terminal. **It does not require a Tianshu Supercomputing Array on the network**: pattern encoding, processing-mode enhancements, uploading to providers, and even closed-loop pattern authoring all work normally without one.

<RecipeFor id="ae2lt:tianshu_pattern_encoding_terminal" />

## Binding to a Tianshu

Only two features require a formed Tianshu Supercomputing Array: uploading closed-loop patterns into Closed-Loop Pattern Storage, and inventory maintenance. When a Tianshu exists on the network, the opened terminal locks onto the first available formed one; if none is available yet, it locks onto the first Tianshu that comes online.

The terminal never rebinds to a different Tianshu while it stays open. If the locked Tianshu goes offline or unforms, the related actions simply fail; close and reopen the terminal to pick a target again.

The Closed-Loop Pattern Storage installed in the bound Tianshu's cooling-compatible positions appears as an extra pattern inventory in the terminal's network-content list. The entry is shown only while the structure is formed and its port is online. Each Closed-Loop Pattern Storage provides **36** closed-loop pattern slots, and installed storages add up. Closed-loop patterns can be taken out of the list for inspection or editing; returning them to the storage requires the terminal's upload function.

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

* **Crafting patterns** automatically match only provider groups for Molecular Assemblers, Extended Molecular Assemblers, EAE/EAEP Assembler Matrices, ECO Crafting Subsystems, and Tianshu Matter Warping Matrices. Matter Warping Matrices have priority; the other groups use the first compatible free slot in stable order.
* **Processing patterns** open the provider-selection screen: all visible Pattern Providers on the network are listed grouped by name, showing provider counts and free slots per group, with search by name, item, or tooltip.
* **Closed-loop patterns** upload to the bound Tianshu's Closed-Loop Pattern Storage.

The provider-selection screen also supports **alias mappings**: map a recipe-source keyword to a provider alias so similar recipes locate their target provider faster. Shift + right-click a list entry to bind that machine's name as an alias directly.

The terminal settings configure an **upload trigger**: holding a chosen key (or no key) while encoding automatically enters the matching upload flow; it can also be set to manual upload only. The manual upload button always remains available.

The terminal enables **duplicate-pattern encoding interception** by default. Before a crafting, processing, or closed-loop pattern is committed, it checks every pattern inventory visible in the Pattern Access Terminal on the current ME network. If an identical definition already exists, encoding is cancelled without consuming a blank pattern. Closed-loop patterns compare their members, ratios, seeds, external inputs, outputs, and multipliers and do not persist a separate UUID. This client preference can be disabled in the terminal settings to allow duplicate patterns to be encoded and uploaded normally.

## Closed-Loop Patterns

> Closed-loop authoring is an advanced topic. This section assumes that you already understand ordinary pattern encoding and AdvancedAE's Certus Quartz loop using the Reaction Chamber.

Pattern chains sometimes feed an ingredient back into their own output: `a → 2a`, or `a → b` followed by `b → 2a`. Smithing Template duplication and AdvancedAE's Reaction Chamber crystal automation are common examples. Encoding such a chain normally can leave AE2 reporting missing ingredients because its planner stops recursive expansion to prevent an infinite loop.

**Closed-loop patterns** are designed for these cases.

A closed-loop pattern bundles a set of interlocking patterns into one combined recipe that the Tianshu executes as a single job. You can think of it as a macro package: instead of packaging items or fluids, it packages the **patterns** that make up a self-sustaining loop. Prepare the ordinary patterns that form the loop before authoring the closed-loop pattern. The examples below use AdvancedAE's Certus Quartz loop in the Reaction Chamber.

A closed-loop pattern consists of:

* **Member patterns** (up to 27): the ordinary patterns forming the loop, each annotated with copies executed per cycle. All copy counts must form a minimal integer ratio; 2:4 is invalid and must be written as 1:2.
* **Primary and secondary outputs:** Mark exactly 1 primary output and up to 8 secondary outputs from the loop's net production. The primary output must have a positive net gain. For example, consuming 128 Certus Quartz Crystals and producing 256 is valid, as is producing 192 Certus Quartz Crystals, 16 Certus Quartz Dust, and 16 Charged Certus Quartz Crystals. Producing only 64 Certus Quartz Crystals, 48 Certus Quartz Dust, and 48 Charged Certus Quartz Crystals has a negative net gain and cannot be encoded. Reversible conversions such as `1 Iron Block → 9 Iron Ingots` paired with `9 Iron Ingots → 1 Iron Block` also have no net gain and are rejected.
* **External inputs:** Materials each cycle draws from the network, computed automatically and shown read-only. Water supplied to the Reaction Chamber loop is one example.
* **Seeds:** Items advanced to start the loop, computed automatically and shown read-only. In the example loop, at least 80 Certus Quartz Crystals—or 16 Charged Certus Quartz Crystals plus 16 Certus Quartz Dust—must be available before crafting can start.

~~Matter from nothing? Interesting. If you did not already know that an ME network is only a logistics network... now you do.~~

You must supply the materials required to start the loop.

### Authoring Workflow

1. Switch to Closed-Loop Pattern mode and place—or encode—the ordinary pattern for the loop's primary product in the terminal's **encoded-pattern output slot**. For a Certus Quartz closed loop, this could be a processing pattern such as `16 Charged Certus Quartz Crystals + 16 Certus Quartz Dust → 64 Certus Quartz Crystals`, or a crafting pattern such as `1 Certus Quartz Block → 4 Certus Quartz Crystals`; any ordinary pattern whose output is Certus Quartz can be used as the starting point.
2. Click **Fill**: the terminal searches the network for pattern combinations that close the loop around that product. When several candidates exist, clicking again cycles between them.
3. Open the **Details** screen to review and adjust: add, remove, and reorder members, edit per-cycle copies, and mark primary and secondary outputs (click to toggle declaration, Shift-click to set primary).
4. Adjust the two multipliers on the settings page:
   * **Job seed waves:** How many seed sets one job borrows at startup—effectively the job's parallelism. Higher values increase per-job throughput and the startup advance.
   * **Stored task sets:** How many jobs' worth of seeds the Tianshu keeps pre-stocked—effectively the number of jobs that can run in parallel.
5. When the status reads ready to encode, encode to obtain a <ItemLink id="ae2lt:closed_loop_pattern" />, then upload it into the Closed-Loop Pattern Storage. Authoring and encoding a closed-loop pattern needs no Tianshu; however, the pattern must be stored in some Tianshu's Closed-Loop Pattern Storage before it can execute.

> Authoring and encoding do not require a Tianshu. Uploading a closed-loop pattern requires a formed Tianshu with at least one Closed-Loop Pattern Storage and one Closed-Loop Seed Storage. To execute closed-loop jobs, the seed storage must also contain a compatible ME Storage Cell in which to keep the seeds.

Members can also be filled entirely by hand when no automatic candidate exists. An encoded closed-loop pattern can be re-inserted to load it for editing; encoding again updates the original pattern. A closed-loop pattern may itself be nested as a member of another loop; it is flattened during encoding, and the flattened member total must still not exceed 27. `(In other words, the old fake-crafting workaround is no longer available—but how many recipes truly need more than 27 member patterns?)`

The status line reports the specific reason a draft cannot encode — for example an unreadable member pattern, non-minimal copy ratios, a missing primary output, or a loop whose inputs and outputs do not balance.

## Inventory Maintenance

> Lightning Tech's own ME Requester—just for you!

When a formed Tianshu Supercomputing Array is available on the network, the terminal can configure automatic restock rules per item: crafting starts when the stored amount drops **below** the lower bound, stops when it **reaches** the upper bound, and each job requests the configured batch size.

### Configuration

> Here, “automatic crafting” means a crafting job dispatched by the maintenance system.

* **Shift + middle-click** a craftable item in the terminal to open its Inventory Maintenance editor, which works much like an ME Requester.
* The **Inventory Maintenance** button also opens the overview listing every configured entry—including items whose current stock is zero. Shift-click an entry to edit its rule.
* The **Maintainable** view filters the terminal list to items with maintenance rules for focused review.

**Start below:** Starts automatic crafting when stock falls below this value.
**Stop at:** Stops automatic crafting when stock reaches this value.
**Per job:** Sets the amount requested by each automatic crafting job.
**Enabled:** Enables or disables this item's maintenance rule.
**Check now:** Immediately checks this item's stock.
**Cancel job:** Cancels the currently running automatic crafting job.
**Crafting topology:** Lists related items in the recipe chain and allows their reserved stock to be configured.

Click any entry under **Crafting topology** to set how much of that material maintenance jobs may not consume. Switch **Tianshu global** to **Rule additional** to configure an extra reserve for this rule. A rule-specific reserve only changes the effective protection when it is greater than the matching global reserve; see “Global Reserves” below.

> Configured items show a colored marker in the Tianshu Pattern Encoding Terminal: gray means the rule is disabled, green means the stock target is satisfied, yellow means work is in progress, and red indicates a missing pattern or ingredients.

### Global Reserves

A global reserve protects a quantity of an item from automatic maintenance jobs. Player-requested crafting jobs may still use this protected stock.

Use the search box to find stored network content and click an item to configure it. Items may also be dragged from JEI or EMI onto the target in the lower-left corner.

**Exact match** protects only the selected component variant, including properties such as durability and enchantments. **Ignore components** groups all variants with the same item ID.

**Reserve** sets the protected quantity. Set it to `-1` to reserve all existing stock.

The number of maintenance entries has a safety limit. When it is exceeded (usually after migrating an old save), the overview becomes a recovery page: zero out or delete old entries one by one and the remaining entries appear in turn.
