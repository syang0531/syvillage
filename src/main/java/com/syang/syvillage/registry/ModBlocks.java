package com.syang.syvillage.registry;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.block.DraftingTable;
import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.block.FacingTable;
import com.syang.syvillage.block.GuardianBlockEntity;
import com.syang.syvillage.block.GuardianStatue;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks this mod adds to the world.
 *
 * <p>There were none for a long time, which was the point: everything up to here is arithmetic
 * over vanilla blocks. A workstation is different. It is the one thing that cannot be borrowed,
 * because a villager takes a job by claiming a block nothing else has claimed.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SyVillage.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SyVillage.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SyVillage.MODID);

    /**
     * The village head's table: the workstation that lets a settlement build in stone brick.
     *
     * <p>Placed by the player, never by the settlement. A village that built its own table would
     * promote itself, and then the standard would be a timer rather than something somebody did.
     */
    public static final DeferredBlock<Block> VILLAGE_HEAD_TABLE = register("village_head_table",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava(),
            FacingTable::new);

    /**
     * The architect's table: a workstation a villager claims, and a drawing board a player uses.
     *
     * <p>Stone rather than wood, because what gets drawn on it is masonry on a different scale.
     *
     * <p>It was the lord's table, and the lord did nothing a table could not: he unlocked a wall
     * the settlement built by itself, and the settlement no longer builds anything by itself.
     * The architect draws, which is a job, and this is where the drawing goes.
     */
    public static final DeferredBlock<Block> ARCHITECTS_TABLE = register("architects_table",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE),
            DraftingTable::new);

    /**
     * The guardian statue: an iron golem that comes back.
     *
     * <p>The one block here with no job attached to it, because what it does is not a job. See
     * {@link GuardianStatue} for why a tidy town needs one at all.
     */
    public static final DeferredBlock<Block> GUARDIAN_STATUE = register("guardian_statue",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    // Not a full cube, so the faces its neighbours would have culled have to be
                    // drawn. Without this the statue is a silhouette with holes in it.
                    .noOcclusion(),
            GuardianStatue::new);

    /** What remembers which drawing is pinned to which table, and where it says to build. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DraftingTableEntity>>
            DRAFTING_TABLE_ENTITY = BLOCK_ENTITIES.register("drafting_table",
                    () -> new BlockEntityType<>(DraftingTableEntity::new, ARCHITECTS_TABLE.get()));

    /** What remembers which golem belongs to which statue. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GuardianBlockEntity>>
            GUARDIAN = BLOCK_ENTITIES.register("guardian",
                    () -> new BlockEntityType<>(GuardianBlockEntity::new,
                            GUARDIAN_STATUE.get()));

    private ModBlocks() {}

    private static DeferredBlock<Block> register(String name, BlockBehaviour.Properties props) {
        return register(name, props, Block::new);
    }

    /** The same, for a block that is not a plain {@link Block}. */
    private static DeferredBlock<Block> register(String name, BlockBehaviour.Properties props,
            Function<BlockBehaviour.Properties, Block> constructor) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(SyVillage.MODID, name));
        DeferredBlock<Block> block = BLOCKS.register(name,
                () -> constructor.apply(props.setId(key)));
        item(name, block);
        return block;
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(SyVillage.MODID, name));
        return ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
