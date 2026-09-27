package dev.cptgummiball.railnet;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.StringIdentifiable;
import java.util.UUID;
import java.util.Locale;

public final class ControllerEntity extends BlockEntity {
    public enum Mode implements StringIdentifiable {
        STATION, JUNCTION, BLOCK, DEPOT, SIGNAL, BOARD;
        @Override public String asString() { return name().toLowerCase(Locale.ROOT); }
    }
    public UUID stationId = UUID.randomUUID();
    public String stationName = "Station";
    public Mode mode = Mode.STATION;
    public boolean locked;
    public enum InputAction { NONE, LOCK_WHILE_POWERED, STOP_TRAIN }
    public InputAction inputAction=InputAction.NONE;
    public boolean lastPowered;
    public int stopTicks=200;
    public UUID boardStationId;
    public void setMode(Mode next) {
        if(!(getCachedState().getBlock() instanceof LegacyControllerBlock))return;
        mode=next;
        if(world!=null && world.getBlockState(pos).isOf(RailNet.CONTROLLER))
            world.setBlockState(pos,world.getBlockState(pos).with(LegacyControllerBlock.MODE,next),3);
        markDirty();
    }
    public ControllerEntity(BlockPos pos, BlockState state) {
        super(RailNet.CONTROLLER_ENTITY, pos, state);
        mode=((ControllerBlock)state.getBlock()).mode(state);
    }
    public void copySettingsFrom(ControllerEntity old) {
        stationId=old.stationId;
        stationName=old.stationName;
        locked=old.locked;
        inputAction=old.inputAction;
        lastPowered=old.lastPowered;
        stopTicks=old.stopTicks;
        boardStationId=old.boardStationId;
        markDirty();
    }
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putInt("schema", 1);
        nbt.putUuid("stationId", stationId);
        nbt.putString("stationName", stationName);
        nbt.putString("mode", mode.name());
        nbt.putBoolean("locked", locked);
        nbt.putString("inputAction",inputAction.name());
        nbt.putBoolean("lastPowered",lastPowered);
        nbt.putInt("stopTicks",stopTicks);
        if(boardStationId!=null)nbt.putUuid("boardStationId",boardStationId);
    }
    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.containsUuid("stationId")) stationId = nbt.getUuid("stationId");
        stationName = nbt.getString("stationName").isBlank() ? "Station" : nbt.getString("stationName");
        if(getCachedState().getBlock() instanceof LegacyControllerBlock) {
            try { mode = Mode.valueOf(nbt.getString("mode")); }
            catch (IllegalArgumentException ignored) { mode = Mode.STATION; }
        } else mode=((ControllerBlock)getCachedState().getBlock()).mode(getCachedState());
        locked = nbt.getBoolean("locked");
        try{inputAction=InputAction.valueOf(nbt.getString("inputAction"));}
        catch(IllegalArgumentException ignored){inputAction=InputAction.NONE;}
        lastPowered=nbt.getBoolean("lastPowered");
        stopTicks=nbt.contains("stopTicks")?Math.max(0,Math.min(1200,nbt.getInt("stopTicks"))):200;
        boardStationId=nbt.containsUuid("boardStationId")?nbt.getUuid("boardStationId"):null;
    }
}
