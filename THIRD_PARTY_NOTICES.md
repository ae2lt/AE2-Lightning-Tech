# Third-Party Notices

## ExtendedAE — Overloaded Machine GUI Backgrounds

The bundled overloaded interface and overloaded pattern provider GUI background textures are
derived from ExtendedAE's Minecraft 1.20.1 Forge resources. They are stored in the `ae2lt` resource
namespace so installing ExtendedAE cannot replace them through a shared `ae2` resource path.

- Upstream repository: https://github.com/GlodBlock/ExtendedAE
- Upstream release reviewed: `1.20-1.4.18-forge`
- Upstream revision reviewed: `1694d22005a65bf73faed2aca4d5b33881a5386d`
- Upstream author attribution: GlodBlock
- Upstream license: GNU Lesser General Public License version 3

## ExtendedAE — ME Void Cell

The ME Void Cell implementation, mode-selection GUI, recipe, localization, and GuideME entry
in this project are adapted from ExtendedAE's Minecraft 1.21.1 implementation. The port replaces
Minecraft 1.21 data components with namespaced, versioned Minecraft 1.20.1 ItemStack NBT and uses
the Forge 1.20.1 / AE2 15 menu and storage APIs. No ExtendedAE bitmap texture is redistributed;
the current visuals use Applied Energistics 2 resources as temporary placeholders.

- Upstream repository: https://github.com/GlodBlock/ExtendedAE
- Upstream release reviewed and adapted: `1.21-2.2.33-neoforge`
- Upstream revision: `90005ee29839fb9fa83bbe6544919c722f8b0dc6`
- Void-cell fix revision incorporated: `1623436f45e0696d4b8c695c7d9df1ebb815158e`
- Upstream author attribution: GlodBlock
- Upstream license: GNU Lesser General Public License version 3

## ExtendedAE Plus [ClientPlus]

The JEI/EMI recipe-transfer hooks and recipe keyword priority model in this project include
adapted portions of ExtendedAE Plus [ClientPlus]. Automatic encoding is intentionally not included.

- Upstream repository: https://github.com/gjmhmm8/ExtendedAE-Plus_ClientPlus
- Revision reviewed and adapted: `07f8373c590c0c6d845f794e7c25090e5ef5703e`
- Upstream author attribution: XianYuFish001 / gjmhmm8
- Upstream license: GNU Lesser General Public License version 3

The adapted implementation has been scoped to the Tianshu pattern terminal and retains
AE2 Lightning Tech's server-authoritative upload acknowledgement and target validation.

## MixinExtras

AE2 Lightning Tech bundles MixinExtras for its mixin injection extensions.

- Upstream repository: https://github.com/LlamaLad7/MixinExtras
- Upstream license: MIT

The complete AE2 Lightning Tech GNU LGPL 3.0 license text, asset license notice, and this
third-party notice are distributed in `META-INF` inside the mod JAR and in the repository root.
