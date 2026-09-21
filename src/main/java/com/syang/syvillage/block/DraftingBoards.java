package com.syang.syvillage.block;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Every drafting table the client currently has loaded.
 *
 * <p>The outline is drawn once a tick for each of these, and this is how the drawing code finds
 * them without searching. A table adds itself when its block entity loads and takes itself out
 * when it unloads, which is exactly the window in which it could be on screen.
 *
 * <p>In common code, holding nothing but client-side objects, on purpose: a class the client
 * package can read without the server ever loading a class that imports the client.
 *
 * <p>Not state in the sense principle three forbids. It is a view of what is loaded, rebuilt
 * from scratch every time a chunk comes in, and saved nowhere.
 */
public final class DraftingBoards {

    private DraftingBoards() {}

    private static final Set<DraftingTableEntity> LOADED = new LinkedHashSet<>();

    static void add(DraftingTableEntity table) {
        LOADED.add(table);
    }

    static void remove(DraftingTableEntity table) {
        LOADED.remove(table);
    }

    public static Set<DraftingTableEntity> loaded() {
        return Collections.unmodifiableSet(LOADED);
    }

    /** Leaving a world. Everything in here belonged to it. */
    public static void clear() {
        LOADED.clear();
    }
}
