package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Name and parents. The family name is inherited paternally, which is most of why players
 * grow attached to individual villagers - see docs/population.md.
 *
 * <p>Parents are {@link Optional} rather than nullable UUIDs so the codec stays total. First
 * generation residents (adopted at registration) have neither.
 */
public record Lineage(String givenName, String familyName, Optional<UUID> motherId, Optional<UUID> fatherId) {

    public static final Codec<Lineage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("given_name").forGetter(Lineage::givenName),
            Codec.STRING.fieldOf("family_name").forGetter(Lineage::familyName),
            UUIDUtil.CODEC.optionalFieldOf("mother_id").forGetter(Lineage::motherId),
            UUIDUtil.CODEC.optionalFieldOf("father_id").forGetter(Lineage::fatherId)
    ).apply(i, Lineage::new));

    public static Lineage founder(String givenName, String familyName) {
        return new Lineage(givenName, familyName, Optional.empty(), Optional.empty());
    }

    /** Display order is a localisation concern; M2 formats this per language. */
    public String fullName() {
        return givenName + " " + familyName;
    }
}
