package dev.cptgummiball.railnet;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/** Bounded, port-aware route search. No chunk is loaded by this class. */
public final class RailPath {
    private RailPath() {}
    record Step(BlockPos pos, Direction entry) {}
    public static boolean rail(ServerWorld world, BlockPos p) {
        return world.isChunkLoaded(p) && world.getBlockState(p).getBlock() instanceof AbstractRailBlock;
    }
    public static BlockPos nearby(ServerWorld world, BlockPos p) {
        for (int y : new int[]{0,1,2,-1,-2}) {
            BlockPos q = p.add(0, y, 0);
            if (rail(world, q)) return q;
            for (Direction d : Direction.Type.HORIZONTAL) if (rail(world, q.offset(d))) return q.offset(d);
        }
        return null;
    }
    public static ControllerEntity controller(World world, BlockPos rail) {
        for (int y : new int[]{0,-1,-2,1}) {
            BlockPos p=rail.up(y);
            if(world.isChunkLoaded(p) && world.getBlockEntity(p) instanceof ControllerEntity c)return c;
        }
        for (int y : new int[]{0,-1,-2,1}) for(Direction d:Direction.Type.HORIZONTAL) {
            BlockPos p=rail.offset(d).up(y);
            if(world.isChunkLoaded(p) && world.getBlockEntity(p) instanceof ControllerEntity c)return c;
        }
        return null;
    }
    private static boolean port(RailShape shape, Direction side) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> side == Direction.NORTH || side == Direction.SOUTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> side == Direction.EAST || side == Direction.WEST;
            case SOUTH_EAST -> side == Direction.SOUTH || side == Direction.EAST;
            case SOUTH_WEST -> side == Direction.SOUTH || side == Direction.WEST;
            case NORTH_WEST -> side == Direction.NORTH || side == Direction.WEST;
            case NORTH_EAST -> side == Direction.NORTH || side == Direction.EAST;
        };
    }
    private static RailShape shape(Direction a, Direction b) {
        if (a == b) return null;
        if (a.getAxis() == b.getAxis()) return a.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        if (a == Direction.NORTH || b == Direction.NORTH) return a == Direction.EAST || b == Direction.EAST ? RailShape.NORTH_EAST : RailShape.NORTH_WEST;
        return a == Direction.EAST || b == Direction.EAST ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST;
    }
    private static boolean allowed(ServerWorld world, BlockPos p, Direction in, Direction out) {
        if (in == out) return false;
        BlockState state = world.getBlockState(p);
        if (state.isOf(RailNet.CROSSING)) return in == out.getOpposite();
        ControllerEntity c = controller(world, p);
        if (c != null && c.mode == ControllerEntity.Mode.JUNCTION) {
            if (c.locked) return port(state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty()), in)
                && port(state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty()), out);
            return shape(in, out) != null;
        }
        RailShape current = state.get(((AbstractRailBlock) state.getBlock()).getShapeProperty());
        return port(current, in) && port(current, out);
    }
    public static boolean switchJunction(ServerWorld world, BlockPos p, Direction in, Direction out) {
        ControllerEntity c = controller(world, p);
        if (c == null || c.mode != ControllerEntity.Mode.JUNCTION) return true;
        if (c.locked) return allowed(world, p, in, out);
        RailShape target = shape(in, out);
        BlockState state = world.getBlockState(p);
        if (!(state.getBlock() instanceof AbstractRailBlock rail) || target == null || state.isOf(RailNet.CROSSING)) return false;
        try {
            if (state.get(rail.getShapeProperty()) != target) world.setBlockState(p, state.with(rail.getShapeProperty(), target), 3);
            return world.getBlockState(p).get(rail.getShapeProperty()) == target;
        } catch (IllegalArgumentException e) { return false; }
    }
    /** A compressed graph node is an endpoint, junction, crossing or controller. */
    static boolean boundary(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isOf(RailNet.CROSSING) || controller(world,pos)!=null;
    }
    /** The entry direction disambiguates crossings and curves. No chunk is loaded. */
    static List<Step> successors(ServerWorld world, Step current) {
        List<Step> result=new ArrayList<>();
        if(!rail(world,current.pos))return result;
        BlockState currentState=world.getBlockState(current.pos);
        ControllerEntity currentController=controller(world,current.pos);
        for(Direction exit:Direction.Type.HORIZONTAL) {
            if(current.entry!=null&&!allowed(world,current.pos,current.entry,exit))continue;
            if(current.entry==null&&!currentState.isOf(RailNet.CROSSING)
                &&currentController==null
                &&!port(currentState.get(((AbstractRailBlock)currentState.getBlock()).getShapeProperty()),exit))continue;
            for(int dy:new int[]{0,1,-1}) {
                BlockPos next=current.pos.offset(exit).up(dy);
                if(!rail(world,next))continue;
                Direction entry=exit.getOpposite();
                BlockState state=world.getBlockState(next);
                ControllerEntity c=controller(world,next);
                if(!state.isOf(RailNet.CROSSING) && (c==null||c.mode!=ControllerEntity.Mode.JUNCTION)
                    &&!port(state.get(((AbstractRailBlock)state.getBlock()).getShapeProperty()),entry))continue;
                result.add(new Step(next,entry));
            }
        }
        return result;
    }
    /** Finds a route over contracted corridors; the original bounded search is a conservative fallback. */
    public static List<BlockPos> find(ServerWorld world, BlockPos start, BlockPos goal, int limit) {
        List<BlockPos> compressed=RailGraph.find(world,start,goal,limit);
        return !compressed.isEmpty()?compressed:findUncompressed(world,start,goal,limit);
    }
    static List<BlockPos> findUncompressed(ServerWorld world, BlockPos start, BlockPos goal, int limit) {
        if (!rail(world, start) || !rail(world, goal)) return List.of();
        Step origin = new Step(start, null);
        ArrayDeque<Step> queue = new ArrayDeque<>(); queue.add(origin);
        Map<Step, Step> previous = new HashMap<>(); previous.put(origin, origin);
        Step found = null;
        while (!queue.isEmpty() && previous.size() < limit) {
            Step current = queue.removeFirst();
            if (current.pos.equals(goal)) { found = current; break; }
            for(Step next:successors(world,current))
                if(previous.putIfAbsent(next,current)==null)queue.addLast(next);
        }
        if (found == null) return List.of();
        ArrayList<BlockPos> result = new ArrayList<>();
        for (Step at = found; !at.equals(origin); at = previous.get(at)) result.add(at.pos);
        result.add(start); Collections.reverse(result);
        return result;
    }
    public static Direction between(BlockPos a, BlockPos b) {
        return Direction.getFacing(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
    }
}
