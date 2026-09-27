package dev.cptgummiball.railnet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.*;

/** One authority per dimension. Route geometry and reservations are deliberately transient. */
public final class TrainData extends PersistentState {
    public enum Status { STOPPED, RUNNING, WAITING_FOR_TRACK, WAITING_FOR_DEPARTURE, ROUTE_LOST, ARRIVED }
    public enum LineMode { STOP, LOOP, SHUTTLE }
    public static final class LineStop {
        public final UUID stationId;
        public int departureTick=-1;
        public int dwellOverride=-1;
        public LineStop(UUID stationId) { this.stationId=stationId; }
    }
    public static final class Line {
        public final UUID id;
        public String name="Line";
        public final List<LineStop> stops=new ArrayList<>();
        public LineMode mode=LineMode.STOP;
        public int intervalTicks;
        Line(UUID id) { this.id=id; }
    }
    public static final class Train {
        public final UUID id;
        public final List<UUID> carts = new ArrayList<>();
        public String name = "Train";
        public Status status = Status.STOPPED;
        public UUID destination;
        public double speed;
        public int preset = 1;
        public List<BlockPos> path = List.of();
        public double[] distances = new double[0];
        public double progress;
        public int index;
        public UUID lineId;
        public int lineIndex=-1;
        public int lineDirection=1;
        public long nextDepartureTick;
        Train(UUID id) { this.id = id; }
    }
    public record Station(UUID id, String name, BlockPos controller) {}
    private final Map<UUID, Train> trains = new LinkedHashMap<>();
    private final Map<UUID, UUID> cartToTrain = new HashMap<>();
    private final Map<UUID, Station> stations = new LinkedHashMap<>();
    private final Map<UUID, Line> lines=new LinkedHashMap<>();
    private final Map<BlockPos, UUID> held = new HashMap<>();
    private final Set<BlockPos> occupied = new HashSet<>();
    private final Set<BlockPos> signaled=new HashSet<>();
    private final Map<UUID,BlockPos> lastSeen=new HashMap<>();
    private final Map<UUID,Integer> missingTicks=new HashMap<>();
    private int tick;
    private long serviceTick;

    public static TrainData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(new PersistentState.Type<>(TrainData::new,
            (nbt, lookup) -> TrainData.fromNbt(nbt), DataFixTypes.LEVEL), "railnet_trains");
    }
    public static TrainData fromNbt(NbtCompound root) {
        TrainData data = new TrainData();
        int schema=root.getInt("schema");
        data.serviceTick=root.getLong("serviceTick");
        for (NbtElement element : root.getList("trains", NbtElement.COMPOUND_TYPE)) {
            NbtCompound n = (NbtCompound) element;
            if (!n.containsUuid("id")) continue;
            Train train = new Train(n.getUuid("id"));
            train.name = n.getString("name");
            train.destination = n.containsUuid("destination") ? n.getUuid("destination") : null;
            train.preset = Math.max(0, Math.min(2, n.getInt("preset")));
            train.lineId=n.containsUuid("lineId")?n.getUuid("lineId"):null;
            train.lineIndex=n.contains("lineIndex")?n.getInt("lineIndex"):-1;
            train.lineDirection=n.getInt("lineDirection")==-1?-1:1;
            for (NbtElement c : n.getList("carts", NbtElement.COMPOUND_TYPE)) {
                NbtCompound cart = (NbtCompound) c;
                if (cart.containsUuid("id") && !data.cartToTrain.containsKey(cart.getUuid("id"))) {
                    train.carts.add(cart.getUuid("id")); data.cartToTrain.put(cart.getUuid("id"), train.id);
                }
            }
            // Never resume blindly after restart; a route must be validated again.
            if(schema<=2 && train.carts.size()==1 && "Train".equals(train.name) && train.destination==null)
                data.cartToTrain.remove(train.carts.getFirst());
            else if(!train.carts.isEmpty())data.trains.put(train.id,train);
        }
        for (NbtElement element : root.getList("stations", NbtElement.COMPOUND_TYPE)) {
            NbtCompound n = (NbtCompound) element;
            if (n.containsUuid("id")) {
                Station s = new Station(n.getUuid("id"), n.getString("name"), BlockPos.fromLong(n.getLong("pos")));
                data.stations.put(s.id, s);
            }
        }
        for(NbtElement element:root.getList("lines",NbtElement.COMPOUND_TYPE)) {
            NbtCompound n=(NbtCompound)element;
            if(!n.containsUuid("id"))continue;
            Line line=new Line(n.getUuid("id"));line.name=n.getString("name");
            try{line.mode=LineMode.valueOf(n.getString("mode"));}catch(IllegalArgumentException ignored){}
            line.intervalTicks=Math.max(0,Math.min(72000,n.getInt("intervalTicks")));
            for(NbtElement entry:n.getList("stops",NbtElement.COMPOUND_TYPE)) {
                NbtCompound stop=(NbtCompound)entry;
                if(!stop.containsUuid("stationId"))continue;
                LineStop s=new LineStop(stop.getUuid("stationId"));
                s.departureTick=stop.contains("departureTick")?stop.getInt("departureTick"):-1;
                s.dwellOverride=stop.contains("dwellOverride")?stop.getInt("dwellOverride"):-1;
                line.stops.add(s);
            }
            data.lines.put(line.id,line);
        }
        return data;
    }
    @Override public NbtCompound writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup lookup) {
        root.putInt("schema", 3);
        root.putLong("serviceTick",serviceTick);
        NbtList list = new NbtList();
        for (Train t : trains.values()) {
            NbtCompound n = new NbtCompound(); n.putUuid("id", t.id); n.putString("name", t.name);
            n.putInt("preset", t.preset);
            if(t.lineId!=null)n.putUuid("lineId",t.lineId);
            n.putInt("lineIndex",t.lineIndex);n.putInt("lineDirection",t.lineDirection);
            if (t.destination != null) n.putUuid("destination", t.destination);
            NbtList carts = new NbtList();
            for (UUID id : t.carts) { NbtCompound c = new NbtCompound(); c.putUuid("id", id); carts.add(c); }
            n.put("carts", carts); list.add(n);
        }
        root.put("trains", list);
        NbtList stops = new NbtList();
        for (Station s : stations.values()) {
            NbtCompound n = new NbtCompound(); n.putUuid("id", s.id); n.putString("name", s.name); n.putLong("pos", s.controller.asLong()); stops.add(n);
        }
        root.put("stations", stops);
        NbtList savedLines=new NbtList();
        for(Line line:lines.values()) {
            NbtCompound n=new NbtCompound();n.putUuid("id",line.id);n.putString("name",line.name);
            n.putString("mode",line.mode.name());n.putInt("intervalTicks",line.intervalTicks);
            NbtList entries=new NbtList();
            for(LineStop stop:line.stops) {
                NbtCompound s=new NbtCompound();s.putUuid("stationId",stop.stationId);
                s.putInt("departureTick",stop.departureTick);s.putInt("dwellOverride",stop.dwellOverride);entries.add(s);
            }
            n.put("stops",entries);savedLines.add(n);
        }
        root.put("lines",savedLines);return root;
    }
    public Collection<Train> trains() { return Collections.unmodifiableCollection(trains.values()); }
    public long serviceTick() { return serviceTick; }
    public Collection<Station> stations() { return Collections.unmodifiableCollection(stations.values()); }
    public Collection<Line> lines() { return Collections.unmodifiableCollection(lines.values()); }
    public Line line(UUID id) { return lines.get(id); }
    public Line createLine() {
        Line line=new Line(UUID.randomUUID());line.name="Line "+(lines.size()+1);
        lines.put(line.id,line);markDirty();return line;
    }
    public void removeLine(Line line) {
        if(lines.remove(line.id)==null)return;
        for(Train t:trains.values())if(line.id.equals(t.lineId)){t.lineId=null;stop(t);}
        markDirty();
    }
    public void assign(Train train,Line line) {
        stop(train);train.lineId=line==null?null:line.id;
        train.lineIndex=-1;train.lineDirection=1;
        train.destination=line==null||line.stops.isEmpty()?null:line.stops.getFirst().stationId;
        markDirty();
    }
    public Train byCart(UUID cart) { return trains.get(cartToTrain.get(cart)); }
    public Train byId(UUID id) { return trains.get(id); }
    public Train ensure(AbstractMinecartEntity cart) {
        Train existing = byCart(cart.getUuid());
        if (existing != null) return existing;
        Train t = new Train(UUID.randomUUID()); t.carts.add(cart.getUuid());
        trains.put(t.id, t); cartToTrain.put(cart.getUuid(), t.id); markDirty(); return t;
    }
    public boolean couple(AbstractMinecartEntity a, AbstractMinecartEntity b) {
        Train aTrain=byCart(a.getUuid()), bTrain=byCart(b.getUuid());
        if(a.getUuid().equals(b.getUuid()) || (aTrain!=null && aTrain==bTrain)
            || (aTrain!=null && aTrain.status==Status.RUNNING)
            || (bTrain!=null && bTrain.status==Status.RUNNING)
            || (aTrain==null?1:aTrain.carts.size())+(bTrain==null?1:bTrain.carts.size())>32)return false;
        Train first=ensure(a),second=ensure(b);
        first.carts.addAll(second.carts);
        second.carts.forEach(id -> cartToTrain.put(id, first.id));
        trains.remove(second.id); first.path = List.of(); first.status = Status.STOPPED; markDirty(); return true;
    }
    public boolean detach(UUID cart) {
        Train train = byCart(cart);
        if (train == null || train.carts.size() < 2 || train.status == Status.RUNNING) return false;
        train.carts.remove(cart); cartToTrain.remove(cart);
        Train detached = new Train(UUID.randomUUID()); detached.carts.add(cart);
        trains.put(detached.id, detached); cartToTrain.put(cart, detached.id);
        train.path = List.of(); train.status = Status.STOPPED; markDirty(); return true;
    }
    public void register(ControllerEntity c) {
        if (c.mode == ControllerEntity.Mode.STATION) stations.put(c.stationId, new Station(c.stationId, c.stationName, c.getPos()));
        else stations.remove(c.stationId);
        markDirty();
    }
    public void removeStation(BlockPos p) { if (stations.values().removeIf(s -> s.controller.equals(p))) markDirty(); }
    public Station station(UUID id) { return stations.get(id); }
    private static BlockPos railAt(ServerWorld world, AbstractMinecartEntity cart) {
        BlockPos p = cart.getBlockPos();
        if (RailPath.rail(world, p)) return p;
        if (RailPath.rail(world, p.down())) return p.down();
        return null;
    }
    public boolean start(ServerWorld world, Train t) {
        t.status = Status.STOPPED; t.speed = 0; t.path = List.of();
        if (t.destination == null || t.carts.isEmpty()) return false;
        Station station = stations.get(t.destination);
        if (station == null || !world.isChunkLoaded(station.controller)
            || !(world.getBlockEntity(station.controller) instanceof ControllerEntity c)
            || !c.stationId.equals(station.id) || c.mode != ControllerEntity.Mode.STATION) { t.status = Status.ROUTE_LOST; return false; }
        Entity lead = world.getEntity(t.carts.get(0));
        if (!(lead instanceof AbstractMinecartEntity cart)) return false;
        BlockPos start = railAt(world, cart), end = RailPath.nearby(world, station.controller);
        if (start == null || end == null) { t.status = Status.ROUTE_LOST; return false; }
        List<BlockPos> route = RailPath.find(world, start, end, 8192);
        if(route.size()==1) {
            t.status=Status.ARRIVED;arrived(world,t);markDirty();return true;
        }
        if(route.size()>1 && t.carts.size()>1) {
            Entity second=world.getEntity(t.carts.get(1));
            if(second instanceof AbstractMinecartEntity follower && route.get(1).equals(railAt(world,follower))) {
                Collections.reverse(t.carts);
                Entity reversed=world.getEntity(t.carts.getFirst());
                if(!(reversed instanceof AbstractMinecartEntity newLead))return false;
                BlockPos newStart=railAt(world,newLead);
                route=RailPath.find(world,newStart,end,8192);
            }
        }
        if (route.size() < 2) { t.status = Status.ROUTE_LOST; return false; }
        List<BlockPos> all = new ArrayList<>();
        for (int i = t.carts.size() - 1; i > 0; i--) {
            Entity e = world.getEntity(t.carts.get(i));
            if (!(e instanceof AbstractMinecartEntity follower) || railAt(world, follower) == null) return false;
            all.add(railAt(world, follower));
        }
        all.addAll(route);
        t.path = all; t.index = all.size() - route.size();
        t.distances = new double[all.size()];
        for (int i = 1; i < all.size(); i++) t.distances[i] = t.distances[i-1] + Math.sqrt(all.get(i).getSquaredDistance(all.get(i-1)));
        t.progress = t.distances[t.index]; t.speed = 0; t.status = Status.RUNNING; markDirty(); return true;
    }
    public void stop(Train t) { t.status = Status.STOPPED; t.speed = 0; t.path = List.of(); t.distances = new double[0]; markDirty(); }
    public void stopAt(ServerWorld world,BlockPos controller) {
        BlockPos rail=RailPath.nearby(world,controller);
        if(rail==null)return;
        for(Train t:trains.values())for(UUID id:t.carts) {
            Entity e=world.getEntity(id);
            if(e instanceof AbstractMinecartEntity cart && rail.equals(railAt(world,cart))) {
                stop(t);break;
            }
        }
    }
    public void tick(ServerWorld world) {
        tick++;serviceTick++;held.clear();occupied.clear();
        if(tick%1200==0)markDirty();
        for (Train t : trains.values()) for (UUID id : t.carts) {
            Entity e = world.getEntity(id);
            if (e != null) {
                lastSeen.put(id,e.getBlockPos());missingTicks.remove(id);
                BlockPos p = e.getBlockPos(); held.putIfAbsent(p, t.id); held.putIfAbsent(p.down(), t.id);
                occupied.add(p); occupied.add(p.down());
            }
        }
        for (Train t : trains.values()) if (t.status == Status.RUNNING || t.status == Status.WAITING_FOR_TRACK) {
            try { move(world, t); } catch (RuntimeException ex) { stop(t); }
        }
        for(Train t:trains.values())if(t.status==Status.WAITING_FOR_DEPARTURE && serviceTick>=t.nextDepartureTick) {
            if(!start(world,t)){t.status=Status.ROUTE_LOST;markDirty();}
        }
        cleanupMissing(world);
        if(tick%5==0) {
            Set<BlockPos> current=new HashSet<>();
            for(BlockPos rail:held.keySet()) {
                ControllerEntity c=RailPath.controller(world,rail);
                if(c!=null)current.add(c.getPos());
            }
            Set<BlockPos> dirty=new HashSet<>(current);dirty.addAll(signaled);
            for(BlockPos pos:dirty)if(world.isChunkLoaded(pos))world.updateComparators(pos,RailNet.CONTROLLER);
            signaled.clear();signaled.addAll(current);
        }
        // Entity lookup is by UUID only; no full-world entity or rail scan.
        if (tick % 200 == 0 && trains.values().removeIf(t -> t.carts.isEmpty())) markDirty();
    }
    private void cleanupMissing(ServerWorld world) {
        for(Train t:new ArrayList<>(trains.values())) {
            for(UUID id:new ArrayList<>(t.carts)) {
                BlockPos last=lastSeen.get(id);
                if(world.getEntity(id)!=null || last==null || !world.isChunkLoaded(last)) {
                    missingTicks.remove(id);continue;
                }
                int count=missingTicks.merge(id,1,Integer::sum);
                if(count<100)continue;
                t.carts.remove(id);cartToTrain.remove(id);lastSeen.remove(id);missingTicks.remove(id);
                stop(t);
            }
            if(t.carts.isEmpty()){trains.remove(t.id);markDirty();}
        }
    }
    /** Comparator level: 15 when occupied, 8 when reserved, 0 when free. */
    public int signal(ServerWorld world, BlockPos controller) {
        BlockPos rail = RailPath.nearby(world, controller);
        if (rail == null) return 0;
        if (occupied.contains(rail)) return 15;
        return held.containsKey(rail) ? 8 : 0;
    }
    private void move(ServerWorld world, Train t) {
        if (t.path.size() < 2) { stop(t); return; }
        for (UUID id : t.carts) if (!(world.getEntity(id) instanceof AbstractMinecartEntity)) { t.status = Status.WAITING_FOR_TRACK; t.speed = 0; return; }
        int current = Math.min(t.index, t.path.size()-2);
        double horizon = 4 + t.speed*t.speed / 0.04;
        double ahead = 0;
        for (int i = current; i < t.path.size() && ahead <= horizon; i++) {
            BlockPos p = t.path.get(i);
            if (!RailPath.rail(world, p) || (held.containsKey(p) && !held.get(p).equals(t.id))) {
                t.speed = Math.max(0, t.speed - 0.03);
                t.status = Status.WAITING_FOR_TRACK; return;
            }
            // Claim the look-ahead before another moving train is considered.
            held.put(p, t.id);
            if (i > current) ahead += Math.sqrt(p.getSquaredDistance(t.path.get(i-1)));
        }
        for (int i = Math.max(1, current); i < Math.min(t.path.size()-1, current+4); i++) {
            BlockPos prev = t.path.get(i-1), p = t.path.get(i), next = t.path.get(i+1);
            if (!RailPath.switchJunction(world, p, RailPath.between(p, prev), RailPath.between(p, next))) {
                t.speed = 0; t.status = Status.WAITING_FOR_TRACK; return;
            }
        }
        t.status = Status.RUNNING;
        double max = new double[]{0.08, 0.14, 0.20}[t.preset];
        t.speed = Math.min(max, t.speed + 0.008);
        double total = t.distances[t.distances.length-1];
        t.progress = Math.min(total, t.progress + t.speed);
        while (t.index + 1 < t.path.size() && t.distances[t.index+1] <= t.progress + 1.0e-6) t.index++;
        for (int i = 0; i < t.carts.size(); i++) {
            AbstractMinecartEntity cart = (AbstractMinecartEntity) world.getEntity(t.carts.get(i));
            Vec3d position = sample(t.path, t.distances, Math.max(0, t.progress - i*1.15));
            cart.setVelocity(Vec3d.ZERO);
            cart.refreshPositionAndAngles(position.x, position.y, position.z, cart.getYaw(), 0);
            cart.velocityModified = true;
        }
        if(t.progress>=total-1.0e-5) {
            t.status=Status.ARRIVED;t.speed=0;t.path=List.of();t.distances=new double[0];
            arrived(world,t);markDirty();
        }
    }
    private void arrived(ServerWorld world,Train t) {
        Line line=t.lineId==null?null:lines.get(t.lineId);
        if(line==null||line.stops.isEmpty())return;
        int at=-1;
        if(t.lineIndex>=0 && t.lineIndex<line.stops.size()) {
            int expected=t.lineIndex+t.lineDirection;
            if(expected<0 && line.mode==LineMode.LOOP)expected=line.stops.size()-1;
            if(expected>=line.stops.size() && line.mode==LineMode.LOOP)expected=0;
            if((expected<0||expected>=line.stops.size()) && line.mode==LineMode.SHUTTLE)
                expected=t.lineIndex-t.lineDirection;
            if(expected>=0 && expected<line.stops.size()
                &&line.stops.get(expected).stationId.equals(t.destination))at=expected;
        }
        if(at<0)for(int i=0;i<line.stops.size();i++)if(line.stops.get(i).stationId.equals(t.destination)){at=i;break;}
        if(at<0){t.status=Status.ROUTE_LOST;return;}
        t.lineIndex=at;
        int next=at+t.lineDirection;
        if(next<0||next>=line.stops.size()) {
            if(line.mode==LineMode.STOP||line.stops.size()<2)return;
            if(line.mode==LineMode.LOOP)next=next<0?line.stops.size()-1:0;
            else {t.lineDirection*=-1;next=at+t.lineDirection;}
        }
        if(!stations.containsKey(line.stops.get(next).stationId)){t.status=Status.ROUTE_LOST;return;}
        int dwell=line.stops.get(at).dwellOverride;
        if(dwell<0) {
            Station station=stations.get(line.stops.get(at).stationId);
            if(station!=null && world.isChunkLoaded(station.controller)
                && world.getBlockEntity(station.controller) instanceof ControllerEntity c)dwell=c.stopTicks;
            else dwell=200;
        }
        long ready=serviceTick+Math.max(0,dwell);
        int explicit=line.stops.get(at).departureTick;
        if(explicit>=0) {
            long cycle=24000;ready=((ready/cycle)*cycle)+explicit;
            if(ready<serviceTick+Math.max(0,dwell))ready+=cycle;
        } else if(line.intervalTicks>0) {
            ready=((ready+line.intervalTicks-1)/line.intervalTicks)*line.intervalTicks;
        }
        t.destination=line.stops.get(next).stationId;t.nextDepartureTick=ready;
        t.status=Status.WAITING_FOR_DEPARTURE;
    }
    private static Vec3d sample(List<BlockPos> path, double[] distances, double progress) {
        int lo = 1, hi = distances.length-1;
        while (lo < hi) { int mid = (lo+hi) >>> 1; if (distances[mid] < progress) lo = mid+1; else hi = mid; }
        int i = lo;
        BlockPos a = path.get(i-1), b = path.get(i);
        double len = distances[i] - distances[i-1];
        if (len > 1.0e-6) {
            double f = Math.max(0, Math.min(1, (progress-distances[i-1])/len));
            return new Vec3d(a.getX()+.5+(b.getX()-a.getX())*f, a.getY()+.0625+(b.getY()-a.getY())*f,
                a.getZ()+.5+(b.getZ()-a.getZ())*f);
        }
        BlockPos last = path.get(path.size()-1); return Vec3d.ofBottomCenter(last).add(0, .0625, 0);
    }
}
