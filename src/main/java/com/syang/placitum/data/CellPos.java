package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * A plot-grid cell, relative to the settlement centre.
 *
 * <p>The codec is a string ("gx,gz") so CellPos can be a map key in NBT. It also gives the
 * grid a stable sort order, which the simulation relies on.
 */
public record CellPos(int gx, int gz) implements Comparable<CellPos> {

    public static final Codec<CellPos> CODEC =
            Codec.STRING.comapFlatMap(CellPos::parse, CellPos::toKey);

    public static DataResult<CellPos> parse(String key) {
        int comma = key.indexOf(',');
        if (comma <= 0) {
            return DataResult.error(() -> "Not a cell position: " + key);
        }
        try {
            return DataResult.success(new CellPos(
                    Integer.parseInt(key.substring(0, comma)),
                    Integer.parseInt(key.substring(comma + 1))));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a cell position: " + key);
        }
    }

    public String toKey() {
        return gx + "," + gz;
    }

    @Override
    public int compareTo(CellPos other) {
        int byZ = Integer.compare(gz, other.gz);
        return byZ != 0 ? byZ : Integer.compare(gx, other.gx);
    }
}
