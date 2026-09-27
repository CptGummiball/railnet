package dev.cptgummiball.railnet;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Route-local sections between distinct block controllers, independent of direction. */
final class RailSections {
    private RailSections() {}
    record Key(BlockPos first,BlockPos last) {
        static Key of(BlockPos a,BlockPos b) {
            return a.asLong()<=b.asLong()?new Key(a,b):new Key(b,a);
        }
    }
    record Section(Key key,int from,int to,Set<BlockPos> rails) {}

    static List<Section> along(ServerWorld world,List<BlockPos> path) {
        List<Section> sections=new ArrayList<>();
        BlockPos previousController=null;
        int previousIndex=-1;
        for(int i=0;i<path.size();i++) {
            ControllerEntity controller=RailPath.controller(world,path.get(i));
            if(controller==null||controller.mode!=ControllerEntity.Mode.BLOCK
                ||!path.get(i).equals(RailPath.nearby(world,controller.getPos())))continue;
            BlockPos at=controller.getPos();
            if(at.equals(previousController))continue;
            if(previousController!=null) {
                Set<BlockPos> rails=new HashSet<>(path.subList(previousIndex,i+1));
                sections.add(new Section(Key.of(previousController,at),previousIndex,i,Set.copyOf(rails)));
            }
            previousController=at;previousIndex=i;
        }
        return List.copyOf(sections);
    }
}
