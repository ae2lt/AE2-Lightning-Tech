package com.moakiee.ae2lt.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class StructureTemplateCompatibilityTest {
    private static final Path DATA_ROOT = Path.of("src/main/resources/data/ae2lt");
    private static final Path TEMPLATES = DATA_ROOT.resolve("structures");

    @Test
    void templatesUseThe1201ResourceDirectory() {
        assertFalse(Files.exists(DATA_ROOT.resolve("structure")));
        assertTrue(Files.isRegularFile(TEMPLATES.resolve("alien_starship/alien_starship.nbt")));
        assertTrue(Files.isRegularFile(TEMPLATES.resolve("firmament_starship/firmament_starship.nbt")));
    }

    @Test
    void templatesUse1201DataAndExpectedDimensions() throws IOException {
        CompoundTag alien = readTemplate("alien_starship/alien_starship.nbt");
        CompoundTag firmament = readTemplate("firmament_starship/firmament_starship.nbt");

        assertEquals(3465, alien.getInt("DataVersion"));
        assertEquals(3465, firmament.getInt("DataVersion"));
        assertSize(alien, 29, 12, 29);
        assertSize(firmament, 80, 20, 80);
    }

    @Test
    void alienTemplateDoesNotReferencePost1201TuffBlocks() throws IOException {
        Set<String> unsupported = Set.of(
                "minecraft:polished_tuff",
                "minecraft:polished_tuff_slab",
                "minecraft:polished_tuff_wall",
                "minecraft:chiseled_tuff",
                "minecraft:chiseled_tuff_bricks");
        ListTag palette = readTemplate("alien_starship/alien_starship.nbt")
                .getList("palette", Tag.TAG_COMPOUND);

        for (Tag entry : palette) {
            String blockId = ((CompoundTag) entry).getString("Name");
            assertFalse(unsupported.contains(blockId), () -> "Unsupported 1.20.1 block in template: " + blockId);
        }
    }

    private static CompoundTag readTemplate(String relativePath) throws IOException {
        return NbtIo.readCompressed(TEMPLATES.resolve(relativePath).toFile());
    }

    private static void assertSize(CompoundTag template, int x, int y, int z) {
        ListTag size = template.getList("size", Tag.TAG_INT);
        assertEquals(3, size.size());
        assertEquals(x, size.getInt(0));
        assertEquals(y, size.getInt(1));
        assertEquals(z, size.getInt(2));
    }
}
