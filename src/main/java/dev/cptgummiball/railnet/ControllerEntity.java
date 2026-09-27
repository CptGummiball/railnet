package dev.cptgummiball.railnet;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

public final class ControllerEntity extends BlockEntity {
    public enum Mode { STATION, JUNCTION, BLOCK, DEPOT, SIGNAL }
    public UUID stationId = UUID.randomUUID();
    public String stationName = "Station";
    public Mode mode = Mode.STATION;
    public boolean locked;
    public ControllerEntity(BlockPos pos, BlockState state) { super(RailNet.CONTROLLER_ENTITY, pos, state); }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putInt("schema", 1);
        nbt.putUuid("stationId", stationId);
        nbt.putString("stationName", stationName);
        nbt.putString("mode", mode.name());
        nbt.putBoolean("locked", locked);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.containsUuid("stationId")) stationId = nbt.getUuid("stationId");
        stationName = nbt.getString("stationName").isBlank() ? "Station" : nbt.getString("stationName");
        try { mode = Mode.valueOf(nbt.getString("mode")); } catch (IllegalArgumentException ignored) { mode = Mode.STATION; }
        locked = nbt.getBoolean("locked");
    }
}
