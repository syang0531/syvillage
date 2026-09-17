package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
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
            DeferredRegister.createBlocks(Placitum.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Placitum.MODID);

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
                    .ignitedByLava());

    private ModBlocks() {}

    private static DeferredBlock<Block> register(String name, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Placitum.MODID, name));
        DeferredBlock<Block> block = BLOCKS.register(name,
                () -> new Block(props.setId(key)));
        item(name, block);
        return block;
    }

    private static DeferredItem<BlockItem> item(String name, DeferredBlock<Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Placitum.MODID, name));
        return ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
