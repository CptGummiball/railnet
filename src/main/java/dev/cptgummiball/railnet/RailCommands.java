package dev.cptgummiball.railnet;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class RailCommands {
    private RailCommands() {}
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var root = literal("railnet");
            var train = literal("train");
            var id = argument("id", StringArgumentType.word());
            for (String action : new String[]{"start", "stop", "reverse", "speed"}) {
                id.then(literal(action).executes(ctx -> train(ctx.getSource(),
                    StringArgumentType.getString(ctx, "id"), action, null)));
            }
            id.then(literal("destination").then(argument("station", StringArgumentType.word())
                .executes(ctx -> train(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                    "destination", StringArgumentType.getString(ctx, "station")))));
            train.then(id); root.then(train);
            var controller = literal("controller");
            var pos = argument("pos", BlockPosArgumentType.blockPos());
            pos.then(literal("lock").executes(ctx -> controller(ctx.getSource(),
                BlockPosArgumentType.getBlockPos(ctx, "pos"), "lock", null)));
            pos.then(literal("mode").then(argument("mode", StringArgumentType.word()).executes(ctx ->
                controller(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"),
                    "mode", StringArgumentType.getString(ctx, "mode")))));
            pos.then(literal("name").then(argument("name", StringArgumentType.greedyString()).executes(ctx ->
                controller(ctx.getSource(), BlockPosArgumentType.getBlockPos(ctx, "pos"),
                    "name", StringArgumentType.getString(ctx, "name")))));
            controller.then(pos); root.then(controller);
            root.then(literal("list").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
                for (TrainData.Train t : TrainData.get(ctx.getSource().getWorld()).trains())
                    ctx.getSource().sendFeedback(() -> Text.literal(t.id+" "+t.name+" "+t.status), false);
                return 1;
            }));
            dispatcher.register(root);
        });
    }
    private static int train(ServerCommandSource source, String id, String action, String value) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        ServerWorld world = player.getServerWorld(); TrainData data = TrainData.get(world);
        try {
            TrainData.Train t = data.byId(UUID.fromString(id));
            if (t == null || t.carts.isEmpty()) return 0;
            Entity lead = world.getEntity(t.carts.get(0));
            if (!(lead instanceof AbstractMinecartEntity) || (!player.hasPermissionLevel(2) && lead.squaredDistanceTo(player) > 100)) return 0;
            boolean okay = true;
            switch (action) {
                case "start" -> okay = data.start(world, t);
                case "stop" -> data.stop(t);
                case "destination" -> {
                    UUID station = UUID.fromString(value);
                    if (data.station(station) == null) return 0;
                    t.destination = station; data.stop(t); data.markDirty();
                }
                case "reverse" -> {
                    data.stop(t); java.util.Collections.reverse(t.carts); data.markDirty();
                }
                case "speed" -> { t.preset = (t.preset+1)%3; data.markDirty(); }
            }
            player.sendMessage(Text.translatable(okay ? "railnet.updated" : "railnet.no_route"), true);
            Menus.train(player, t); return okay ? 1 : 0;
        } catch (IllegalArgumentException ex) { return 0; }
    }
    private static int controller(ServerCommandSource source, BlockPos pos, String action, String value) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(pos) || (!player.hasPermissionLevel(2) && player.squaredDistanceTo(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5) > 100)
            || !(world.getBlockEntity(pos) instanceof ControllerEntity c)) return 0;
        try {
            switch (action) {
                case "lock" -> c.locked = !c.locked;
                case "mode" -> c.mode = ControllerEntity.Mode.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                case "name" -> { if (value.length() > 40 || value.isBlank()) return 0; c.stationName = value; }
            }
        } catch (IllegalArgumentException ex) { return 0; }
        c.markDirty(); TrainData.get(world).register(c); Menus.controller(player, c); return 1;
    }
}
