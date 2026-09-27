package dev.cptgummiball.railnet.client;

import dev.cptgummiball.railnet.RailGuiPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standalone client screen, independent of the player's inventory. */
public final class RailNetScreen extends Screen {
    private final RailGuiPackets.State state;
    private final List<RailGuiPackets.Entry> sorted;
    private final List<Icon> icons=new ArrayList<>();
    private TextFieldWidget input;
    private int page;
    private int rows;
    private int left,top,panelWidth,panelHeight;
    private boolean drawingContents;

    private record Icon(ItemStack stack,int x,int y) {}

    public RailNetScreen(RailGuiPackets.State state) {
        super(state.title());
        this.state=state;
        this.sorted=state.entries().stream().sorted(Comparator.comparingInt(RailGuiPackets.Entry::slot)).toList();
    }
    public long session(){return state.session();}

    @Override protected void init() {
        panelWidth=Math.min(430,width-16);
        panelHeight=Math.min(292,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        rows=Math.max(1,(panelHeight-90)/26);
        if(state.kind()==1) {
            input=new TextFieldWidget(textRenderer,left+16,top+72,panelWidth-32,20,Text.translatable("railnet.gui.name_field"));
            input.setMaxLength(40);input.setText(state.value());
            addDrawableChild(input);setInitialFocus(input);
            addDrawableChild(ButtonWidget.builder(Text.translatable("railnet.gui.save"),button->save())
                .dimensions(left+16,top+108,(panelWidth-40)/2,20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("railnet.gui.cancel"),button->close())
                .dimensions(left+24+(panelWidth-40)/2,top+108,(panelWidth-40)/2,20).build());
            return;
        }
        renderPage();
    }

    private void renderPage() {
        clearChildren();icons.clear();
        int perPage=rows*2;
        int pageCount=Math.max(1,(sorted.size()+perPage-1)/perPage);
        page=Math.max(0,Math.min(page,pageCount-1));
        int gutter=8,margin=12;
        int buttonWidth=(panelWidth-2*margin-gutter)/2;
        for(int i=page*perPage;i<Math.min(sorted.size(),(page+1)*perPage);i++) {
            RailGuiPackets.Entry entry=sorted.get(i);
            int column=(i-page*perPage)%2,row=(i-page*perPage)/2;
            int x=left+margin+column*(buttonWidth+gutter),y=top+42+row*26;
            addDrawableChild(ButtonWidget.builder(entry.label(),button->click(entry.slot()))
                .dimensions(x,y,buttonWidth,22).build());
            Identifier id=Identifier.tryParse(entry.icon());
            if(id!=null && Registries.ITEM.containsId(id))icons.add(new Icon(new ItemStack(Registries.ITEM.get(id)),x+3,y+3));
        }
        int footer=top+panelHeight-27;
        if(page>0)addDrawableChild(ButtonWidget.builder(Text.translatable("railnet.gui.scroll_previous"),button->{page--;renderPage();})
            .dimensions(left+margin,footer,90,20).build());
        if(page+1<pageCount)addDrawableChild(ButtonWidget.builder(Text.translatable("railnet.gui.scroll_next"),button->{page++;renderPage();})
            .dimensions(left+panelWidth-margin-90,footer,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("railnet.gui.close"),button->close())
            .dimensions(left+(panelWidth-70)/2,footer,70,20).build());
    }
    private void click(int slot) {
        ClientPlayNetworking.send(new RailGuiPackets.Action(state.session(),slot,""));
    }
    private void save() {
        if(input!=null && !input.getText().isBlank())
            ClientPlayNetworking.send(new RailGuiPackets.Action(state.session(),-1,input.getText()));
    }
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(state.kind()==1 && (keyCode==GLFW.GLFW_KEY_ENTER||keyCode==GLFW.GLFW_KEY_KP_ENTER)) {
            save();return true;
        }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontalAmount,double verticalAmount) {
        if(state.kind()==0 && sorted.size()>rows*2 && verticalAmount!=0) {
            page=Math.max(0,Math.min((sorted.size()-1)/(rows*2),page+(verticalAmount<0?1:-1)));
            renderPage();return true;
        }
        return super.mouseScrolled(mouseX,mouseY,horizontalAmount,verticalAmount);
    }
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) {
        // Screen.render() calls this again when rendering the widgets. Blurring a
        // second time would include the panel and its text in the framebuffer.
        if(!drawingContents)super.renderBackground(context,mouseX,mouseY,delta);
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        renderBackground(context,mouseX,mouseY,delta);
        drawingContents=true;
        try {
            context.fill(left-2,top-2,left+panelWidth+2,top+panelHeight+2,0xff476b80);
            context.fill(left,top,left+panelWidth,top+panelHeight,0xf01a2733);
            context.fill(left,top,left+panelWidth,top+30,0xff254354);
            context.drawTextWithShadow(textRenderer,title,left+12,top+11,0xfff2f7f8);
            if(state.kind()==1)context.drawTextWithShadow(textRenderer,
                Text.translatable("railnet.gui.name_field"),left+16,top+54,0xffb3d3e0);
            else {
                int perPage=rows*2;
                int total=Math.max(1,(sorted.size()+perPage-1)/perPage);
                context.drawCenteredTextWithShadow(textRenderer,Text.translatable("railnet.gui.page",page+1,total),
                    width/2,top+panelHeight-40,0xffb3d3e0);
            }
            super.render(context,mouseX,mouseY,delta);
            if(state.kind()==0)for(Icon icon:icons)context.drawItem(icon.stack(),icon.x(),icon.y());
        } finally {
            drawingContents=false;
        }
    }
    @Override public void removed() {
        // Closing or switching screens invalidates only this session, never the new one.
        if(client!=null && client.getNetworkHandler()!=null)
            ClientPlayNetworking.send(new RailGuiPackets.Action(state.session(),-2,""));
    }
    @Override public boolean shouldPause(){return false;}
}
