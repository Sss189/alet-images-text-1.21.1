package com.docvin.alet.client;

import com.docvin.alet.blueprint.BlueprintEncoder;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OptionsScreen extends Screen {
    private final Screen parent;
    private final ToolSettings settings=ToolSettings.INSTANCE;
    private int left,top,panelWidth;
    public OptionsScreen(Screen parent) { super(Component.translatable("alet.options")); this.parent=parent; }
    private void field(int x,int y,int width,String value,String key,Consumer<String> changed) {
        var box=new EditBox(font,x,y,width,20,Component.translatable(key));
        box.setValue(value); box.setResponder(changed); addRenderableWidget(box);
        box.setTooltip(Tooltip.create(Component.translatable(key+"_hint")));
    }
    private Button button(int x,int y,int width,String key,Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.translatable(key),action).bounds(x,y,width,20).build());
    }
    @Override protected void init() {
        panelWidth=Math.min(360,width-28); left=(width-panelWidth)/2; top=Math.max(5,(height-188)/2);
        int third=(panelWidth-12)/3;
        field(left,top+41,third,settings.thickness,"alet.thickness",v -> settings.thickness=v);
        field(left+third+6,top+41,third,settings.colorStep,"alet.color_step",v -> settings.colorStep=v);
        field(left+2*(third+6),top+41,panelWidth-2*(third+6),settings.rotation,"alet.rotation",v -> settings.rotation=v);
        button(left,top+76,third,"alet.plane."+settings.plane,b -> {
            var planes=BlueprintEncoder.Plane.values(); settings.plane=planes[(settings.plane.ordinal()+1)%planes.length]; b.setMessage(Component.translatable("alet.plane."+settings.plane));
        });
        button(left+third+6,top+76,third,settings.ignoreAlpha?"alet.alpha_opaque":"alet.alpha_keep",b -> {
            settings.ignoreAlpha=!settings.ignoreAlpha; b.setMessage(Component.translatable(settings.ignoreAlpha?"alet.alpha_opaque":"alet.alpha_keep"));
        });
        button(left+2*(third+6),top+76,panelWidth-2*(third+6),settings.mirror?"alet.mirror_on":"alet.mirror_off",b -> {
            settings.mirror=!settings.mirror; b.setMessage(Component.translatable(settings.mirror?"alet.mirror_on":"alet.mirror_off"));
        });
        button(left,top+108,panelWidth,settings.antialias?"alet.antialias_on":"alet.antialias_off",b -> {
            settings.antialias=!settings.antialias; b.setMessage(Component.translatable(settings.antialias?"alet.antialias_on":"alet.antialias_off"));
        });
        button(left,top+159,panelWidth,"gui.done",b -> onClose());
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTick) {
        renderTransparentBackground(graphics); graphics.fill(left-8,top-4,left+panelWidth+8,top+188,0xEE202020);
        super.render(graphics,x,y,partialTick); graphics.drawString(font,title,left,top+5,0xFFFFFF);
        int third=(panelWidth-12)/3;
        graphics.drawString(font,Component.translatable("alet.thickness"),left,top+29,0xDDDDDD);
        graphics.drawString(font,Component.translatable("alet.color_step"),left+third+6,top+29,0xDDDDDD);
        graphics.drawString(font,Component.translatable("alet.rotation"),left+2*(third+6),top+29,0xDDDDDD);
        graphics.drawString(font,Component.translatable("alet.options_hint"),left,top+141,0xAADDFF);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float tick) {}
    @Override public boolean isPauseScreen() { return false; }
}
