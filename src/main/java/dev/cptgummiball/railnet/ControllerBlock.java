package dev.cptgummiball.railnet;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class ControllerBlock extends Block implements BlockEntityProvider {
    private final ControllerEntity.Mode fixedMode;
    public ControllerBlock(Settings settings, ControllerEntity.Mode mode) {
        super(settings);
        fixedMode=mode;
    }
    public ControllerEntity.Mode mode(BlockState state) { return fixedMode; }
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ControllerEntity(pos, state); }
    @Override public boolean hasComparatorOutput(BlockState state) { return true; }
    @Override public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world instanceof net.minecraft.server.world.ServerWorld server ? TrainData.get(server).signal(server, pos) : 0;
    }
    @Override protected void neighborUpdate(BlockState state,World world,BlockPos pos,Block sourceBlock,
        BlockPos sourcePos,boolean notify) {
        super.neighborUpdate(state,world,pos,sourceBlock,sourcePos,notify);
        if(!(world instanceof net.minecraft.server.world.ServerWorld server)
            ||!(world.getBlockEntity(pos) instanceof ControllerEntity c))return;
        boolean powered=world.isReceivingRedstonePower(pos);
        if(powered==c.lastPowered)return;
        c.lastPowered=powered;
        if(c.inputAction==ControllerEntity.InputAction.LOCK_WHILE_POWERED)c.locked=powered;
        if(powered&&c.inputAction==ControllerEntity.InputAction.STOP_TRAIN)
            TrainData.get(server).stopAt(server,pos);
        c.markDirty();
    }
    @Override public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world instanceof net.minecraft.server.world.ServerWorld server && world.getBlockEntity(pos) instanceof ControllerEntity c)
            TrainData.get(server).register(c);
    }
    @Override public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient && world instanceof net.minecraft.server.world.ServerWorld server) {
            TrainData.get(server).removeStation(pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}

/** Kept under the old registry ID so controllers in existing worlds remain readable. */
final class LegacyControllerBlock extends ControllerBlock {
    static final EnumProperty<ControllerEntity.Mode> MODE=EnumProperty.of("mode",ControllerEntity.Mode.class);
    LegacyControllerBlock(Settings settings) {
        super(settings,ControllerEntity.Mode.STATION);
        setDefaultState(getStateManager().getDefaultState().with(MODE,ControllerEntity.Mode.STATION));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(MODE); }
    @Override public ControllerEntity.Mode mode(BlockState state) { return state.get(MODE); }
}
