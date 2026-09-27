package dev.cptgummiball.railnet;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Builds only the reachable part of a directed rail graph for a route request.
 * Straight corridors are walked once per branch and stored as single search edges.
 * Geometry and switch states are read afresh, so construction edits cannot stale a cache.
 */
final class RailGraph {
    private RailGraph() {}
    private record Edge(RailPath.Step end,List<BlockPos> geometry,double length) {}
    private record Known(double length,RailPath.Step previous,Edge edge) {}
    private record QueueEntry(RailPath.Step at,double length) {}

    static List<BlockPos> find(ServerWorld world,BlockPos start,BlockPos goal,int limit) {
        if(limit<2||!RailPath.rail(world,start)||!RailPath.rail(world,goal))return List.of();
        RailPath.Step origin=new RailPath.Step(start,null);
        PriorityQueue<QueueEntry> pending=new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::length));
        Map<RailPath.Step,Known> known=new HashMap<>();
        Set<RailPath.Step> settled=new HashSet<>();
        pending.add(new QueueEntry(origin,0));known.put(origin,new Known(0,null,null));
        int scanned=0;
        while(!pending.isEmpty() && scanned<limit) {
            QueueEntry node=pending.remove();
            if(settled.contains(node.at))continue;
            Known base=known.get(node.at);
            if(base==null||node.length>base.length)continue;
            settled.add(node.at);
            if(node.at.pos().equals(goal))return reconstruct(origin,node.at,known);
            for(RailPath.Step next:RailPath.successors(world,node.at)) {
                List<BlockPos> path=new ArrayList<>();
                Set<RailPath.Step> seen=new HashSet<>();seen.add(node.at);
                double distance=0;
                BlockPos previous=node.at.pos();
                RailPath.Step cursor=next;
                boolean loop=false;
                while(scanned<limit) {
                    if(!seen.add(cursor)) {loop=true;break;}
                    path.add(cursor.pos());
                    distance+=Math.sqrt(previous.getSquaredDistance(cursor.pos()));
                    previous=cursor.pos();scanned++;
                    if(cursor.pos().equals(goal)||RailPath.boundary(world,cursor.pos()))break;
                    List<RailPath.Step> outgoing=RailPath.successors(world,cursor);
                    if(outgoing.size()!=1)break;
                    cursor=outgoing.getFirst();
                }
                if(loop||path.isEmpty()||scanned>=limit)continue;
                double total=base.length+distance;
                Known old=known.get(cursor);
                if(old==null||total<old.length-1.0e-8) {
                    Edge edge=new Edge(cursor,List.copyOf(path),distance);
                    known.put(cursor,new Known(total,node.at,edge));
                    pending.add(new QueueEntry(cursor,total));
                }
            }
        }
        return List.of();
    }
    private static List<BlockPos> reconstruct(RailPath.Step origin,RailPath.Step goal,
                                               Map<RailPath.Step,Known> known) {
        LinkedList<List<BlockPos>> pieces=new LinkedList<>();
        for(RailPath.Step at=goal;!at.equals(origin);) {
            Known entry=known.get(at);
            if(entry==null||entry.edge==null)return List.of();
            pieces.addFirst(entry.edge.geometry);
            at=entry.previous;
        }
        List<BlockPos> route=new ArrayList<>();route.add(origin.pos());
        for(List<BlockPos> piece:pieces)route.addAll(piece);
        return route;
    }
}
