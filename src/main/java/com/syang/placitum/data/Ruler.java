package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringRepresentable;

/**
 * V2 hook. In V1 this is always {@link Npc} and does nothing at all.
 *
 * <p>It exists now because it is part of the save format. Adding it later would mean a
 * migration; reserving the slot costs nothing. See docs/v2-deferred.md.
 */
public sealed interface Ruler {

    Codec<Ruler> CODEC = Kind.CODEC.dispatch("type", Ruler::kind, Kind::codec);

    Kind kind();

    record Npc(UUID residentId) implements Ruler {
        public static final MapCodec<Npc> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                UUIDUtil.CODEC.fieldOf("resident_id").forGetter(Npc::residentId)
        ).apply(i, Npc::new));

        @Override
        public Kind kind() {
            return Kind.NPC;
        }
    }

    record Player(UUID profileId, UUID regentId) implements Ruler {
        public static final MapCodec<Player> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                UUIDUtil.CODEC.fieldOf("profile_id").forGetter(Player::profileId),
                UUIDUtil.CODEC.fieldOf("regent_id").forGetter(Player::regentId)
        ).apply(i, Player::new));

        @Override
        public Kind kind() {
            return Kind.PLAYER;
        }
    }

    enum Kind implements StringRepresentable {
        NPC,
        PLAYER;

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        MapCodec<? extends Ruler> codec() {
            return switch (this) {
                case NPC -> Npc.MAP_CODEC;
                case PLAYER -> Player.MAP_CODEC;
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
