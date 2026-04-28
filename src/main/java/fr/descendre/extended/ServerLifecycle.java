package fr.descendre.extended;

import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ServerLifecycle {
    private ServerLifecycle() {
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        ExtendedBlockStore.saveAndUnload(event.getServer());
    }
}