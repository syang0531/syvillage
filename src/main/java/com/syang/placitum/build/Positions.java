package com.syang.placitum.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A list of columns, carried in a recipe's spare int list.
 *
 * <p>Streets and lamp posts are a scattering of positions rather than a shape measured from one
 * corner, so there is nothing for {@code anchor} and {@code extent} to describe. The positions
 * themselves have to be frozen into the recipe, for the same reason the ground profile is: a
 * recipe that has to be looked up again is not a recipe.
 */
public final class Positions {

    private Positions() {}

    public static List<Integer> encode(List<BlockPos> columns) {
        List<Integer> out = new ArrayList<>(columns.size() * 2);
        for (BlockPos column : columns) {
            out.add(column.getX());
            out.add(column.getZ());
        }
        return List.copyOf(out);
    }

    public static List<BlockPos> decode(List<Integer> coords) {
        if (coords.size() % 2 != 0) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>(coords.size() / 2);
        for (int i = 0; i < coords.size(); i += 2) {
            out.add(new BlockPos(coords.get(i), 0, coords.get(i + 1)));
        }
        return List.copyOf(out);
    }
}
