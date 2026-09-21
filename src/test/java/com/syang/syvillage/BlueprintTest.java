package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Outline;
import com.syang.syvillage.build.Placement;
import com.syang.syvillage.build.Raise;
import com.syang.syvillage.build.Site;
import com.syang.syvillage.build.Template;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.Craft;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a blueprint turns into, without a game to put it in.
 *
 * <p>The occupancy check needs a level and is left to play; everything that decides which
 * blocks go where does not, and that is the point of 0.3's version of principle four.
 */
class BlueprintTest {

    private static final Identifier TOWER =
            Identifier.fromNamespaceAndPath("syvillage", "tower");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Placement at(BlockPos origin, Rotation rotation, Craft craft) {
        return new Placement(TOWER, origin, rotation, craft, origin.getY());
    }

    @Test
    @DisplayName("expansion cannot read the world: no level anywhere in the signature")
    void expansionHasNoLevel() throws Exception {
        Method expand = Raise.class.getMethod("expand", Placement.class);
        for (Class<?> parameter : expand.getParameterTypes()) {
            assertFalse(Level.class.isAssignableFrom(parameter),
                    "expand takes " + parameter + ", so it could read the world");
        }
        // And the frozen ground the old recipe carried is gone with it.
        for (var component : Placement.class.getRecordComponents()) {
            assertFalse(component.getName().contains("profile"),
                    "a placement still carries a ground profile");
        }
    }

    @Test
    @DisplayName("the same placement gives the same blocks, every time")
    void expansionIsPure() {
        Placement placement = at(new BlockPos(100, 64, -40), Rotation.CLOCKWISE_90, Craft.PLAINS);
        assertEquals(Raise.expand(placement), Raise.expand(placement));
    }

    @Test
    @DisplayName("no position is written twice, which is what makes sorting safe")
    void noPositionTwice() {
        for (Rotation rotation : Rotation.values()) {
            List<BuildOp> ops = Raise.expand(at(new BlockPos(0, 70, 0), rotation, Craft.PLAINS));
            Set<BlockPos> seen = new HashSet<>();
            for (BuildOp op : ops) {
                assertTrue(seen.add(op.pos()), "wrote " + op.pos() + " twice, turned " + rotation);
            }
            assertEquals(ops.size(), seen.size());
        }
    }

    @Test
    @DisplayName("blocks arrive lowest layer first, so the flourish goes upwards")
    void laidFromTheBottom() {
        List<BuildOp> ops = Raise.expand(at(new BlockPos(8, 64, 8), Rotation.NONE, Craft.PLAINS));
        int previous = Integer.MIN_VALUE;
        for (BuildOp op : ops) {
            assertTrue(op.pos().getY() >= previous, "out of order at " + op.pos());
            previous = op.pos().getY();
        }
    }

    @Test
    @DisplayName("layer 0 lands on the floor the table names, and nothing is below it")
    void layerZeroSitsOnTheFloor() {
        int floor = 71;
        List<BuildOp> ops = Raise.expand(at(new BlockPos(0, floor, 0), Rotation.NONE,
                Craft.PLAINS));
        int lowest = Integer.MAX_VALUE;
        for (BuildOp op : ops) {
            lowest = Math.min(lowest, op.pos().getY());
        }
        assertEquals(floor, lowest, "the structure must start at its floor, not below it");
    }

    @Test
    @DisplayName("every occupied column has a span, and the span is what the check walks")
    void everyColumnHasASpan() {
        Template tower = Template.of(TOWER);
        for (int[] column : tower.columns()) {
            int bottom = tower.bottomOf(column[0], column[1]);
            int top = tower.topOf(column[0], column[1]);
            assertTrue(bottom >= 0, "a column with no lowest block is in the footprint");
            assertTrue(top >= bottom, "a column whose top is below its bottom");
        }
        // A column the template puts nothing in is nobody's business - the roadway under a
        // gate arch is the case this exists for.
        Template gate = Template.of("gatehouse");
        assertTrue(gate.columns().size() < gate.sizeX() * gate.sizeZ(),
                "a footprint that covers its whole box is a box, not a footprint");
    }

    @Test
    @DisplayName("a quarter turn swaps the box, and the blocks follow it")
    void turningSwapsTheBox() {
        Template gate = Template.of("gatehouse");
        assertEquals(gate.sizeX(), gate.turnedWidth(Rotation.NONE));
        assertEquals(gate.sizeZ(), gate.turnedWidth(Rotation.CLOCKWISE_90));

        BlockPos origin = new BlockPos(0, 64, 0);
        List<BuildOp> turned = Raise.expand(new Placement(
                Identifier.fromNamespaceAndPath("syvillage", "gatehouse"),
                origin, Rotation.CLOCKWISE_90, Craft.PLAINS, 64));
        int widest = 0;
        for (BuildOp op : turned) {
            widest = Math.max(widest, op.pos().getX() - origin.getX());
        }
        assertEquals(gate.sizeZ() - 1, widest, "the turned box did not stay in its corner");
    }

    @Test
    @DisplayName("an outline carries the shape and nothing else, and packs it whole")
    void outlineCarriesTheShape() {
        Placement placement = at(new BlockPos(-30, 68, 12), Rotation.CLOCKWISE_90, Craft.PLAINS);
        Template tower = Template.of(TOWER);
        Outline outline = Outline.of(placement, new Site.Survey(placement,
                List.of(new BlockPos(-30, 69, 12)), 1, 0));

        assertEquals(tower.columns().size(), outline.columns().length, "one entry per column");
        assertEquals(outline.columns().length, outline.spans().length, "a span for each");
        assertEquals(3, outline.blocked().length, "three ints for one blocked position");
        assertFalse(outline.buildable());
        assertEquals(tower.turnedWidth(Rotation.CLOCKWISE_90), outline.width());

        // Packed into a byte each way, which is what bounds a template to 256 on a side.
        for (int packed : outline.columns()) {
            assertTrue((packed >> 8) < outline.width() && (packed >> 8) >= 0, "x out of the box");
            assertTrue((packed & 0xFF) < outline.depth(), "z out of the box");
        }
        for (int span : outline.spans()) {
            assertTrue((span >> 8) <= (span & 0xFF), "a column whose bottom is above its top");
        }
        // Arrays, so identity equality would make every refresh look like a change and the
        // table would send an update packet every second for ever.
        assertEquals(outline, Outline.of(placement, new Site.Survey(placement,
                List.of(new BlockPos(-30, 69, 12)), 1, 0)));
    }

    @Test
    @DisplayName("the palette is the biome it is put down in, not the one it was drawn in")
    void materialsFollowThePlacement() {
        Placement desert = at(new BlockPos(0, 64, 0), Rotation.NONE, Craft.DESERT);
        boolean sandstone = false;
        for (BuildOp op : Raise.expand(desert)) {
            assertFalse(op.state().is(Blocks.STONE_BRICKS), "stone brick survived the desert");
            sandstone |= op.state().is(Blocks.CUT_SANDSTONE);
        }
        assertTrue(sandstone, "a desert tower with no sandstone in it");
    }
}
