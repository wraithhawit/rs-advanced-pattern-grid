package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingTransferMatchTest {
    record Key(String name) implements ResourceKey { }
    private static ResourceAmount amount(String name, long count) {
        return new ResourceAmount(new Key(name), count);
    }

    @Test void acceptsTransferredAlternativeAndEmptyPadding() {
        assertTrue(ProcessingTransferMatch.matches(List.of(amount("iron", 2)),
            List.of(List.of(), List.of(amount("iron", 2), amount("copper", 2)), List.of())));
    }

    @Test void rejectsChangedInputEvenWhenOutputWouldBeTheSame() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("copper", 2)),
            List.of(List.of(amount("iron", 2)))));
    }

    @Test void rejectsChangedQuantity() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("iron", 1)),
            List.of(List.of(amount("iron", 2)))));
    }

    @Test void rejectsMissingIngredient() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("iron", 2)),
            List.of(List.of(amount("iron", 2)), List.of(amount("water", 1000)))));
    }

    @Test void rejectsAddedIngredient() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("iron", 2), amount("water", 1000)),
            List.of(List.of(amount("iron", 2)))));
    }

    @Test void preservesDuplicateSlotsAndTheirAmounts() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("iron", 4)),
            List.of(List.of(amount("iron", 2)), List.of(amount("iron", 2)))));
    }

    private static boolean circuit(ResourceKey key) {
        return key.equals(new Key("circuit"));
    }

    @Test void skipsCircuitSlotThePatternStoresElsewhere() {
        assertTrue(ProcessingTransferMatch.matches(List.of(amount("tin", 1)),
            List.of(List.of(amount("tin", 1)), List.of(amount("circuit", 1))), ProcessingTransferMatchTest::circuit));
    }

    @Test void comparesCircuitWhenThePatternKeepsIt() {
        assertTrue(ProcessingTransferMatch.matches(List.of(amount("tin", 1), amount("circuit", 1)),
            List.of(List.of(amount("tin", 1)), List.of(amount("circuit", 1))), ProcessingTransferMatchTest::circuit));
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("circuit", 1), amount("tin", 1)),
            List.of(List.of(amount("tin", 1)), List.of(amount("circuit", 1))), ProcessingTransferMatchTest::circuit));
    }

    @Test void circuitSkipStillRejectsOtherChanges() {
        assertFalse(ProcessingTransferMatch.matches(List.of(amount("copper", 1)),
            List.of(List.of(amount("tin", 1)), List.of(amount("circuit", 1))), ProcessingTransferMatchTest::circuit));
    }
}
