package dev.cptgummiball.railnet;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Clickable chat menus keep all gameplay authority on the server. */
public final class Menus {
    private Menus() {}
    private static Text button(Text label, String cmd) {
        return label.copy().formatted(Formatting.AQUA).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd)));
    }
    public static void train(ServerPlayerEntity player, TrainData.Train train) {
        ServerWorld world = player.getServerWorld();
        TrainData data = TrainData.get(world);
        player.sendMessage(Text.translatable("railnet.train_menu", train.name, train.status.name(), train.carts.size()), false);
        String base = "/railnet train " + train.id;
        player.sendMessage(Text.empty().append(button(Text.translatable("railnet.start"), base+" start"))
            .append(Text.literal("  ")).append(button(Text.translatable("railnet.stop"), base+" stop"))
            .append(Text.literal("  ")).append(button(Text.translatable("railnet.reverse"), base+" reverse")), false);
        player.sendMessage(Text.translatable("railnet.choose_destination"), false);
        for (TrainData.Station s : data.stations()) {
            if (!world.isChunkLoaded(s.controller()) || !(world.getBlockEntity(s.controller()) instanceof ControllerEntity c)
                || c.mode != ControllerEntity.Mode.STATION || !c.stationId.equals(s.id())) continue;
            player.sendMessage(button(Text.literal(" » "+s.name()), base+" destination "+s.id()), false);
        }
        player.sendMessage(button(Text.translatable("railnet.speed"), base+" speed"), false);
    }
    public static void controller(ServerPlayerEntity player, ControllerEntity c) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        TrainData.get(sw).register(c);
        BlockPos p = c.getPos();
        String base = "/railnet controller "+p.getX()+" "+p.getY()+" "+p.getZ();
        player.sendMessage(Text.translatable("railnet.controller_menu", c.mode.name(), c.stationName), false);
        for (ControllerEntity.Mode m : ControllerEntity.Mode.values()) {
            player.sendMessage(button(Text.literal(" » "+m.name()), base+" mode "+m.name().toLowerCase()), false);
        }
        player.sendMessage(button(Text.translatable("railnet.lock", c.locked), base+" lock"), false);
        player.sendMessage(Text.translatable("railnet.rename_hint", base+" name <text>"), false);
    }
}
