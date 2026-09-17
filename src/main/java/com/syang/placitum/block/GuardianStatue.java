package com.syang.placitum.block;

import com.mojang.serialization.MapCodec;
import com.syang.placitum.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A statue that keeps an iron golem.
 *
 * <p>The only thing this mod adds that wants no villager. A workstation is a job and a job needs
 * somebody to take it; this is a thing the player pays iron for and puts down, which is right for
 * what it does - a garrison is not a trade.
 *
 * <p>It exists because of something in vanilla's own source. A golem appears where five villagers
 * inside a ten-block box all want one, and a town laid out on a twenty-block period spreads its
 * villagers much further apart than the huddle vanilla generates: <b>the tidier this mod makes a
 * village, the fewer golems it gets.</b> The statue hands back what the town plan takes away.
 *
 * <p>What it sells is not iron - it costs about what a golem costs to build by hand - but
 * permanence. A golem you build wanders off, or dies once. This one comes back.
 */
public class GuardianStatue extends BaseEntityBlock {

    public static final MapCodec<GuardianStatue> CODEC = simpleCodec(GuardianStatue::new);

    public GuardianStatue(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * Drawn from its model.
     *
     * <p>{@link BaseEntityBlock} defaults this to {@code INVISIBLE}, which is what a chest or a
     * bell wants - something else draws those. Nothing else draws this one.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardianBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        // Server only. A client-side ticker would have every player's own copy of the game
        // deciding whether a golem was missing, and only the server may answer that.
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlocks.GUARDIAN.get(),
                        GuardianBlockEntity::serverTick);
    }
}
