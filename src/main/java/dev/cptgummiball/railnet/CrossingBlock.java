package dev.cptgummiball.railnet;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;

/** Logical crossing: routing only permits opposite exits; the visual rails overlap. */
public final class CrossingBlock extends AbstractRailBlock {
    public static final MapCodec<CrossingBlock> CODEC = createCodec(CrossingBlock::new);
    public static final EnumProperty<RailShape> SHAPE = EnumProperty.of("shape", RailShape.class);
    public CrossingBlock(Settings settings) {
        super(true, settings);
        setDefaultState(getStateManager().getDefaultState()
            .with(SHAPE, RailShape.NORTH_SOUTH)
            .with(Properties.WATERLOGGED, false));
    }
    @Override public EnumProperty<RailShape> getShapeProperty() { return SHAPE; }
    @Override protected MapCodec<? extends AbstractRailBlock> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(SHAPE, Properties.WATERLOGGED);
    }
}
