package com.docvin.alet.client;

import com.docvin.alet.raster.RasterService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FontPickerScreen extends Screen {
    private static final CompletableFuture<List<String>> FONTS=CompletableFuture.supplyAsync(RasterService::fonts,AletJobs.worker());
    private final Screen parent;
    private final Consumer<String> chosen;
    private final List<Button> rows=new ArrayList<>();
    private String search="";
    private int page,left,top,panelWidth,pageSize;
    private List<String> all=List.of();
    public FontPickerScreen(Screen parent,Consumer<String> chosen) {
        super(Component.translatable("alet.choose_font")); this.parent=parent; this.chosen=chosen;
    }
    @Override protected void init() {
        panelWidth=Math.min(360,width-28); left=(width-panelWidth)/2; top=10;
        pageSize=Math.max(1,Math.min(12,(height-91)/20)); rows.clear();
        var filter=new EditBox(font,left,top+20,panelWidth,20,Component.translatable("alet.font_search"));
        filter.setValue(search); filter.setHint(Component.translatable("alet.font_search"));
        filter.setResponder(value -> { search=value; page=0; rebuildRows(); }); addRenderableWidget(filter);
        int third=(panelWidth-12)/3,bottom=top+48+pageSize*20;
        addRenderableWidget(Button.builder(Component.literal("<"),b -> { page=Math.max(0,page-1); rebuildRows(); }).bounds(left,bottom,third,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b -> onClose()).bounds(left+third+6,bottom,third,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b -> { if ((page+1)*pageSize<filtered().size()) page++; rebuildRows(); }).bounds(left+2*(third+6),bottom,panelWidth-2*(third+6),20).build());
        FONTS.whenComplete((list,error) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            all=error==null?list:List.of("Dialog","SansSerif","Serif","Monospaced");
            if (minecraft.screen==this) rebuildRows();
        }));
        rebuildRows();
    }
    private List<String> filtered() {
        String query=search.toLowerCase(Locale.ROOT);
        return all.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void rebuildRows() {
        for (var row:rows) removeWidget(row); rows.clear();
        List<String> list=filtered();
        for (int i=0;i<pageSize && page*pageSize+i<list.size();i++) {
            String name=list.get(page*pageSize+i);
            var button=Button.builder(Component.literal(font.plainSubstrByWidth(name,panelWidth-12)),b -> {
                chosen.accept(name); minecraft.setScreen(parent);
            }).bounds(left,top+45+i*20,panelWidth,18).build();
            rows.add(button); addRenderableWidget(button);
        }
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick) {
        renderTransparentBackground(graphics); graphics.fill(left-8,top-4,left+panelWidth+8,top+73+pageSize*20,0xEE202020);
        super.render(graphics,x,y,tick); graphics.drawString(font,title,left,top+3,0xFFFFFF);
        graphics.drawString(font,(page+1)+" / "+Math.max(1,(filtered().size()+pageSize-1)/pageSize),left+panelWidth-65,top+3,0xAAAAAA);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float tick) {}
    @Override public boolean isPauseScreen() { return false; }
}
