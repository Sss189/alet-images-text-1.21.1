package com.docvin.alet.client;

import com.docvin.alet.ALET;
import com.docvin.alet.blueprint.BlueprintEncoder;
import com.docvin.alet.raster.JobControl;
import com.docvin.alet.raster.RasterException;
import com.docvin.alet.raster.RasterService;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import team.creative.littletiles.LittleTilesRegistry;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.grid.LittleGrid;

public final class AletJobs {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r -> {
        var thread=new Thread(r,"ALET image and text conversion"); thread.setDaemon(true); return thread;
    });
    public record Output(Path directory, String blueprint, RasterService.Result raster, int grid) {}
    @FunctionalInterface private interface ImageTask { BufferedImage run(JobControl job) throws Exception; }
    public static boolean busy;
    public static JobControl control=new JobControl();
    public static Component status=Component.translatable("alet.ready");
    public static Output output;
    private AletJobs() {}
    public static ExecutorService worker() { return WORKER; }
    public static Path outputRoot() { return Minecraft.getInstance().gameDirectory.toPath().resolve("alet"); }
    public static void image(String source, RasterService.Options options, int grid, int thickness, BlueprintEncoder.Plane plane) throws Exception {
        start("image",options,grid,thickness,plane,job -> RasterService.read(source,job));
    }
    public static void text(RasterService.Text text, RasterService.Options options, int grid, int thickness, BlueprintEncoder.Plane plane) throws Exception {
        start("text",options,grid,thickness,plane,job -> RasterService.text(text,job));
    }
    private static void start(String kind, RasterService.Options options, int grid, int thickness,
                              BlueprintEncoder.Plane plane, ImageTask task) throws Exception {
        if (busy) throw new RasterException("busy");
        if (LittleGrid.tryGet(grid)==null || grid>LittleGrid.getMax().count) throw new RasterException("grid",LittleGrid.getMax().count);
        if (thickness<1 || thickness>grid) throw new RasterException("thickness",grid);
        // Prime LT's block lookup once on the client thread. Worker code only writes fresh tags.
        String blockName=new LittleElement(LittleTilesRegistry.CLEAN.value().defaultBlockState(),-1).getBlockName();
        Path root=outputRoot().resolve("prints");
        control=new JobControl(); var job=control; busy=true; output=null;
        status=Component.translatable("alet.working");
        CompletableFuture.supplyAsync(() -> {
            try {
                var raster=RasterService.convert(task.run(job),options,job);
                var tag=BlueprintEncoder.encode(raster,grid,thickness,plane,blockName,job);
                job.check(); String blueprint=tag.toString();
                if (blueprint.getBytes(StandardCharsets.UTF_8).length>4*1024*1024) throw new RasterException("blueprint_size");
                job.phase="saving"; job.percent=95;
                Files.createDirectories(root);
                Path directory=Files.createTempDirectory(root,kind+"-");
                Files.writeString(directory.resolve("model.struct"),blueprint,StandardCharsets.UTF_8);
                ImageIO.write(raster.image(),"PNG",directory.resolve("preview.png").toFile());
                job.check(); return new Output(directory,blueprint,raster,grid);
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        },WORKER).whenComplete((result,error) -> Minecraft.getInstance().execute(() -> {
            if (error!=null) failure(error);
            else if (job.cancelled.get()) failure(new CancellationException());
            else {
                busy=false; output=result; job.percent=100;
                status=Component.translatable("alet.success",result.raster().image().getWidth(),result.raster().image().getHeight(),result.raster().rectangles().size());
                ALET.LOG.info("ALET_OUTPUT {} ({} visible pixels, {} rectangles, {} colors)",result.directory(),result.raster().pixels(),result.raster().rectangles().size(),result.raster().colors());
            }
        }));
    }
    public static void failure(Throwable error) {
        while (error.getCause()!=null && (error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)) error=error.getCause();
        busy=false; output=null;
        if (error instanceof CancellationException) status=Component.translatable("alet.cancelled");
        else if (error instanceof RasterException problem) status=Component.translatable("alet.error."+problem.key,problem.arguments);
        else {
            status=Component.translatable("alet.failure",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
            ALET.LOG.error("ALET conversion failed",error);
        }
    }
    public static void openNativeImport() {
        var minecraft=Minecraft.getInstance();
        if (busy || output==null || minecraft.player==null) return;
        minecraft.keyboardHandler.setClipboard(output.blueprint());
        minecraft.setScreen(null); minecraft.player.connection.sendCommand("lt-import");
        minecraft.player.sendSystemMessage(Component.translatable("alet.native_help"));
    }
    public static void copyBlueprint() {
        if (output!=null && !busy) Minecraft.getInstance().keyboardHandler.setClipboard(output.blueprint());
    }
}
