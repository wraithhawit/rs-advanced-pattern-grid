package com.wraithhawit.rsapg.tree;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server's verdict on one {@link RecipeRequest} (same index), sent back for the preview.
 *
 * @param target   where the pattern would go, as the autocrafter manager names it (or why not)
 * @param location where that is (coordinates and dimension), for the hover tooltip; empty if none
 */
public record PlanLine(int index, Status status, String target, String location) {
    public enum Status {
        /** Would be encoded and placed in {@link #target}. */
        PLACE,
        /** The network already has a pattern for this output. */
        ALREADY_HAS,
        /** An earlier recipe in this tree already makes this output. */
        DUPLICATE,
        /** No autocrafter on the network fits (no matching machine, or all full); not encoded. */
        NO_TARGET,
        /** Could not be turned into an RS pattern (nothing RS can hold as output). */
        UNSUPPORTED,
        /** Encoded and placed in {@link #target} (after the Encode button). */
        PLACED,
        /** Tried and failed; {@link #target} says why (after the Encode button). */
        FAILED
    }

    public static final StreamCodec<ByteBuf, PlanLine> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PlanLine::index,
        ByteBufCodecs.idMapper(i -> Status.values()[i], Status::ordinal), PlanLine::status,
        ByteBufCodecs.STRING_UTF8, PlanLine::target,
        ByteBufCodecs.STRING_UTF8, PlanLine::location,
        PlanLine::new
    );
}
