package com.moakiee.ae2lt.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moakiee.ae2lt.block.MatrixMultiblockComponentBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerPortBlock;
import com.moakiee.ae2lt.block.TianshuSupercomputerStructureBlock;
import com.moakiee.ae2lt.block.WrenchDisassemblableBlock;
import org.junit.jupiter.api.Test;

class MultiblockWrenchHandlerTest {
    @Test
    void allMatrixAndTianshuBlockFamiliesSupportWrenchDisassembly() {
        assertTrue(WrenchDisassemblableBlock.class.isAssignableFrom(
                MatrixMultiblockComponentBlock.class));
        assertTrue(WrenchDisassemblableBlock.class.isAssignableFrom(
                TianshuSupercomputerStructureBlock.class));
        assertTrue(WrenchDisassemblableBlock.class.isAssignableFrom(
                TianshuSupercomputerControllerBlock.class));
        assertTrue(WrenchDisassemblableBlock.class.isAssignableFrom(
                TianshuSupercomputerPortBlock.class));
    }
}
