package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Template;
import com.syang.syvillage.data.Craft;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The templates as shipped: that they load, that they are the size the plan believes, and
 * that turning and re-materialing them does what a structure block would.
 */
class TemplateTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("the three templates load at the size the code expects")
    void theyLoadAtTheSizeThePlanBelieves() {
        Template gate = Template.of("gatehouse");
        assertEquals(25, gate.sizeX(), "a gatehouse is twenty-five wide");
        assertEquals(9, gate.sizeZ(), "and nine deep");
        assertEquals(10, gate.sizeY());

        Template tower = Template.of("tower");
        assertEquals(17, tower.sizeX());
        assertEquals(17, tower.sizeZ());

        // The sample was saved in a box one layer taller than the wall, so the height is read
        // from the highest block, not the box.
        Template rampart = Template.of("rampart");
        assertEquals(5, rampart.sizeZ(), "five wide: parapet, three of walkway, parapet");
        int highest = -1;
        for (int[] column : rampart.columns()) {
            highest = Math.max(highest, rampart.topOf(column[0], column[1]));
        }
        assertEquals(5, highest + 1, "body, parapet, merlon");
    }

    @Test
    @DisplayName("a footprint is the occupied columns, not the box")
    void theFootprintIsWhatIsBuiltOn() {
        Template tower = Template.of("tower");
        int box = tower.sizeX() * tower.sizeZ();
        assertTrue(tower.columns().size() < box,
                "the tower's box has open ground in it that is nobody's to demand be walkable");
        for (int[] column : tower.columns()) {
            assertTrue(tower.topOf(column[0], column[1]) >= 0);
        }
    }

    @Test
    @DisplayName("materials change by family and keep their properties")
    void remappingKeepsTheFamily() {
        BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);

        BlockState desert = Template.remap(stair, Craft.DESERT);
        assertEquals(Blocks.SANDSTONE_STAIRS, desert.getBlock());
        assertEquals(Direction.WEST, desert.getValue(StairBlock.FACING));
        assertEquals(Half.TOP, desert.getValue(StairBlock.HALF));

        assertEquals(Blocks.CUT_SANDSTONE,
                Template.remap(Blocks.STONE_BRICKS.defaultBlockState(), Craft.DESERT).getBlock());
        assertEquals(Blocks.ACACIA_FENCE_GATE,
                Template.remap(Blocks.OAK_FENCE_GATE.defaultBlockState(), Craft.SAVANNA).getBlock());
        assertEquals(Blocks.LANTERN,
                Template.remap(Blocks.LANTERN.defaultBlockState(), Craft.DESERT).getBlock(),
                "a lantern is a lantern everywhere");
        assertEquals(Blocks.STONE_BRICKS,
                Template.remap(Blocks.STONE_BRICKS.defaultBlockState(), Craft.PLAINS).getBlock());
    }

}
