package com.docvin.alet.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PreviewScreen extends Screen {
    private final Screen parent;
    private final AletJobs.Output output;
    private ResourceLocation texture;
    public PreviewScreen(Screen parent,AletJobs.Output output) {
        super(Component.translatable("alet.preview")); this.parent=parent; this.output=output;
    }
    @Override protected void init() {
        if (texture==null) {
            var image=output.raster().image(); var nativeImage=new NativeImage(image.getWidth(),image.getHeight(),true);
            for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
                int argb=image.getRGB(x,y);
                nativeImage.setPixelRGBA(x,y,(argb&0xff00ff00)|((argb&255)<<16)|((argb>>>16)&255));
            }
            var dynamic=new DynamicTexture(nativeImage); dynamic.setFilter(false,false);
            texture=minecraft.getTextureManager().register("alet-preview",dynamic);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b -> onClose()).bounds(width/2-70,height-29,140,20).build());
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick) {
        renderTransparentBackground(graphics); graphics.fill(0,0,width,height,0xED202020);
        var image=output.raster().image();
        double scale=Math.min((width-24.0)/image.getWidth(),(height-88.0)/image.getHeight());
        int w=Math.max(1,(int)(image.getWidth()*scale)),h=Math.max(1,(int)(image.getHeight()*scale));
        int left=(width-w)/2,top=40+(height-88-h)/2;
        for (int py=top;py<top+h;py+=8) for (int px=left;px<left+w;px+=8)
            graphics.fill(px,py,Math.min(px+8,left+w),Math.min(py+8,top+h),(((px-left)/8+(py-top)/8)%2==0)?0xff777777:0xff555555);
        graphics.blit(texture,left,top,w,h,0.0F,0.0F,image.getWidth(),image.getHeight(),image.getWidth(),image.getHeight());
        super.render(graphics,x,y,tick);
        graphics.drawCenteredString(font,title,width/2,8,0xFFFFFF);
        graphics.drawCenteredString(font,Component.translatable("alet.preview_size",image.getWidth(),image.getHeight(),
            String.format(java.util.Locale.ROOT,"%.2f",image.getWidth()/(double)output.grid()),
            String.format(java.util.Locale.ROOT,"%.2f",image.getHeight()/(double)output.grid())),width/2,22,0xDDDDDD);
    }
    @Override public void removed() {
        if (texture!=null) { Minecraft.getInstance().getTextureManager().release(texture); texture=null; }
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float tick) {}
    @Override public boolean isPauseScreen() { return false; }
}
