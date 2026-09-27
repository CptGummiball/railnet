package dev.cptgummiball.railnet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Server-owned GUI sessions with short lived action maps; the client renders a real Screen. */
public final class RailScreens {
    private RailScreens() {}
    private static boolean hasTool(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(RailNet.TOOL)
            || player.getOffHandStack().isOf(RailNet.TOOL);
    }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,UUID> COUPLING=new HashMap<>();
    private static long nextSession;
    private static final class Session {
        final long id;
        final BooleanSupplier valid;
        final Map<Integer,Runnable> actions;
        final Consumer<String> rename;
        long expires;
        Session(long id,BooleanSupplier valid,Map<Integer,Runnable> actions,Consumer<String> rename,long expires) {
            this.id=id;this.valid=valid;this.actions=actions;this.rename=rename;this.expires=expires;
        }
    }
    public static void disconnect(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUuid());COUPLING.remove(player.getUuid());
    }
    public static void accept(ServerPlayerEntity player,RailGuiPackets.Action packet) {
        Session session=SESSIONS.get(player.getUuid());
        if(session==null||session.id!=packet.session())return;
        if(packet.slot()==-2) { SESSIONS.remove(player.getUuid());return; }
        if(!player.isAlive()||player.getServerWorld().getTime()>session.expires||!session.valid.getAsBoolean()) {
            SESSIONS.remove(player.getUuid());
            ServerPlayNetworking.send(player,new RailGuiPackets.State(session.id,2,Text.empty(),"",List.of()));
            return;
        }
        if(packet.slot()==-1) {
            if(session.rename==null||packet.value()==null||packet.value().isBlank()||packet.value().length()>40)return;
            SESSIONS.remove(player.getUuid());
            session.rename.accept(packet.value().trim());
            return;
        }
        Runnable action=session.actions.get(packet.slot());
        if(action==null||session.rename!=null)return;
        SESSIONS.remove(player.getUuid());
        action.run();
    }
    public static final class Menu {
        private final Map<Integer,Runnable> actions=new HashMap<>();
        private final List<RailGuiPackets.Entry> entries=new ArrayList<>();
        public void icon(int slot,Item item,Text label,Runnable action) {
            if(slot<0||slot>=54||action==null)throw new IllegalArgumentException("Invalid GUI slot");
            entries.add(new RailGuiPackets.Entry(slot,Registries.ITEM.getId(item).toString(),label));
            actions.put(slot,action);
        }
    }
    static void open(ServerPlayerEntity player,Text title,BooleanSupplier valid,Consumer<Menu> fill) {
        if(!valid.getAsBoolean())return;
        Menu menu=new Menu();fill.accept(menu);
        long session=++nextSession;
        SESSIONS.put(player.getUuid(),new Session(session,valid,menu.actions,null,player.getServerWorld().getTime()+1200));
        ServerPlayNetworking.send(player,new RailGuiPackets.State(session,0,title,"",List.copyOf(menu.entries)));
    }
    private static boolean near(ServerPlayerEntity p,Entity e) {
        return e.isAlive()&&e.getWorld()==p.getWorld()&&p.squaredDistanceTo(e)<=100;
    }
    private static boolean near(ServerPlayerEntity p,BlockPos pos) {
        return p.getServerWorld().isChunkLoaded(pos)
            &&p.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=100;
    }
    static boolean trainValid(ServerPlayerEntity p,TrainData.Train t) {
        if(!hasTool(p)||TrainData.get(p.getServerWorld()).byId(t.id)!=t||t.carts.isEmpty())return false;
        for(UUID id:t.carts) {
            Entity cart=p.getServerWorld().getEntity(id);
            if(cart instanceof AbstractMinecartEntity &&near(p,cart))return true;
        }
        return false;
    }
    static boolean controllerValid(ServerPlayerEntity p,ControllerEntity c) {
        return hasTool(p)&&near(p,c.getPos())&&p.getServerWorld().getBlockEntity(c.getPos())==c;
    }
    public static void openCart(ServerPlayerEntity player,AbstractMinecartEntity cart) {
        if(!hasTool(player)||!near(player,cart))return;
        TrainData data=TrainData.get(player.getServerWorld());
        TrainData.Train train=data.byCart(cart.getUuid());
        if(train!=null){openTrain(player,train,cart);return;}
        open(player,Text.translatable("railnet.gui.cart"),()->hasTool(player)&&near(player,cart)
            &&data.byCart(cart.getUuid())==null,menu->{
            menu.icon(22,Items.MINECART,Text.translatable("railnet.gui.create_train"),()->{
                data.ensure(cart);openCart(player,cart);
            });
            couplingButton(player,cart,menu,31,()->openCart(player,cart));
            if(cart.getUuid().equals(COUPLING.get(player.getUuid())))
                menu.icon(32,Items.RAIL,Text.translatable("railnet.gui.coupling_waiting"),()->openCart(player,cart));
        });
    }
    public static void openTrain(ServerPlayerEntity player,TrainData.Train train) {
        for(UUID id:train.carts) {
            Entity cart=player.getServerWorld().getEntity(id);
            if(cart instanceof AbstractMinecartEntity minecart && near(player,cart)) {
                openTrain(player,train,minecart);return;
            }
        }
    }
    private static void openTrain(ServerPlayerEntity player,TrainData.Train train,AbstractMinecartEntity clicked) {
        TrainData data=TrainData.get(player.getServerWorld());
        BooleanSupplier valid=()->trainValid(player,train);
        open(player,Text.translatable("railnet.gui.train",train.name,
            Text.translatable("railnet.gui.status."+train.status.name().toLowerCase())),valid,menu->{
            menu.icon(10,Items.NAME_TAG,Text.translatable("railnet.gui.rename",train.name),()->rename(player,train.name,valid,
                name->{train.name=name;data.markDirty();openTrain(player,train);}));
            menu.icon(12,Items.COMPASS,Text.translatable("railnet.gui.destination"),()->destinations(player,train,0));
            menu.icon(14,Items.MAP,Text.translatable("railnet.gui.line"),()->LinesScreen.list(player,train,0));
            menu.icon(16,Items.CLOCK,Text.translatable("railnet.gui.schedule"),()->LinesScreen.schedule(player,train));
            menu.icon(28,Items.LIME_DYE,Text.translatable("railnet.gui.start"),()->{
                if(!data.start(player.getServerWorld(),train))player.sendMessage(Text.translatable("railnet.no_route"),true);
                openTrain(player,train);
            });
            menu.icon(30,Items.RED_DYE,Text.translatable("railnet.gui.stop"),()->{data.stop(train);openTrain(player,train);});
            menu.icon(32,Items.ARROW,Text.translatable("railnet.gui.reverse"),()->{
                if(train.status!=TrainData.Status.RUNNING){data.stop(train);java.util.Collections.reverse(train.carts);data.markDirty();}
                openTrain(player,train);
            });
            menu.icon(34,Items.POWERED_RAIL,Text.translatable("railnet.gui.speed",train.preset+1),()->{
                train.preset=(train.preset+1)%3;data.markDirty();openTrain(player,train);
            });
            menu.icon(36,Items.SPYGLASS,Text.translatable("railnet.gui.diagnostics"),()->diagnostics(player,train));
            if(train.status!=TrainData.Status.RUNNING)couplingButton(player,clicked,menu,40,()->openTrain(player,train,clicked));
            if(clicked.getUuid().equals(COUPLING.get(player.getUuid())))
                menu.icon(41,Items.RAIL,Text.translatable("railnet.gui.coupling_waiting"),()->openTrain(player,train,clicked));
            menu.icon(49,Items.CHAIN,Text.translatable("railnet.gui.carts",train.carts.size()),()->carts(player,train));
        });
    }
    private static void couplingButton(ServerPlayerEntity player,AbstractMinecartEntity clicked,Menu menu,int slot,Runnable refresh) {
        UUID first=COUPLING.get(player.getUuid());
        if(first==null)menu.icon(slot,Items.CHAIN,Text.translatable("railnet.gui.coupling_select"),()->{
            COUPLING.put(player.getUuid(),clicked.getUuid());refresh.run();
        });
        else if(first.equals(clicked.getUuid()))
            menu.icon(slot,Items.BARRIER,Text.translatable("railnet.gui.coupling_cancel"),()->{
                COUPLING.remove(player.getUuid());refresh.run();
            });
        else menu.icon(slot,Items.CHAIN,Text.translatable("railnet.gui.coupling_confirm"),()->{
            COUPLING.remove(player.getUuid());
            Entity selected=player.getServerWorld().getEntity(first);
            TrainData data=TrainData.get(player.getServerWorld());
            if(!(selected instanceof AbstractMinecartEntity a)||!a.isAlive()||!clicked.isAlive()
                ||!near(player,clicked)||a.squaredDistanceTo(clicked)>36||!data.couple(a,clicked))
                player.sendMessage(Text.translatable("railnet.coupling_failed"),true);
            openCart(player,clicked);
        });
    }
    private static void diagnostics(ServerPlayerEntity player,TrainData.Train train) {
        TrainData data=TrainData.get(player.getServerWorld());
        open(player,Text.translatable("railnet.gui.diagnostics"),()->trainValid(player,train),menu->{
            TrainData.TextDiagnostic detail=data.diagnose(player.getServerWorld(),train);
            menu.icon(10,Items.MINECART,Text.translatable("railnet.gui.train",train.name,
                Text.translatable("railnet.gui.status."+train.status.name().toLowerCase())),()->diagnostics(player,train));
            TrainData.Station station=train.destination==null?null:data.station(train.destination);
            menu.icon(12,Items.COMPASS,Text.translatable("railnet.gui.diagnostic.target",
                station==null?Text.translatable("railnet.gui.missing_station"):Text.literal(station.name())),()->diagnostics(player,train));
            Text reason=Text.translatable(detail.translationKey());
            if(detail.at()!=null)reason=Text.translatable("railnet.gui.diagnostic.at",reason,
                detail.at().getX(),detail.at().getY(),detail.at().getZ());
            menu.icon(14,Items.REDSTONE_TORCH,reason,()->diagnostics(player,train));
            menu.icon(16,Items.RAIL,Text.translatable("railnet.gui.diagnostic.progress",train.index,train.path.size()),
                ()->diagnostics(player,train));
            menu.icon(18,Items.REDSTONE_TORCH,Text.translatable("railnet.gui.diagnostic.sections",train.sections.size()),
                ()->diagnostics(player,train));
            if(train.status==TrainData.Status.WAITING_FOR_DEPARTURE)
                menu.icon(28,Items.CLOCK,Text.translatable("railnet.gui.diagnostic.seconds",
                    Math.max(0,train.nextDepartureTick-data.serviceTick())/20),()->diagnostics(player,train));
            if(train.status!=TrainData.Status.RUNNING && train.status!=TrainData.Status.WAITING_FOR_DEPARTURE)
                menu.icon(30,Items.LIME_DYE,Text.translatable("railnet.gui.diagnostic.retry"),()->{
                    data.start(player.getServerWorld(),train);diagnostics(player,train);
                });
            menu.icon(49,Items.ARROW,Text.translatable("railnet.gui.back"),()->openTrain(player,train));
        });
    }
    private static void destinations(ServerPlayerEntity player,TrainData.Train train,int page) {
        TrainData data=TrainData.get(player.getServerWorld());
        var options=data.stations().stream().filter(s->player.getServerWorld().isChunkLoaded(s.controller())).toList();
        open(player,Text.translatable("railnet.gui.destination"),()->trainValid(player,train),menu->{
            int from=page*45;
            for(int i=from;i<Math.min(from+45,options.size());i++) {
                var s=options.get(i);
                menu.icon(i-from,Items.COMPASS,Text.literal(s.name()),()->{
                    train.destination=s.id();data.stop(train);data.markDirty();openTrain(player,train);
                });
            }
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->destinations(player,train,page-1));
            if(from+45<options.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->destinations(player,train,page+1));
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->openTrain(player,train));
        });
    }
    private static void carts(ServerPlayerEntity player,TrainData.Train train) {
        TrainData data=TrainData.get(player.getServerWorld());
        open(player,Text.translatable("railnet.gui.carts",train.carts.size()),()->trainValid(player,train),menu->{
            for(int i=0;i<Math.min(train.carts.size(),45);i++) {
                UUID id=train.carts.get(i);
                menu.icon(i,Items.MINECART,Text.translatable("railnet.gui.detach",i+1),()->{
                    data.detach(id);openTrain(player,train);
                });
            }
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->openTrain(player,train));
        });
    }
    public static void controller(ServerPlayerEntity player,ControllerEntity c) {
        if(!controllerValid(player,c))return;
        TrainData data=TrainData.get(player.getServerWorld());data.register(c);
        BooleanSupplier valid=()->controllerValid(player,c);
        open(player,Text.translatable("railnet.gui.controller",Text.translatable("railnet.gui.mode."+c.mode.asString())),valid,menu->{
            menu.icon(10,Items.NAME_TAG,Text.translatable("railnet.gui.rename",c.stationName),()->rename(player,c.stationName,valid,
                name->{c.stationName=name;c.markDirty();data.register(c);controller(player,c);}));
            if(c.getCachedState().getBlock() instanceof LegacyControllerBlock)
                menu.icon(19,Items.SMITHING_TABLE,Text.translatable("railnet.gui.upgrade_legacy"),()->upgradeLegacy(player,c));
            if(c.mode==ControllerEntity.Mode.JUNCTION)
                menu.icon(29,Items.LEVER,Text.translatable("railnet.gui.lock",c.locked),()->{
                    c.locked=!c.locked;c.markDirty();controller(player,c);
                });
            if(c.mode!=ControllerEntity.Mode.BOARD)
                menu.icon(42,Items.REDSTONE,Text.translatable("railnet.gui.input."+c.inputAction.name().toLowerCase()),()->{
                c.inputAction=ControllerEntity.InputAction.values()[(c.inputAction.ordinal()+1)
                    %ControllerEntity.InputAction.values().length];
                c.lastPowered=player.getServerWorld().isReceivingRedstonePower(c.getPos());
                if(c.inputAction==ControllerEntity.InputAction.LOCK_WHILE_POWERED)c.locked=c.lastPowered;
                c.markDirty();controller(player,c);
            });
            if(c.mode==ControllerEntity.Mode.STATION)menu.icon(30,Items.CLOCK,
                Text.translatable("railnet.gui.dwell",c.stopTicks/20),()->{
                    c.stopTicks=(c.stopTicks+100)%1300;c.markDirty();controller(player,c);
                });
            if(c.mode==ControllerEntity.Mode.BLOCK||c.mode==ControllerEntity.Mode.SIGNAL) {
                int signal=data.signal(player.getServerWorld(),c.getPos());
                String state=signal>=15?"occupied":signal>=8?"reserved":"free";
                menu.icon(30,Items.REDSTONE_TORCH,
                    Text.translatable("railnet.gui.section_state",Text.translatable("railnet.gui.section."+state)),
                    ()->controller(player,c));
            }
            if(c.mode==ControllerEntity.Mode.BOARD) {
                menu.icon(31,Items.COMPASS,Text.translatable("railnet.gui.board_station"),()->boardStations(player,c,0));
                menu.icon(32,Items.BELL,Text.translatable("railnet.gui.call_train"),()->callBoard(player,c,0));
                menu.icon(33,Items.CLOCK,Text.translatable("railnet.gui.refresh_board"),()->controller(player,c));
                if(c.boardStationId!=null) {
                    int index=0;
                    for(TrainData.Train t:data.trains()) {
                        if(!c.boardStationId.equals(t.destination)||index>=9)continue;
                        int slot=36+index++;
                        long wait=t.status==TrainData.Status.WAITING_FOR_DEPARTURE
                            ?Math.max(0,t.nextDepartureTick-data.serviceTick())/20:0;
                        menu.icon(slot,Items.MINECART,Text.translatable("railnet.gui.board_entry",t.name,
                            Text.translatable("railnet.gui.status."+t.status.name().toLowerCase()),wait),()->controller(player,c));
                    }
                }
            }
        });
    }
    private static void upgradeLegacy(ServerPlayerEntity player,ControllerEntity previous) {
        if(!controllerValid(player,previous)
            ||!(previous.getCachedState().getBlock() instanceof LegacyControllerBlock))return;
        var world=player.getServerWorld();var data=TrainData.get(world);
        BlockPos pos=previous.getPos();
        world.setBlockState(pos,RailNet.controllerFor(previous.mode).getDefaultState(),3);
        if(world.getBlockEntity(pos) instanceof ControllerEntity dedicated) {
            dedicated.copySettingsFrom(previous);
            data.removeStation(pos);data.register(dedicated);
            controller(player,dedicated);
        }
    }
    private static void boardStations(ServerPlayerEntity player,ControllerEntity c,int page) {
        TrainData data=TrainData.get(player.getServerWorld());var list=data.stations().stream().toList();
        open(player,Text.translatable("railnet.gui.board_station"),()->controllerValid(player,c),menu->{
            int from=page*45;
            for(int i=from;i<Math.min(from+45,list.size());i++) {
                var station=list.get(i);
                menu.icon(i-from,Items.COMPASS,Text.literal(station.name()),()->{
                    c.boardStationId=station.id();c.markDirty();controller(player,c);
                });
            }
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->boardStations(player,c,page-1));
            if(from+45<list.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->boardStations(player,c,page+1));
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->controller(player,c));
        });
    }
    private static void callBoard(ServerPlayerEntity player,ControllerEntity c,int page) {
        TrainData data=TrainData.get(player.getServerWorld());
        var list=data.trains().stream().filter(t->t.status!=TrainData.Status.RUNNING).toList();
        open(player,Text.translatable("railnet.gui.call_train"),()->controllerValid(player,c),menu->{
            int from=page*45;
            for(int i=from;i<Math.min(from+45,list.size());i++) {
                var t=list.get(i);
                menu.icon(i-from,Items.MINECART,Text.literal(t.name),()->{
                    if(data.byId(t.id)==t&&c.boardStationId!=null&&data.station(c.boardStationId)!=null) {
                        t.lineId=null;t.destination=c.boardStationId;data.stop(t);
                        if(!data.start(player.getServerWorld(),t))player.sendMessage(Text.translatable("railnet.no_route"),true);
                    }
                    controller(player,c);
                });
            }
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->callBoard(player,c,page-1));
            if(from+45<list.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->callBoard(player,c,page+1));
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->controller(player,c));
        });
    }
    public static void rename(ServerPlayerEntity player,String value,BooleanSupplier valid,Consumer<String> onChange) {
        if(!valid.getAsBoolean())return;
        long session=++nextSession;
        SESSIONS.put(player.getUuid(),new Session(session,valid,Map.of(),onChange,player.getServerWorld().getTime()+1200));
        ServerPlayNetworking.send(player,new RailGuiPackets.State(session,1,Text.translatable("railnet.gui.rename_title"),
            value.length()>40?value.substring(0,40):value,List.of()));
    }
}
