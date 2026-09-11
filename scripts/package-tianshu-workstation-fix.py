#!/usr/bin/env python3
"""Package the workstation Ctrl fix on the exact deployed build, preserving all other code/resources."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile

BASE_SHA256 = "1a1bd97eb93d54fb952b0c0e27c19412563b43eaaf38c032f93f5df70a4c2f31"
OLD_VERSION = "2.1.0-beta.5-tianshu.2"
NEW_VERSION = OLD_VERSION + "-workstationfix.4"
CLASSES = (
    "com/moakiee/ae2lt/client/LightningKeyClientInit.class",
    "com/moakiee/ae2lt/integration/jei/JEIPlugin.class",
    "com/moakiee/ae2lt/integration/jei/TianshuCraftingTransferHandler.class",
    "com/moakiee/ae2lt/integration/jei/TianshuCraftingTransferHandler$CraftingFeedback.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$1.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$2.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$3.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$4.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$5.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$AnvilTransferRequest.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$WorkIngredient.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$WorkRecipeAvailability.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu.class",
    "com/moakiee/ae2lt/menu/TianshuWorkstationSession.class",
    "com/moakiee/ae2lt/menu/TianshuWorkstationSession$Pending.class",
    "com/moakiee/ae2lt/menu/TianshuCraftingTermMenu$WorkState.class",
    "com/moakiee/ae2lt/client/TianshuCraftingTermScreen.class",
    "com/moakiee/ae2lt/client/TianshuCraftingTermScreen$1.class",
    "com/moakiee/ae2lt/client/TianshuCraftingTermScreen$2.class",
    "com/moakiee/ae2lt/client/TianshuCraftingTermScreen$StoneRecipeButton.class",
    "com/moakiee/ae2lt/client/TianshuCraftingTermScreen$WorkPageTabButton.class",
    "com/moakiee/ae2lt/integration/emi/TianshuCraftingRecipeHandler$AnvilInputs.class",
    "com/moakiee/ae2lt/integration/emi/TianshuCraftingRecipeHandler.class",
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    assert sha(args.baseline.read_bytes()) == BASE_SHA256, "Not the deployed Tianshu .2 artifact"
    assert args.output.resolve() != args.baseline.resolve(), "Never overwrite the baseline"
    replacements = {name: (root / "build/classes/java/main" / name).read_bytes() for name in CLASSES}
    with zipfile.ZipFile(args.baseline) as original:
        meta = "META-INF/neoforge.mods.toml"
        text = original.read(meta).decode()
        expected = f'version = "{OLD_VERSION}"'
        assert text.count(expected) == 1
        replacements[meta] = text.replace(expected, f'version = "{NEW_VERSION}"').encode()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as result:
            for entry in original.infolist():
                result.writestr(copy.copy(entry), replacements.get(entry.filename, original.read(entry.filename)))
            for name in replacements.keys() - set(original.namelist()):
                result.writestr(name, replacements[name])
        with zipfile.ZipFile(args.output) as result:
            assert result.testzip() is None
            assert set(result.namelist()) == set(original.namelist()) | set(CLASSES)
            unchanged = 0
            for name in original.namelist():
                if name not in replacements:
                    assert result.read(name) == original.read(name), name
                    unchanged += 1
            for name, data in replacements.items():
                assert result.read(name) == data, name
    report = {
        "artifact": args.output.name,
        "version": NEW_VERSION,
        "sha256": sha(args.output.read_bytes()),
        "baseline_sha256": BASE_SHA256,
        "changed_entries": sorted(replacements),
        "unchanged_entries_verified": unchanged,
        "scope": "Tianshu manual workstation sounds and input continuity; includes prior JEI/EMI Ctrl fixes; install on both client and server",
    }
    args.output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
