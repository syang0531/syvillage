package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.Ground;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ground that only looks like ground. */
class GroundTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState of(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    @Test
    @DisplayName("ice is water, lava is a fluid, powder snow is a hole - none of them is ground")
    void whatIsNotGround() {
        BlockState air = of(Blocks.AIR);
        assertTrue(Ground.unfit(of(Blocks.ICE), air), "a lantern melts it and the street is in the lake");
        assertTrue(Ground.unfit(of(Blocks.FROSTED_ICE), air));
        assertTrue(Ground.unfit(of(Blocks.LAVA), air));
        assertTrue(Ground.unfit(of(Blocks.STONE), of(Blocks.LAVA)));
        assertTrue(Ground.unfit(of(Blocks.POWDER_SNOW), air));
        assertTrue(Ground.unfit(of(Blocks.GRASS_BLOCK), of(Blocks.POWDER_SNOW)),
                "a hole with a lid on it");
        assertTrue(Ground.unfit(of(Blocks.SAND), of(Blocks.WATER)), "what underwater already said");
    }

    @Test
    @DisplayName("permanent ice and snow-covered ground are ground")
    void whatIsGround() {
        BlockState air = of(Blocks.AIR);
        assertFalse(Ground.unfit(of(Blocks.PACKED_ICE), air), "packed ice does not melt");
        assertFalse(Ground.unfit(of(Blocks.BLUE_ICE), air));
        assertFalse(Ground.unfit(of(Blocks.GRASS_BLOCK), of(Blocks.SNOW)),
                "a snow layer on a path is what a snowy village looks like");
        assertFalse(Ground.unfit(of(Blocks.SAND), air));
    }
}
