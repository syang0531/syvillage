package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Placement;
import com.syang.syvillage.build.Raise;
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
    @DisplayName("layer 0 lands on the floor the player clicked, and nothing is below it")
    void layerZeroSitsOnTheClickedFace() {
        int floor = 71;
        List<BuildOp> ops = Raise.expand(at(new BlockPos(0, floor, 0), Rotation.NONE,
                Craft.PLAINS));
        int lowest = Integer.MAX_VALUE;
        for (BuildOp op : ops) {
            lowest = Math.min(lowest, op.pos().getY());
        }
        assertEquals(floor, lowest, "the structure must start at the clicked face, not below it");
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
    @DisplayName("the box grows away from the player, so it cannot close over them")
    void theBoxGrowsAwayFromThePlayer() {
        BlockPos at = new BlockPos(0, 64, 0);
        int w = 17;
        int d = 9;
        // Yaw 0 is south (+z), 90 is west (-x), 180 north (-z), 270 east (+x).
        assertEquals(new BlockPos(0, 64, 0), Placement.corner(at, 0f, w, d), "facing south");
        assertEquals(new BlockPos(-16, 64, 0), Placement.corner(at, 90f, w, d), "facing west");
        assertEquals(new BlockPos(-16, 64, -8), Placement.corner(at, 180f, w, d), "facing north");
        assertEquals(new BlockPos(0, 64, -8), Placement.corner(at, 270f, w, d), "facing east");

        // Whatever the quarter, the clicked block is a corner of the box and never inside it.
        for (float yaw = 0f; yaw < 360f; yaw += 15f) {
            BlockPos corner = Placement.corner(at, yaw, w, d);
            boolean onX = corner.getX() == at.getX() || corner.getX() + w - 1 == at.getX();
            boolean onZ = corner.getZ() == at.getZ() || corner.getZ() + d - 1 == at.getZ();
            assertTrue(onX && onZ, "yaw " + yaw + " put the click off the corner");
        }
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
