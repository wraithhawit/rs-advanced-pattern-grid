package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * One recipe from EMI's recipe tree, already converted to Refined Storage resources on the client
 * (EMI is client-only), in the same shape RS's own EMI transfer uses.
 *
 * @param kind         which RS pattern type it becomes
 * @param label        the EMI recipe category's display name, for messages
 * @param inputs       per input slot, the acceptable resources (crafting: 9 slots, items only;
 *                     stonecutter: 1; smithing: template, base, addition; processing: any)
 * @param outputs      per output, the resources (crafting: the result; processing: outputs at 100%)
 * @param workstations item ids of the machines EMI says run this recipe's category (processing)
 */
public record RecipeRequest(Kind kind, String label, List<List<ResourceAmount>> inputs,
                            List<List<ResourceAmount>> outputs, List<ResourceLocation> workstations) {
    public enum Kind {
        CRAFTING, PROCESSING, STONECUTTER, SMITHING;

        /** Runs inside the autocrafter itself; no machine involved. */
        public boolean internal() {
            return this != PROCESSING;
        }
    }

    /** The first output, which decides "the network already has a pattern for this". */
    @Nullable
    public ResourceAmount primaryOutput() {
        for (final List<ResourceAmount> output : outputs) {
            if (!output.isEmpty()) {
                return output.getFirst();
            }
        }
        return null;
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<List<ResourceAmount>>> SLOTS = StreamCodec.of(
        (buf, slots) -> {
            buf.writeVarInt(slots.size());
            for (final List<ResourceAmount> slot : slots) {
                buf.writeVarInt(slot.size());
                for (final ResourceAmount amount : slot) {
                    ResourceCodecs.AMOUNT_STREAM_CODEC.encode(buf, amount);
                }
            }
        },
        buf -> {
            final int size = buf.readVarInt();
            final List<List<ResourceAmount>> slots = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                final int count = buf.readVarInt();
                final List<ResourceAmount> slot = new ArrayList<>(count);
                for (int j = 0; j < count; ++j) {
                    slot.add(ResourceCodecs.AMOUNT_STREAM_CODEC.decode(buf));
                }
                slots.add(slot);
            }
            return slots;
        }
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeRequest> STREAM_CODEC = StreamCodec.of(
        (buf, r) -> {
            buf.writeEnum(r.kind());
            buf.writeUtf(r.label(), 256);
            SLOTS.encode(buf, r.inputs());
            SLOTS.encode(buf, r.outputs());
            buf.writeCollection(r.workstations(), (b, id) -> b.writeResourceLocation(id));
        },
        buf -> new RecipeRequest(
            buf.readEnum(Kind.class),
            buf.readUtf(256),
            SLOTS.decode(buf),
            SLOTS.decode(buf),
            buf.readList(b -> b.readResourceLocation())
        )
    );
}
