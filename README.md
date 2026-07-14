# AE2 Lightning Tech [![Modrinth](https://img.shields.io/modrinth/dt/ArHeh5Fz?style=flat&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/ae2-lightning-tech) [![CurseForge](https://img.shields.io/curseforge/dt/1527395?style=flat&logo=curseforge&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/ae2-lightning-tech)

[中文文档](README_zh_CN.md)

An [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that introduces a lightning energy system, advanced machines, and overloaded network components.

> Requires AE2 · Built for Minecraft 1.21.1 / NeoForge

## About

AE2 Lightning Tech turns lightning into a usable resource. Capture natural strikes, refine them into High Voltage and Extreme High Voltage tiers, and feed them into machines to grow Overload Crystals — the foundation of an overloaded ME network with vastly higher throughput, wireless pattern routing, and new processing pipelines.

## Features

### Lightning Energy System
Two tiers of lightning energy — High Voltage and Extreme High Voltage — stored, transmitted and crafted with as a first-class resource alongside FE.

### Lightning Collection
- **Lightning Collector** — catches lightning that strikes nearby rods.
- **Atmospheric Ionizer** — multiblock weather conditioner that produces clear / rain / thunderstorm condensate.
- **Tesla Coil** — fabricates HV and EHV lightning from Overload Crystal Dust and FE.

### Overload Crystals
A budding-crystal progression line built on top of AE2's certus quartz tiers — with **Damaged**, **Cracked**, **Flawed** and **Flawless** budding stages, decay, Crystal Growth Accelerator support, and Overload Crystal Clusters as the final yield.

### Lightning Machinery
- **Lightning Assembly Chamber** — assembles overload components from raw materials and lightning.
- **Lightning Simulation Room** — runs Lightning Transform recipes indoors using a Lightning Collapse Matrix.
- **Overload Processing Factory** — high-throughput parallel processor for overload alloys, plates and cores.
- **Crystal Catalyzer** — runs catalysts in parallel to bulk-craft crystals; the Lightning Collapse Matrix multiplies output.

### Overloaded ME Network
- **Overloaded ME Controller**, **ME Interface**, **Pattern Provider**, and 16 colors of **Overloaded ME Cable** — drop-in upgrades of the AE2 network with vastly higher throughput.
- **Overloaded Pattern Encoder** with byproduct slots and an *Ignore NBT* mode.
- **Wireless Overloaded Controller** + **Wireless Receiver** — connect a Pattern Provider to remote machines without cables, with round-robin or balanced distribution.
- **Overloaded Wireless Connect Tool** — bind providers and power supplies to targets in-world.

## Public API for addon authors

`com.moakiee.ae2lt.api.*` is the only stable surface this mod exposes to third-party mods. Addons can `compileOnly` against this jar and import:

- **`AE2LTCapabilities.LIGHTNING_ENERGY_BLOCK`** — block-side capability returning an `ILightningEnergyHandler`. Registered on the five lightning-grid block entities: Lightning Collector, Lightning Simulation Room, Lightning Assembly Chamber, Overload Processing Factory, Tesla Coil. The handler reads/writes the AE2 grid's lightning storage directly — no reflection required.
- **`LightningTier`** — `HIGH_VOLTAGE` / `EXTREME_HIGH_VOLTAGE`. Serialized names are frozen at `"high_voltage"` / `"extreme_high_voltage"`.
- **`LightningCollectedEvent`** — a cancellable event posted on `NeoForge.EVENT_BUS` from inside `LightningCollectorBlockEntity.captureLightning(boolean)`, after the amount has been rolled but before it is inserted into the grid. Subscribers can cancel the capture or rewrite the amount.
- **`AE2LTBlockEntityIds`** / **`AE2LTRecipeIds`** — frozen `ResourceLocation` constants for the public block-entity and recipe types.
- **`com.moakiee.ae2lt.api.frequency.FrequencyApi`** — static, server-thread facade for the wireless frequency system. Read-only queries (`getBoundFrequencyId(BlockEntity)`, `getFrequencyInfo(server, id)`, `getTransmitter(server, id)`, `isValidFrequency(server, id)`) return `FrequencyInfo` / `TransmitterInfo` / `FrequencySecurity` snapshots without exposing internal mutable state.
- **`FrequencyBindingHost`** + **`FrequencyBindingAccess`** — let a third-party block entity join a wireless controller as a receiver. The BE must extend AE2's `AENetworkedBlockEntity`; store one access from `FrequencyApi.createBinding(this)` in a field, return it from `getFrequencyBindingAccess()` (plus the three other host accessors: `getFrequencyBindingBlockEntity` / `saveFrequencyBindingChanges` / `markFrequencyBindingForUpdate`), and forward the lifecycle methods (`onReady` / `setRemoved` / `clearRemoved` / `serverTick` / `save` / `load` / `onMainNodeStateChanged`). The helper handles virtual-connection retry, listener subscription and the bound-devices list automatically. See `package-info.java` for a full reference implementation.
- **`FrequencyBindingMenuHost`** + **`FrequencyApi.openBindingScreen(menu)`** — let a third-party menu reuse the shared frequency selection / creation / membership UI. Implement the marker on your `AbstractContainerMenu`, draw your own button in your `Screen`, and call the helper from `onPress` — server-side permission checks, list sync, password and member management are all reused.

Anything outside `com.moakiee.ae2lt.api.*` is internal and may change between minor versions. See `package-info.java` for the full contract and the frozen-on-release list.

## Dependencies

| Mod | Required |
|-----|----------|
| Applied Energistics 2 | Required |
| Jade, Flywheel, Ponder | Optional |
| Advanced AE, ExtendedAE, Applied Flux, AE2 JEI Integration | Optional integrations |

## Issues

Found a bug or have a suggestion? Please open an issue on the project tracker with your Minecraft / NeoForge / AE2LT versions, a clear description and a log if applicable.

## License

[![Source License](https://img.shields.io/badge/Source-LGPL--3.0-blue)](LICENSE)
[![Assets License](https://img.shields.io/badge/Assets-CC%20BY--NC--SA%203.0-lightgrey)](LICENSE_ASSETS.md)

AE2 Lightning Tech uses separate licenses for source code and textures:

- Source code is licensed under [GNU LGPL 3.0](https://www.gnu.org/licenses/lgpl-3.0.html).
- Textures and other visual assets are licensed under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

## Credits

Developed by **MOAKIEE**, **CystrySU**, **gjmhmm8**, **_leng**, **TedXenon**, **MHanHanBing**.

Special thanks to the Applied Energistics 2 team — without AE2 this addon would not exist.
