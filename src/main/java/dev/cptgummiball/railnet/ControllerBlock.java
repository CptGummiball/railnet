package dev.cptgummiball.railnet;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ControllerBlock extends Block implements BlockEntityProvider {
    public ControllerBlock(Settings settings) { super(settings); }
    @Override public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ControllerEntity(pos, state); }
    @Override public boolean hasComparatorOutput(BlockState state) { return true; }
    @Override public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world instanceof net.minecraft.server.world.ServerWorld server ? TrainData.get(server).signal(server, pos) : 0;
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
