# AE2 Lightning Tech — Forge 1.20.1 Port

[中文文档](README_zh_CN.md)

An [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that introduces a lightning energy system, advanced machines, and overloaded network components.

> Requires AE2 and Thunderbolt Core · Built for Minecraft 1.20.1 / Forge 47.4

This branch is the maintained Forge 1.20.1 port. The primary project targets newer Minecraft and NeoForge versions; behavior changes are ported back where the 1.20.1 APIs support them.

## Features

- Two lightning-energy tiers: High Voltage and Extreme High Voltage.
- Lightning Collector, Atmospheric Ionizer and Tesla Coil energy production.
- Lightning Assembly Chamber, Lightning Simulation Room, Overload Processing Factory and Crystal Catalyzer processing chains.
- Overload crystal growth, decay and bulk-processing progression.
- High-throughput overloaded ME controllers, interfaces, pattern providers and cables.
- Wireless pattern routing, Tianshu supercomputer automation and configurable batch dispatch.
- Celestweave modular armor and the electromagnetic railgun system.

## Dependencies

| Mod | Requirement |
|-----|-------------|
| Minecraft 1.20.1 | Required |
| Forge 47.4.x | Required |
| Applied Energistics 2 15.4.10+ | Required |
| Thunderbolt Core 1.0.7 | Required |
| JEI or EMI, Jade | Optional integration |
| AdvancedAE, ExtendedAE, Applied Flux, AE2WTLib | Optional integration |
| Mekanism, Curios, Flux Networks, Polymorph | Optional integration |

Optional integrations are isolated and are not required for normal startup.

## Build

Use Java 17 and run:

```powershell
.\gradlew.bat test build
```

The distributable artifact is `build/libs/ae2lt-2.0.0-port-1.20.1forge.jar`.

## Public API

`com.moakiee.ae2lt.api.*` is the stable surface for addon authors. It includes lightning-energy capabilities and events, frozen block-entity and recipe IDs, and the server-authoritative wireless-frequency API. Forge event subscribers should use `MinecraftForge.EVENT_BUS`.

Everything outside `com.moakiee.ae2lt.api.*` is internal and may change between minor versions.

## License

- Source code: [GNU LGPL 3.0](LICENSE)
- Textures and visual assets: [CC BY-NC-SA 3.0](LICENSE_ASSETS.md)
- Adapted or bundled code: [Third-party notices](THIRD_PARTY_NOTICES.md)

Developed by **MOAKIEE**, **CystrySU**, **gjmhmm8**, **_leng**, **TedXenon**, and **MHanHanBing**.
