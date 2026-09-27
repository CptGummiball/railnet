package dev.cptgummiball.railnet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.ArrayList;
import java.util.List;

/** Small, bounded GUI snapshots. No client supplied action is trusted without a server session. */
public final class RailGuiPackets {
    private RailGuiPackets() {}

    public record Entry(int slot, String icon, Text label) {
        private static Entry read(RegistryByteBuf buf) {
            return new Entry(buf.readVarInt(), buf.readString(128), TextCodecs.REGISTRY_PACKET_CODEC.decode(buf));
        }
        private static void write(RegistryByteBuf buf, Entry entry) {
            buf.writeVarInt(entry.slot);
            buf.writeString(entry.icon, 128);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, entry.label);
        }
    }

    /** kind: 0 = choices, 1 = text input, 2 = close invalid session. */
    public record State(long session, int kind, Text title, String value, List<Entry> entries) implements CustomPayload {
        public static final Id<State> ID = new Id<>(RailNet.id("gui_state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = PacketCodec.ofStatic(State::write, State::read);
        private static State read(RegistryByteBuf buf) {
            long session = buf.readLong();
            int kind = buf.readUnsignedByte();
            Text title = TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
            String value = buf.readString(40);
            int count = buf.readVarInt();
            if (count < 0 || count > 54) throw new IllegalArgumentException("Invalid RailNet menu size");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(Entry.read(buf));
            return new State(session, kind, title, value, List.copyOf(entries));
        }
        private static void write(RegistryByteBuf buf, State state) {
            buf.writeLong(state.session);
            buf.writeByte(state.kind);
            TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, state.title);
            buf.writeString(state.value, 160);
            buf.writeVarInt(state.entries.size());
            for (Entry entry : state.entries) Entry.write(buf, entry);
        }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** slot -1 saves input, slot -2 dismisses. Other slots must exist in the current menu. */
    public record Action(long session, int slot, String value) implements CustomPayload {
        public static final Id<Action> ID = new Id<>(RailNet.id("gui_action"));
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.ofStatic(
            (buf, action) -> {buf.writeLong(action.session);buf.writeVarInt(action.slot);buf.writeString(action.value, 160);},
            buf -> new Action(buf.readLong(), buf.readVarInt(), buf.readString(40)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
