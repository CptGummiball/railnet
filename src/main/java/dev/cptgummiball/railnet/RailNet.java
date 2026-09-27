package dev.cptgummiball.railnet;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RailNet implements ModInitializer {
    public static final String ID = "railnet";
    public static final Block CONTROLLER = new ControllerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2));
    public static final Block CROSSING = new CrossingBlock(AbstractBlock.Settings.copy(Blocks.RAIL));
    public static BlockEntityType<ControllerEntity> CONTROLLER_ENTITY;
    private static final Map<UUID, UUID> FIRST_CART = new HashMap<>();

    @Override public void onInitialize() {
        register("rail_controller", CONTROLLER);
        register("crossing_rail", CROSSING);
        CONTROLLER_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id("rail_controller"),
            FabricBlockEntityTypeBuilder.create(ControllerEntity::new, CONTROLLER).build());
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity sp) || !(entity instanceof AbstractMinecartEntity cart)) return ActionResult.PASS;
            ServerWorld sw = (ServerWorld) world;
            TrainData data = TrainData.get(sw);
            if (player.getStackInHand(hand).isOf(Items.CHAIN)) {
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
                            if (!sp.isCreative()) player.getStackInHand(hand).decrement(1);
                            sp.sendMessage(Text.translatable("railnet.coupled"), true);
                        }
                    }
                }
                return ActionResult.SUCCESS;
            }
            if (player.isSneaking() && player.getStackInHand(hand).isEmpty()) {
                Menus.train(sp, data.ensure(cart));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(hit.getBlockPos()) instanceof ControllerEntity controller) {
                Menus.controller(sp, controller);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_WORLD_TICK.register(world -> TrainData.get(world).tick(world));
        RailCommands.register();
    }
    private static void register(String name, Block block) {
        Registry.register(Registries.BLOCK, id(name), block);
        Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
    }
    public static Identifier id(String path) { return Identifier.of(ID, path); }
}
