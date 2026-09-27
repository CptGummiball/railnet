package dev.cptgummiball.railnet.client;

import dev.cptgummiball.railnet.RailGuiPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class RailNetClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RailGuiPackets.State.ID,(state,context) ->
            context.client().execute(() -> {
                if(state.kind()==2) {
                    if(context.client().currentScreen instanceof RailNetScreen current
                        &&current.session()==state.session())current.close();
                } else context.client().setScreen(new RailNetScreen(state));
            }));
    }
}
