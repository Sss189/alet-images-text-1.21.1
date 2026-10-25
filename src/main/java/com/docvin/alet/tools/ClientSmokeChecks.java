package com.docvin.alet.tools;

import com.docvin.alet.ALET;
import com.docvin.alet.blueprint.BlueprintEncoder;
import com.docvin.alet.client.AletJobs;
import com.docvin.alet.client.AletScreen;
import com.docvin.alet.client.OptionsScreen;
import com.docvin.alet.client.PreviewScreen;
import com.docvin.alet.client.ToolSettings;
import com.docvin.alet.raster.JobControl;
import com.docvin.alet.raster.RasterService;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import team.creative.littletiles.LittleTilesRegistry;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.grid.LittleGrid;
import team.creative.littletiles.common.item.ItemLittleBlueprint;

/** Opt-in tests in a separate client at its main menu; no player save is loaded. */
public final class ClientSmokeChecks {
    private static int stage,ticks,shots;
    private static boolean finished;
    private static volatile boolean screenshot;
    public static void install() { NeoForge.EVENT_BUS.addListener(ClientSmokeChecks::tick); }
    private static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if (finished || mc.getOverlay()!=null) return;
        try {
            if (stage==0 && mc.screen instanceof TitleScreen && ++ticks>10) {
                LittleGrid.configure(32);
                check(java.util.Arrays.stream(mc.options.keyMappings).anyMatch(key -> key.getName().equals("alet.key.open")),"F9 tool key not registered");
                check(new Font("Dialog",Font.PLAIN,24).canDisplayUpTo("小方块")==-1,"Dialog font cannot display Chinese");
                verifyPlanes();
                var root=mc.gameDirectory.toPath().resolve("alet-smoke"); Files.createDirectories(root);
                var image=new BufferedImage(64,48,BufferedImage.TYPE_INT_ARGB);
                for (int y=0;y<48;y++) for (int x=0;x<64;x++) image.setRGB(x,y,
                    (x<8 || y<8 || x>=56 || y>=40)?0:(x<32?0xffff8800:0x800088ff));
                var path=root.resolve("像素画测试.png"); ImageIO.write(image,"PNG",path.toFile());
                ToolSettings.INSTANCE.source=path.toString(); ToolSettings.INSTANCE.width="64"; ToolSettings.INSTANCE.height="48";
                var screen=new AletScreen(); screen.init(mc,427,240); new OptionsScreen(screen).init(mc,427,240);
                mc.setScreen(screen);
                pressGenerate(screen);
                stage=1;
            } else if (stage==1 && !AletJobs.busy) {
                verifyOutput();
                check(AletJobs.output.raster().rectangles().size()==2,"Image did not compress to two colored regions");
                screenshot=false; Screenshot.grab(mc.gameDirectory,"alet-image-tools.png",mc.getMainRenderTarget(),message -> screenshot=true);
                stage=2;
            } else if (stage==2 && screenshot) {
                ToolSettings.INSTANCE.textMode=true; ToolSettings.INSTANCE.text="小方块 LT"; ToolSettings.INSTANCE.color="55FF88";
                ToolSettings.INSTANCE.style=Font.BOLD;
                var screen=new AletScreen(); mc.setScreen(screen); pressGenerate(screen);
                stage=3;
            } else if (stage==3 && !AletJobs.busy) {
                verifyOutput(); screenshot=false;
                Screenshot.grab(mc.gameDirectory,"alet-text-tools.png",mc.getMainRenderTarget(),message -> screenshot=true); stage=4;
            } else if (stage==4 && screenshot) {
                mc.setScreen(new PreviewScreen(new AletScreen(),AletJobs.output)); shots=0; stage=5;
            } else if (stage==5 && ++shots==5) {
                screenshot=false; Screenshot.grab(mc.gameDirectory,"alet-text-preview.png",mc.getMainRenderTarget(),message -> screenshot=true); stage=6;
            } else if (stage==6 && screenshot) {
                finished=true;
                ALET.LOG.info("ALET_SMOKE_PASS image + Chinese text; async pipeline; XY/XZ/ZY geometry; alpha; native LT SNBT/data-component roundtrip; GUI/preview screenshots");
                mc.stop();
            }
            if (stage>0 && ++ticks>3600) throw new IllegalStateException("Smoke conversion timed out");
        } catch (Throwable error) {
            finished=true; ALET.LOG.error("ALET_SMOKE_FAIL",error); mc.stop();
        }
    }
    private static void verifyOutput() throws Exception {
        check(AletJobs.output!=null,"Asynchronous conversion failed: "+AletJobs.status.getString());
        var tag=TagParser.parseTag(AletJobs.output.blueprint()); var group=LittleGroup.load(tag);
        check(group.totalBoxes()==AletJobs.output.raster().rectangles().size(),"LT box count changed");
        check(group.totalTiles()==AletJobs.output.raster().colors(),"LT colors changed");
        check(LittleGroup.load(LittleGroup.save(group)).totalBoxes()==group.totalBoxes(),"SNBT roundtrip failed");
        var stack=new ItemStack(LittleTilesRegistry.BLUEPRINT.value());
        var item=(ItemLittleBlueprint)stack.getItem(); item.saveTiles(stack,group);
        check(item.hasTiles(stack) && item.getTiles(stack).totalBoxes()==group.totalBoxes(),"Blueprint data component failed");
        check(Files.isRegularFile(AletJobs.output.directory().resolve("model.struct")),"Missing structure file");
    }
    private static void verifyPlanes() throws Exception {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffff0000); image.setRGB(1,0,0xff00ff00); image.setRGB(0,1,0xff0000ff); image.setRGB(1,1,0x8000ffff);
        var result=RasterService.convert(image,new RasterService.Options(0,0,false,1,false),new JobControl());
        String block=new LittleElement(LittleTilesRegistry.CLEAN.value().defaultBlockState(),-1).getBlockName();
        for (var plane:BlueprintEncoder.Plane.values()) {
            var encoded=BlueprintEncoder.encode(result,32,2,plane,block,new JobControl());
            var group=LittleGroup.load(encoded); check(group.getGrid().count==32,"Grid lost");
            check(group.totalBoxes()==4 && group.totalTiles()==4,"Missing colored/alpha pixels");
            for (var tile:group) for (var box:tile) if (tile.color==0xffff0000) {
                check(box.minX==0,"Leftmost red pixel moved");
                if (plane==BlueprintEncoder.Plane.XY) check(box.minY==1 && box.minZ==0,"XY image flipped");
                if (plane==BlueprintEncoder.Plane.XZ) check(box.minY==0 && box.minZ==0,"XZ image flipped");
                if (plane==BlueprintEncoder.Plane.ZY) check(box.minY==1 && box.minZ==0,"ZY image flipped");
            }
            check(encoded.getBoolean("trans"),"Alpha not marked translucent");
        }
    }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); }
    private static void pressGenerate(AletScreen screen) {
        String label=net.minecraft.network.chat.Component.translatable("alet.generate").getString();
        for (var child:screen.children()) if (child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().equals(label)) { button.onPress(); return; }
        throw new IllegalStateException("Generate button was not found");
    }
}
