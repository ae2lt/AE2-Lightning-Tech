---
navigation:
  title: Atmospheric Ionizer
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# Atmospheric Ionizer

<Row>
  <BlockImage id="ae2lt:atmospheric_ionizer" scale="4" />
</Row>

The **Atmospheric Ionizer** is a weather control device. It consumes **Weather Condensate** and AE energy to force the world's weather to a specific state, letting you feed the Lightning Collector with reliable natural lightning strikes.

## Weather Condensate

There are three types of Weather Condensate, one per weather state:

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| Condensate | Target Weather | AE Cost | Duration |
|------------|----------------|---------|----------|
| Clear Condensate | Clear | 500,000 AE | 12,000 ~ 180,000 ticks |
| Rain Condensate | Rain | 1,000,000 AE | 12,000 ~ 24,000 ticks |
| Thunderstorm Condensate | Thunderstorm | 8,000,000 AE | 3,600 ~ 15,600 ticks |

## Operating Flow

1. Connect the Atmospheric Ionizer to the ME network
2. Put the required Weather Condensate into the input slot
3. The machine continuously pulls AE from the ME network to ionize
4. Once ionization is complete, the world weather is forced to the target state
5. The condensate is consumed

## Notes

* The Atmospheric Ionizer consumes **AE energy** from the ME network, not FE
* Thunderstorm Condensate has the highest single-use cost (8,000,000 AE) — make sure the network has enough energy on hand
* In dimensions that do not support weather, the machine cannot work
* When the target weather is already the current weather, the machine does not consume condensate
