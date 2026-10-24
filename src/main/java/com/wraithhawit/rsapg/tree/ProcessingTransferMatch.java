package com.wraithhawit.rsapg.tree;

import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import java.util.List;
import java.util.function.Predicate;

/** Conservative comparison: preserve slot order, selected alternative and quantity. */
public final class ProcessingTransferMatch {
    private ProcessingTransferMatch() { }

    public static boolean matches(final List<ResourceAmount> actual, final List<List<ResourceAmount>> requested) {
        return matches(actual, requested, key -> false);
    }

    /**
     * As {@link #matches(List, List)}, but a requested slot made only of {@code storedOffPattern}
     * resources is skipped when the pattern has none of them as inputs: another mod moved it off the
     * inputs (gttweaks stores GregTech's programmed circuit on the pattern itself).
     */
    public static boolean matches(final List<ResourceAmount> actual, final List<List<ResourceAmount>> requested,
                                  final Predicate<ResourceKey> storedOffPattern) {
        final boolean patternHasOne = actual.stream().anyMatch(a -> storedOffPattern.test(a.resource()));
        final var occupied = requested.stream()
            .filter(slot -> !slot.isEmpty())
            .filter(slot -> patternHasOne || !slot.stream().allMatch(a -> storedOffPattern.test(a.resource())))
            .toList();
        if (actual.size() != occupied.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!occupied.get(i).contains(actual.get(i))) {
                return false;
            }
        }
        return true;
    }
}
