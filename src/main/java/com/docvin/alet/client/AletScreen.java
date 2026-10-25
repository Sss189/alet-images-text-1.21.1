package com.docvin.alet.client;

import com.docvin.alet.raster.RasterException;
import com.docvin.alet.raster.RasterService;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AletScreen extends Screen {
    private final ToolSettings settings=ToolSettings.INSTANCE;
    private int left,top,panelWidth;
    private Button generate,preview,nativeImport,copy,cancel;
    public AletScreen() {
        super(Component.translatable("alet.title")); settings.ensureGrid();
    }
    private EditBox field(int x,int y,int width,String value,String key,Consumer<String> changed) {
        var field=new EditBox(font,x,y,width,20,Component.translatable(key));
        field.setMaxLength(32768); field.setValue(value); field.setResponder(changed);
        addRenderableWidget(field); return field;
    }
    private Button button(int x,int y,int width,Component label,Button.OnPress action) {
        return addRenderableWidget(Button.builder(label,action).bounds(x,y,width,20).build());
    }
    private Component gridLabel() { return Component.translatable("alet.grid_value",settings.grid); }
    private Component styleLabel() { return Component.translatable("alet.style."+settings.style); }
    @Override protected void init() {
        panelWidth=Math.min(400,width-28); left=(width-panelWidth)/2; top=Math.max(4,(height-224)/2);
        int half=(panelWidth-6)/2;
        var imageTab=button(left,top+20,half,Component.translatable("alet.image_tab"),b -> { settings.textMode=false; rebuildWidgets(); });
        var textTab=button(left+half+6,top+20,half,Component.translatable("alet.text_tab"),b -> { settings.textMode=true; rebuildWidgets(); });
        imageTab.active=settings.textMode; textTab.active=!settings.textMode;
        int quarter=(panelWidth-18)/4;
        if (settings.textMode) {
            field(left,top+57,panelWidth,settings.text,"alet.text",v -> settings.text=v)
                .setTooltip(Tooltip.create(Component.translatable("alet.text_hint")));
            int sizeWidth=55,chooseWidth=70,fontWidth=panelWidth-sizeWidth-chooseWidth-12;
            field(left,top+94,fontWidth,settings.family,"alet.font",v -> settings.family=v);
            button(left+fontWidth+6,top+94,chooseWidth,Component.translatable("alet.choose_font"),
                b -> minecraft.setScreen(new FontPickerScreen(this,name -> settings.family=name)));
            field(left+panelWidth-sizeWidth,top+94,sizeWidth,settings.fontSize,"alet.font_size",v -> settings.fontSize=v);
            field(left,top+131,quarter,settings.color,"alet.color",v -> settings.color=v)
                .setTooltip(Tooltip.create(Component.translatable("alet.color_hint")));
            button(left+quarter+6,top+131,quarter,styleLabel(),b -> { settings.style=(settings.style+1)%4; b.setMessage(styleLabel()); });
            button(left+2*(quarter+6),top+131,quarter,gridLabel(),b -> { settings.nextGrid(); b.setMessage(gridLabel()); });
            button(left+3*(quarter+6),top+131,panelWidth-3*(quarter+6),Component.translatable("alet.options"),
                b -> minecraft.setScreen(new OptionsScreen(this)));
        } else {
            field(left,top+57,panelWidth,settings.source,"alet.source",v -> settings.source=v)
                .setTooltip(Tooltip.create(Component.translatable("alet.source_hint")));
            field(left,top+94,quarter,settings.width,"alet.width",v -> settings.width=v);
            field(left+quarter+6,top+94,quarter,settings.height,"alet.height",v -> settings.height=v);
            button(left+2*(quarter+6),top+94,quarter,gridLabel(),b -> { settings.nextGrid(); b.setMessage(gridLabel()); });
            button(left+3*(quarter+6),top+94,panelWidth-3*(quarter+6),Component.translatable("alet.options"),
                b -> minecraft.setScreen(new OptionsScreen(this)));
            int third=(panelWidth-12)/3;
            button(left,top+131,third,Component.translatable(settings.ignoreAlpha?"alet.alpha_opaque":"alet.alpha_keep"),
                b -> { settings.ignoreAlpha=!settings.ignoreAlpha; b.setMessage(Component.translatable(settings.ignoreAlpha?"alet.alpha_opaque":"alet.alpha_keep")); });
            button(left+third+6,top+131,third,Component.translatable(settings.mirror?"alet.mirror_on":"alet.mirror_off"),
                b -> { settings.mirror=!settings.mirror; b.setMessage(Component.translatable(settings.mirror?"alet.mirror_on":"alet.mirror_off")); });
            button(left+2*(third+6),top+131,panelWidth-2*(third+6),Component.translatable("alet.plane."+settings.plane),
                b -> { var planes=com.docvin.alet.blueprint.BlueprintEncoder.Plane.values(); settings.plane=planes[(settings.plane.ordinal()+1)%planes.length]; b.setMessage(Component.translatable("alet.plane."+settings.plane)); });
        }
        int third=(panelWidth-12)/3;
        generate=button(left,top+174,third,Component.translatable("alet.generate"),b -> generate());
        preview=button(left+third+6,top+174,third,Component.translatable("alet.preview"),
            b -> { if (AletJobs.output!=null) minecraft.setScreen(new PreviewScreen(this,AletJobs.output)); });
        nativeImport=button(left+2*(third+6),top+174,panelWidth-2*(third+6),Component.translatable("alet.native_import"),b -> AletJobs.openNativeImport());
        nativeImport.setTooltip(Tooltip.create(Component.translatable("alet.native_help")));
        copy=button(left,top+198,quarter,Component.translatable("alet.copy"),b -> AletJobs.copyBlueprint());
        button(left+quarter+6,top+198,quarter,Component.translatable("alet.output"),
            b -> Util.getPlatform().openPath(AletJobs.output==null?AletJobs.outputRoot():AletJobs.output.directory()));
        cancel=button(left+2*(quarter+6),top+198,quarter,Component.translatable("alet.cancel"),b -> AletJobs.control.cancelled.set(true));
        button(left+3*(quarter+6),top+198,panelWidth-3*(quarter+6),Component.translatable("gui.done"),b -> onClose());
    }
    private void generate() {
        try {
            var options=settings.rasterOptions(settings.textMode);
            int thickness=Integer.parseInt(settings.thickness);
            if (settings.textMode) AletJobs.text(new RasterService.Text(settings.text,settings.family,
                Integer.parseInt(settings.fontSize),settings.style,RasterService.parseColor(settings.color),
                Double.parseDouble(settings.rotation),settings.antialias),options,settings.grid,thickness,settings.plane);
            else AletJobs.image(settings.source,options,settings.grid,thickness,settings.plane);
        } catch (NumberFormatException e) { AletJobs.failure(new RasterException("number")); }
        catch (IllegalArgumentException e) { AletJobs.failure(new RasterException("settings")); }
        catch (Exception e) { AletJobs.failure(e); }
    }
    @Override public void onFilesDrop(List<Path> files) {
        if (files.isEmpty()) return;
        Path path=files.getFirst(); String name=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".ttf") || name.endsWith(".otf")) { settings.textMode=true; settings.family=path.toString(); }
        else { settings.textMode=false; settings.source=path.toString(); }
        rebuildWidgets();
    }
    private void label(GuiGraphics graphics,String key,int x,int y) { graphics.drawString(font,Component.translatable(key),x,y,0xDDDDDD); }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float tick) {}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(left-8,top-4,left+panelWidth+8,top+224,0xEE202020);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawString(font,title,left,top+4,0xFFFFFF);
        label(graphics,settings.textMode?"alet.text":"alet.source",left,top+46);
        if (settings.textMode) {
            label(graphics,"alet.font",left,top+83);
            label(graphics,"alet.font_size",left+panelWidth-55,top+83);
            label(graphics,"alet.color",left,top+120);
        } else {
            label(graphics,"alet.width",left,top+83);
            label(graphics,"alet.height",left+(panelWidth-18)/4+6,top+83);
            label(graphics,"alet.scale_hint",left,top+120);
        }
        Component status=AletJobs.busy?Component.translatable("alet.phase."+AletJobs.control.phase,AletJobs.control.percent):AletJobs.status;
        graphics.drawString(font,font.plainSubstrByWidth(status.getString(),panelWidth),left,top+159,0xFFFFAA,false);
        generate.active=!AletJobs.busy;
        preview.active=copy.active=!AletJobs.busy && AletJobs.output!=null;
        nativeImport.active=preview.active && minecraft.player!=null;
        cancel.active=AletJobs.busy;
    }
    @Override public boolean isPauseScreen() { return false; }
}
