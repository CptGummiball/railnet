package dev.cptgummiball.railnet;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Server-side controls using Vanilla container and anvil screens. */
public final class RailScreens {
    private RailScreens() {}
    private static boolean hasTool(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(RailNet.TOOL)
            || player.getOffHandStack().isOf(RailNet.TOOL);
    }
    private static final ConcurrentLinkedQueue<Runnable> PENDING=new ConcurrentLinkedQueue<>();
    public static void finishClicks() {
        Runnable action;
        while((action=PENDING.poll())!=null)action.run();
    }
    public static final class Menu extends GenericContainerScreenHandler {
        private final SimpleInventory icons;
        private final Map<Integer,Runnable> actions=new HashMap<>();
        private final ServerPlayerEntity owner;
        private final BooleanSupplier valid;
        Menu(int sync,PlayerInventory player,ServerPlayerEntity owner,BooleanSupplier valid) {
            this(sync,player,owner,valid,new SimpleInventory(54));
        }
        private Menu(int sync,PlayerInventory player,ServerPlayerEntity owner,BooleanSupplier valid,SimpleInventory inventory) {
            super(ScreenHandlerType.GENERIC_9X6,sync,player,inventory,6);
            icons=inventory;this.owner=owner;this.valid=valid;
        }
        public void icon(int slot,Item item,Text label,Runnable action) {
            ItemStack stack=new ItemStack(item);stack.set(DataComponentTypes.CUSTOM_NAME,label);
            icons.setStack(slot,stack);actions.put(slot,action);
        }
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity player) {
            if(player!=owner||!canUse(player)||slot<0||slot>=54||type!=SlotActionType.PICKUP)return;
            Runnable action=actions.get(slot);
            if(action!=null) {
                owner.closeHandledScreen();
                PENDING.add(()->{if(owner.isAlive()&&valid.getAsBoolean())action.run();});
            }
        }
        @Override public ItemStack quickMove(PlayerEntity player,int index){return ItemStack.EMPTY;}
        @Override public boolean canUse(PlayerEntity player){return player==owner&&valid.getAsBoolean();}
        @Override public void onClosed(PlayerEntity player){/* All icons are virtual. */}
    }
    static void open(ServerPlayerEntity player,Text title,BooleanSupplier valid,Consumer<Menu> fill) {
        if(!valid.getAsBoolean())return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((id,inventory,ignored)->{
            Menu menu=new Menu(id,inventory,player,valid);fill.accept(menu);return menu;
        },title));
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
        Entity lead=p.getServerWorld().getEntity(t.carts.getFirst());
        return lead instanceof AbstractMinecartEntity&&near(p,lead);
    }
    static boolean controllerValid(ServerPlayerEntity p,ControllerEntity c) {
        return hasTool(p)&&near(p,c.getPos())&&p.getServerWorld().getBlockEntity(c.getPos())==c;
    }
    public static void openCart(ServerPlayerEntity player,AbstractMinecartEntity cart) {
        if(!hasTool(player)||!near(player,cart))return;
        TrainData data=TrainData.get(player.getServerWorld());
        TrainData.Train train=data.byCart(cart.getUuid());
        if(train!=null){openTrain(player,train);return;}
        open(player,Text.translatable("railnet.gui.cart"),()->hasTool(player)&&near(player,cart)
            &&data.byCart(cart.getUuid())==null,menu->{
            menu.icon(22,Items.MINECART,Text.translatable("railnet.gui.create_train"),()->{
                data.ensure(cart);openCart(player,cart);
            });
            menu.icon(31,Items.CHAIN,Text.translatable("railnet.gui.coupling_tip"),()->openCart(player,cart));
        });
    }
    public static void openTrain(ServerPlayerEntity player,TrainData.Train train) {
        TrainData data=TrainData.get(player.getServerWorld());
        BooleanSupplier valid=()->trainValid(player,train);
        open(player,Text.translatable("railnet.gui.train",train.name,
            Text.translatable("railnet.gui.status."+train.status.name().toLowerCase())),valid,menu->{
            menu.icon(10,Items.NAME_TAG,Text.translatable("railnet.gui.rename",train.name),()->rename(player,train.name,valid,
                name->{train.name=name;data.markDirty();}));
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
            menu.icon(49,Items.CHAIN,Text.translatable("railnet.gui.carts",train.carts.size()),()->carts(player,train));
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
                name->{c.stationName=name;c.markDirty();data.register(c);}));
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
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inventory,ignored)->{
            NameHandler handler=new NameHandler(sync,inventory,player,valid,onChange);
            ItemStack input=new ItemStack(Items.NAME_TAG);
            input.set(DataComponentTypes.CUSTOM_NAME,Text.literal(value));
            handler.getSlot(0).setStack(input);return handler;
        },Text.translatable("railnet.gui.rename_title")));
    }
    private static final class NameHandler extends AnvilScreenHandler {
        private final ServerPlayerEntity owner;
        private final BooleanSupplier valid;
        private final Consumer<String> onChange;
        NameHandler(int sync,PlayerInventory inventory,ServerPlayerEntity owner,BooleanSupplier valid,Consumer<String> onChange) {
            super(sync,inventory);this.owner=owner;this.valid=valid;this.onChange=onChange;
        }
        @Override public boolean canUse(PlayerEntity player){return player==owner&&valid.getAsBoolean();}
        @Override public boolean setNewItemName(String name) {
            if(valid.getAsBoolean()&&name!=null&&!name.isBlank()&&name.length()<=40)onChange.accept(name);
            return super.setNewItemName(name);
        }
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity player){/* Virtual input. */}
        @Override public ItemStack quickMove(PlayerEntity player,int index){return ItemStack.EMPTY;}
        @Override public void onClosed(PlayerEntity player){/* Virtual input is not dropped. */}
    }
}
