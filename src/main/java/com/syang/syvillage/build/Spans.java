package com.syang.syvillage.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * A column of ground a build has an interest in, and how much has to come off the top of it.
 *
 * <p>Four numbers: where the column is, where its ground is, and the highest block that must be
 * cut out of it. Streets and lamp posts are a scattering of positions rather than a shape
 * measured from a corner, so they need the positions frozen anyway; a house and a field need the
 * clearance and know their own shape. One record covers both, and a recipe carries a list of
 * them in its spare int list.
 *
 * <p>Frozen at plan time for the same reason the ground profile is: a recipe that has to look at
 * the world again to be expanded is not a recipe.
 */
public record Spans(int x, int z, int base, int top) {

    /** Nothing grows here, so nothing is cut. */
    public boolean clear() {
        return base == Ground.SKIP || top <= base;
    }

    public BlockPos at(int y) {
        return new BlockPos(x, y, z);
    }

    public static List<Integer> encode(List<Spans> spans) {
        List<Integer> out = new ArrayList<>(spans.size() * 4);
        for (Spans span : spans) {
            out.add(span.x());
            out.add(span.z());
            out.add(span.base());
            out.add(span.top());
        }
        return List.copyOf(out);
    }

    public static List<Spans> decode(List<Integer> ints) {
        if (ints.size() % 4 != 0) {
            return List.of();
        }
        List<Spans> out = new ArrayList<>(ints.size() / 4);
        for (int i = 0; i < ints.size(); i += 4) {
            out.add(new Spans(ints.get(i), ints.get(i + 1), ints.get(i + 2), ints.get(i + 3)));
        }
        return List.copyOf(out);
    }
}
