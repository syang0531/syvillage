package com.syang.syvillage.build;

import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a drawing looks like on the ground, in the only terms a client needs.
 *
 * <p>The table works this out; the client draws it. Nothing else crosses - no template, no
 * palette, no verdict about what a blueprint is - so the drawing code never has to know what it
 * is drawing. It is four int arrays and a flag.
 *
 * <p>It was a custom packet first, sent to one player and kept alive by a timer. It rides the
 * block entity's own sync now, which is how it stopped needing a timer at all: the table is in
 * the world, so everybody near it sees the same outline for as long as it stands.
 *
 * @param columns occupied columns, {@code (dx << 8) | dz} from the corner
 * @param spans   the same columns' vertical extent, {@code (bottom << 8) | top}, for the massing
 * @param blocked positions something already stands in, three ints each
 */
public record Outline(BlockPos corner, int width, int height, int depth,
        int[] columns, int[] spans, int[] blocked, boolean buildable) {

    private static final Outline NONE = new Outline(BlockPos.ZERO, 0, 0, 0,
            new int[0], new int[0], new int[0], false);

    public static Outline none() {
        return NONE;
    }

    public boolean empty() {
        return columns.length == 0;
    }

    /** Everything about a placement that can be drawn, and nothing that cannot. */
    public static Outline of(Placement placement, Site.Survey survey) {
        Template template = placement.template();
        Rotation rotation = placement.rotation();
        List<int[]> occupied = template.columns();
        int[] columns = new int[occupied.size()];
        int[] spans = new int[occupied.size()];
        for (int i = 0; i < occupied.size(); i++) {
            int[] column = occupied.get(i);
            int[] turned = template.turnInBox(column[0], column[1], rotation);
            columns[i] = (turned[0] << 8) | turned[1];
            spans[i] = (template.bottomOf(column[0], column[1]) << 8)
                    | Math.min(template.topOf(column[0], column[1]), 255);
        }
        int[] blocked = new int[survey.blocked().size() * 3];
        for (int i = 0; i < survey.blocked().size(); i++) {
            BlockPos pos = survey.blocked().get(i);
            blocked[i * 3] = pos.getX();
            blocked[i * 3 + 1] = pos.getY();
            blocked[i * 3 + 2] = pos.getZ();
        }
        return new Outline(placement.origin(), template.turnedWidth(rotation),
                template.sizeY(), template.turnedDepth(rotation),
                columns, spans, blocked, survey.buildable());
    }

    // ---- riding the block entity's sync
    //
    // Written into the update tag only, never into the saved data: this is read off the world
    // and would be a lie the moment it was written to disk.

    private static final String BOX = "outline_box";
    private static final String COLUMNS = "outline_columns";
    private static final String SPANS = "outline_spans";
    private static final String BLOCKED = "outline_blocked";
    private static final String BUILDABLE = "outline_ok";

    public void store(ValueOutput output) {
        if (empty()) {
            return;
        }
        output.putIntArray(BOX, new int[] {corner.getX(), corner.getY(), corner.getZ(),
                width, height, depth});
        output.putIntArray(COLUMNS, columns);
        output.putIntArray(SPANS, spans);
        output.putIntArray(BLOCKED, blocked);
        output.putBoolean(BUILDABLE, buildable);
    }

    public static Outline load(ValueInput input) {
        int[] box = input.getIntArray(BOX).orElse(null);
        if (box == null || box.length != 6) {
            return NONE;
        }
        return new Outline(new BlockPos(box[0], box[1], box[2]), box[3], box[4], box[5],
                input.getIntArray(COLUMNS).orElse(new int[0]),
                input.getIntArray(SPANS).orElse(new int[0]),
                input.getIntArray(BLOCKED).orElse(new int[0]),
                input.getBooleanOr(BUILDABLE, false));
    }

    // Arrays, so the generated members would compare by identity and every refresh would look
    // like a change. The table only sends an update when the outline actually differs.

    @Override
    public boolean equals(Object other) {
        return other instanceof Outline o
                && corner.equals(o.corner) && width == o.width && height == o.height
                && depth == o.depth && buildable == o.buildable
                && Arrays.equals(columns, o.columns) && Arrays.equals(spans, o.spans)
                && Arrays.equals(blocked, o.blocked);
    }

    @Override
    public int hashCode() {
        return corner.hashCode() * 31 + Arrays.hashCode(blocked);
    }

    @Override
    public String toString() {
        return "Outline[" + corner + " " + width + "x" + height + "x" + depth
                + " columns=" + columns.length + " blocked=" + blocked.length / 3
                + (buildable ? " ok]" : " blocked]");
    }
}
