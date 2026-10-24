package com.wraithhawit.rsapg;

import com.wraithhawit.rsapg.client.tree.TreeDumpClient;

/** Client-only entry points for packet handlers; nothing here loads on a dedicated server. */
final class ClientHooks {
    private ClientHooks() {
    }

    static void onTreePlan(final ModNetwork.TreePlan plan) {
        TreeDumpClient.onPlan(plan);
    }
}
