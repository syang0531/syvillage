package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Template;
import com.syang.syvillage.build.TownPlan;
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
    @DisplayName("the three templates load, and are the size the plan believes")
    void theyLoadAtTheSizeThePlanBelieves() {
        Template gate = Template.of("gatehouse");
        assertEquals(TownPlan.GATE_WIDE, gate.sizeX(), "the plan's idea of a gatehouse's width");
        assertEquals(TownPlan.GATE_DEEP, gate.sizeZ(), "and its depth");
        assertEquals(10, gate.sizeY());

        Template tower = Template.of("tower");
        assertEquals(TownPlan.TOWER_FRAME, tower.sizeX());
        assertEquals(TownPlan.TOWER_FRAME, tower.sizeZ());

        // The sample was saved in a box one layer taller than the wall, so the height is read
        // from the highest block, not the box.
        Template rampart = Template.of("rampart");
        assertEquals(TownPlan.WALL, rampart.sizeZ(), "five wide: parapet, three of walkway, parapet");
        int highest = -1;
        for (int[] column : rampart.columns()) {
            highest = Math.max(highest, rampart.topOf(column[0], column[1]));
        }
        assertEquals(TownPlan.WALL_HEIGHT, highest + 1, "body, parapet, merlon");
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
    @DisplayName("the probe column is solid from the ground and as tall as any such")
    void theProbeIsSolidMasonry() {
        for (String name : new String[] {"gatehouse", "tower"}) {
            Template template = Template.of(name);
            int[] probe = template.probe();
            assertTrue(template.solidToTop(probe[0], probe[1]), name);
            for (int[] column : template.columns()) {
                if (template.solidToTop(column[0], column[1])) {
                    assertTrue(template.topOf(column[0], column[1]) <= probe[2], name);
                }
            }
        }
        // And the arch is not solid: that is the column the old gatehouse levelled itself on.
        Template gate = Template.of("gatehouse");
        assertFalse(gate.solidToTop(gate.sizeX() / 2, gate.sizeZ() / 2), "the way through");
    }

    @Test
    @DisplayName("turning follows the game's convention: clockwise takes north to east")
    void turningMatchesBlockStates() {
        assertArrayEquals(new int[] {1, 0}, Template.turn(0, -1, Rotation.CLOCKWISE_90));
        assertArrayEquals(new int[] {0, 1}, Template.turn(0, -1, Rotation.CLOCKWISE_180));
        assertArrayEquals(new int[] {-1, 0}, Template.turn(0, -1, Rotation.COUNTERCLOCKWISE_90));

        BlockState north = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH);
        assertEquals(Direction.EAST, north.rotate(Rotation.CLOCKWISE_90).getValue(StairBlock.FACING),
                "so a stair turned with the offset it sits at still faces the same way relative"
                        + " to the structure");
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

    @Test
    @DisplayName("the ways in are on the lowest layer, and both templates have them")
    void theWaysInAreOnTheGround() {
        for (String name : new String[] {"gatehouse", "tower"}) {
            Template template = Template.of(name);
            assertFalse(template.entrances().isEmpty(), name + " has no way in");
            for (int[] column : template.entrances()) {
                assertTrue(template.hasBase(column[0], column[1]),
                        name + ": a way in with nothing on the lowest layer at "
                                + column[0] + "," + column[1]);
            }
        }
        // The gatehouse's ways in include its road: the fence gates across the arch.
        Template gate = Template.of("gatehouse");
        boolean onTheRoad = false;
        for (int[] column : gate.entrances()) {
            onTheRoad |= column[0] == gate.sizeX() / 2;
        }
        assertTrue(onTheRoad, "the arch's fence gates are ways in, so the floor is the road's");
    }
}
