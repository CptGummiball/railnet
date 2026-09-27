package dev.cptgummiball.railnet;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RailNet implements ModInitializer {
    public static final String ID = "railnet";
    /** Legacy ID is retained for blocks and items already saved in older worlds. */
    public static final Block CONTROLLER = new LegacyControllerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2));
    public static final Block STATION_CONTROLLER = controller(ControllerEntity.Mode.STATION);
    public static final Block JUNCTION_CONTROLLER = controller(ControllerEntity.Mode.JUNCTION);
    public static final Block BLOCK_CONTROLLER = controller(ControllerEntity.Mode.BLOCK);
    public static final Block DEPOT_CONTROLLER = controller(ControllerEntity.Mode.DEPOT);
    public static final Block SIGNAL_CONTROLLER = controller(ControllerEntity.Mode.SIGNAL);
    public static final Block TIMETABLE_BOARD = controller(ControllerEntity.Mode.BOARD);
    public static final Block CROSSING = new CrossingBlock(AbstractBlock.Settings.copy(Blocks.RAIL));
    public static final Item TOOL = new Item(new Item.Settings().maxCount(1));
    public static BlockEntityType<ControllerEntity> CONTROLLER_ENTITY;
    private static final Map<UUID, UUID> FIRST_CART = new HashMap<>();
    private static Block controller(ControllerEntity.Mode mode) {
        return new ControllerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2),mode);
    }
    public static Block controllerFor(ControllerEntity.Mode mode) {
        return switch(mode) {
            case STATION -> STATION_CONTROLLER;
            case JUNCTION -> JUNCTION_CONTROLLER;
            case BLOCK -> BLOCK_CONTROLLER;
            case DEPOT -> DEPOT_CONTROLLER;
            case SIGNAL -> SIGNAL_CONTROLLER;
            case BOARD -> TIMETABLE_BOARD;
        };
    }

    @Override public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(RailGuiPackets.State.ID,RailGuiPackets.State.CODEC);
        PayloadTypeRegistry.playC2S().register(RailGuiPackets.Action.ID,RailGuiPackets.Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RailGuiPackets.Action.ID,(payload,context)->
            context.server().execute(()->RailScreens.accept(context.player(),payload)));
        register("rail_controller", CONTROLLER);
        register("station_controller", STATION_CONTROLLER);
        register("junction_controller", JUNCTION_CONTROLLER);
        register("block_controller", BLOCK_CONTROLLER);
        register("depot_controller", DEPOT_CONTROLLER);
        register("signal_controller", SIGNAL_CONTROLLER);
        register("timetable_board", TIMETABLE_BOARD);
        register("crossing_rail", CROSSING);
        Registry.register(Registries.ITEM,id("railnet_tool"),TOOL);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> {
            entries.add(STATION_CONTROLLER);entries.add(JUNCTION_CONTROLLER);
            entries.add(BLOCK_CONTROLLER);entries.add(DEPOT_CONTROLLER);
            entries.add(SIGNAL_CONTROLLER);entries.add(TIMETABLE_BOARD);entries.add(CROSSING);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries->entries.add(TOOL));
        CONTROLLER_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("rail_controller"),
            FabricBlockEntityTypeBuilder.create(ControllerEntity::new, CONTROLLER, STATION_CONTROLLER,
                JUNCTION_CONTROLLER, BLOCK_CONTROLLER, DEPOT_CONTROLLER, SIGNAL_CONTROLLER, TIMETABLE_BOARD).build());
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(entity instanceof AbstractMinecartEntity cart)) return ActionResult.PASS;
            Hand used=hand;
            if(hand==Hand.MAIN_HAND && !player.getStackInHand(hand).isOf(Items.CHAIN)
                && !player.getStackInHand(hand).isOf(TOOL)
                && (player.getOffHandStack().isOf(TOOL)||player.getOffHandStack().isOf(Items.CHAIN)))
                used=Hand.OFF_HAND;
            if (world.isClient && (player.getStackInHand(used).isOf(TOOL)
                || player.getStackInHand(used).isOf(Items.CHAIN))) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ServerWorld sw = (ServerWorld) world;
            if(player.getStackInHand(used).isOf(TOOL)) {
                RailScreens.openCart(sp,cart);
                return ActionResult.SUCCESS;
            }
            TrainData data = TrainData.get(sw);
            if (player.getStackInHand(used).isOf(Items.CHAIN)) {
                if (player.isSneaking()) {
                    if (data.detach(cart.getUuid())) sp.sendMessage(Text.translatable("railnet.detached"), true);
                    else sp.sendMessage(Text.translatable("railnet.not_coupled"), true);
                } else {
                    UUID first = FIRST_CART.remove(player.getUuid());
                    if (first == null || first.equals(cart.getUuid())) {
                        FIRST_CART.put(player.getUuid(), cart.getUuid());
                        sp.sendMessage(Text.translatable("railnet.first_cart"), true);
                    } else {
                        var other = sw.getEntity(first);
                        if (!(other instanceof AbstractMinecartEntity a) || !a.isAlive() || a.squaredDistanceTo(cart) > 36
                            || !data.couple(a, cart)) sp.sendMessage(Text.translatable("railnet.coupling_failed"), true);
                        else {
                            if (!sp.isCreative()) player.getStackInHand(used).decrement(1);
                            sp.sendMessage(Text.translatable("railnet.coupled"), true);
                        }
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if(!player.getMainHandStack().isOf(TOOL)
                &&!player.getOffHandStack().isOf(TOOL))return ActionResult.PASS;
            ControllerEntity controller=world.getBlockEntity(hit.getBlockPos()) instanceof ControllerEntity c ? c : null;
            if(controller==null && world.getBlockState(hit.getBlockPos()).getBlock() instanceof AbstractRailBlock)
                controller=RailPath.controller(world,hit.getBlockPos());
            if(controller!=null) {
                if(player instanceof ServerPlayerEntity sp)RailScreens.controller(sp,controller);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> TrainData.get(world).tick(world));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            FIRST_CART.remove(handler.player.getUuid());RailScreens.disconnect(handler.player);
        });
        RailCommands.register();
    }
    private static void register(String name, Block block) {
        Registry.register(Registries.BLOCK, id(name), block);
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
    }
    public static Identifier id(String path) { return Identifier.of(ID, path); }
}
