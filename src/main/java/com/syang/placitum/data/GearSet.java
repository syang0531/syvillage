package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Worn equipment only; loose items live in the settlement stock.
 *
 * <p>Deliberately holds {@link Item} plus a damage value rather than ItemStack: a stack drags
 * in components and enchantments, none of which the militia formula reads. M1 revisits this
 * if gear tiers turn out to need more than the item identity.
 */
public record GearSet(
        Optional<Item> weapon,
        Optional<Item> helmet,
        Optional<Item> chest,
        Optional<Item> legs,
        Optional<Item> boots,
        int damage) {

    private static final Codec<Item> ITEM = BuiltInRegistries.ITEM.byNameCodec();

    public static final Codec<GearSet> CODEC = RecordCodecBuilder.create(i -> i.group(
            ITEM.optionalFieldOf("weapon").forGetter(GearSet::weapon),
            ITEM.optionalFieldOf("helmet").forGetter(GearSet::helmet),
            ITEM.optionalFieldOf("chest").forGetter(GearSet::chest),
            ITEM.optionalFieldOf("legs").forGetter(GearSet::legs),
            ITEM.optionalFieldOf("boots").forGetter(GearSet::boots),
            Codec.INT.fieldOf("damage").forGetter(GearSet::damage)
    ).apply(i, GearSet::new));

    public static final GearSet EMPTY =
            new GearSet(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), 0);

    public boolean armed() {
        return weapon.isPresent();
    }
}
