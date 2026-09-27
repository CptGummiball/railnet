package dev.cptgummiball.railnet;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** Ordered line and timetable editor, backed by server-validated Vanilla screens. */
public final class LinesScreen {
    private LinesScreen() {}
    public static void list(ServerPlayerEntity player,TrainData.Train train,int page) {
        TrainData data=TrainData.get(player.getServerWorld());
        List<TrainData.Line> lines=data.lines().stream().toList();
        RailScreens.open(player,Text.translatable("railnet.gui.lines"),()->RailScreens.trainValid(player,train),menu->{
            int from=page*45;
            for(int i=from;i<Math.min(from+45,lines.size());i++) {
                TrainData.Line line=lines.get(i);
                menu.icon(i-from,Items.MAP,Text.literal(line.name+" ("+line.stops.size()+")"),()->edit(player,train,line,0));
            }
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->list(player,train,page-1));
            if(from+45<lines.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->list(player,train,page+1));
            menu.icon(46,Items.LIME_DYE,Text.translatable("railnet.gui.new_line"),()->edit(player,train,data.createLine(),0));
            menu.icon(47,Items.BARRIER,Text.translatable("railnet.gui.no_line"),()->{
                data.assign(train,null);RailScreens.openTrain(player,train);
            });
            menu.icon(49,Items.ARROW,Text.translatable("railnet.gui.back"),()->RailScreens.openTrain(player,train));
        });
    }
    public static void schedule(ServerPlayerEntity player,TrainData.Train train) {
        TrainData data=TrainData.get(player.getServerWorld());
        TrainData.Line line=train.lineId==null?null:data.line(train.lineId);
        if(line==null)list(player,train,0);else edit(player,train,line,0);
    }
    private static void edit(ServerPlayerEntity player,TrainData.Train train,TrainData.Line line,int page) {
        TrainData data=TrainData.get(player.getServerWorld());
        RailScreens.open(player,Text.translatable("railnet.gui.line_editor",line.name),
            ()->RailScreens.trainValid(player,train)&&data.line(line.id)==line,menu->{
            int from=page*36;
            for(int i=from;i<Math.min(from+36,line.stops.size());i++) {
                var stop=line.stops.get(i);var station=data.station(stop.stationId);
                Text name=station==null?Text.translatable("railnet.gui.missing_station"):Text.literal(station.name());
                int index=i;
                menu.icon(i-from,Items.RAIL,Text.translatable("railnet.gui.stop_number",i+1,name),()->editStop(player,train,line,index));
            }
            menu.icon(36,Items.NAME_TAG,Text.translatable("railnet.gui.rename",line.name),()->RailScreens.rename(player,line.name,
                ()->RailScreens.trainValid(player,train)&&data.line(line.id)==line,
                name->{line.name=name;data.markDirty();}));
            menu.icon(37,Items.COMPASS,Text.translatable("railnet.gui.add_stop"),()->addStop(player,train,line,0));
            menu.icon(38,Items.MINECART,Text.translatable("railnet.gui.assign_line"),()->{
                if(!line.stops.isEmpty())data.assign(train,line);RailScreens.openTrain(player,train);
            });
            menu.icon(39,Items.CLOCK,Text.translatable("railnet.gui.interval",line.intervalTicks/20),()->{
                line.intervalTicks=line.intervalTicks>=36000?0:line.intervalTicks+1200;
                data.markDirty();edit(player,train,line,page);
            });
            menu.icon(40,Items.REPEATER,Text.translatable("railnet.gui.service_mode."+line.mode.name().toLowerCase()),()->{
                line.mode=TrainData.LineMode.values()[(line.mode.ordinal()+1)%TrainData.LineMode.values().length];
                data.markDirty();edit(player,train,line,page);
            });
            menu.icon(41,Items.RED_DYE,Text.translatable("railnet.gui.delete_line"),()->{
                data.removeLine(line);list(player,train,0);
            });
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->edit(player,train,line,page-1));
            if(from+36<line.stops.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->edit(player,train,line,page+1));
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->list(player,train,0));
        });
    }
    private static void addStop(ServerPlayerEntity player,TrainData.Train train,TrainData.Line line,int page) {
        TrainData data=TrainData.get(player.getServerWorld());var stations=data.stations().stream().toList();
        RailScreens.open(player,Text.translatable("railnet.gui.add_stop"),
            ()->RailScreens.trainValid(player,train)&&data.line(line.id)==line,menu->{
            int from=page*45;
            for(int i=from;i<Math.min(from+45,stations.size());i++) {
                var station=stations.get(i);
                menu.icon(i-from,Items.COMPASS,Text.literal(station.name()),()->{
                    if(data.station(station.id())!=null){line.stops.add(new TrainData.LineStop(station.id()));data.markDirty();}
                    edit(player,train,line,0);
                });
            }
            if(page>0)menu.icon(45,Items.ARROW,Text.translatable("railnet.gui.previous"),()->addStop(player,train,line,page-1));
            if(from+45<stations.size())menu.icon(53,Items.ARROW,Text.translatable("railnet.gui.next"),()->addStop(player,train,line,page+1));
            menu.icon(49,Items.BARRIER,Text.translatable("railnet.gui.back"),()->edit(player,train,line,0));
        });
    }
    private static void editStop(ServerPlayerEntity player,TrainData.Train train,TrainData.Line line,int index) {
        TrainData data=TrainData.get(player.getServerWorld());
        if(data.line(line.id)!=line||index<0||index>=line.stops.size())return;
        TrainData.LineStop stop=line.stops.get(index);
        var station=data.station(stop.stationId);
        Text name=station==null?Text.translatable("railnet.gui.missing_station"):Text.literal(station.name());
        RailScreens.open(player,Text.translatable("railnet.gui.stop_editor",index+1,name),
            ()->RailScreens.trainValid(player,train)&&data.line(line.id)==line
                &&index<line.stops.size()&&line.stops.get(index)==stop,menu->{
            menu.icon(10,Items.CLOCK,Text.translatable("railnet.gui.dwell_override",stop.dwellOverride<0
                ?Text.translatable("railnet.gui.automatic"):Text.translatable("railnet.gui.seconds",stop.dwellOverride/20)),()->{
                stop.dwellOverride=stop.dwellOverride>=600?-1:stop.dwellOverride<0?0:stop.dwellOverride+100;
                data.markDirty();editStop(player,train,line,index);
            });
            menu.icon(11,Items.DAYLIGHT_DETECTOR,Text.translatable("railnet.gui.departure",stop.departureTick<0
                ?Text.translatable("railnet.gui.automatic"):Text.translatable("railnet.gui.seconds",stop.departureTick/20)),()->{
                stop.departureTick=stop.departureTick<0?0:(stop.departureTick+1200)%24000;
                data.markDirty();editStop(player,train,line,index);
            });
            menu.icon(12,Items.RED_DYE,Text.translatable("railnet.gui.departure_off"),()->{
                stop.departureTick=-1;data.markDirty();editStop(player,train,line,index);
            });
            menu.icon(19,Items.ARROW,Text.translatable("railnet.gui.move_up"),()->{
                if(index>0){java.util.Collections.swap(line.stops,index,index-1);data.markDirty();}
                edit(player,train,line,0);
            });
            menu.icon(20,Items.ARROW,Text.translatable("railnet.gui.move_down"),()->{
                if(index+1<line.stops.size()){java.util.Collections.swap(line.stops,index,index+1);data.markDirty();}
                edit(player,train,line,0);
            });
            menu.icon(21,Items.BARRIER,Text.translatable("railnet.gui.remove_stop"),()->{
                line.stops.remove(index);data.markDirty();edit(player,train,line,0);
            });
            menu.icon(49,Items.ARROW,Text.translatable("railnet.gui.back"),()->edit(player,train,line,0));
        });
    }
}
