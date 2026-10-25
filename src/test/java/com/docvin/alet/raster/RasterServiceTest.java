package com.docvin.alet.raster;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CancellationException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterServiceTest {
    @TempDir Path directory;
    private RasterService.Result convert(BufferedImage image) throws Exception {
        return RasterService.convert(image,new RasterService.Options(0,0,false,1,false),new JobControl());
    }
    @Test void retainsExactPaletteAlphaAndTopDownCoordinates() throws Exception {
        var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        int[] colors={0xffff0000,0xff00ff00,0xff0000ff,0x8000ffff};
        image.setRGB(0,0,2,2,colors,0,2);
        var result=convert(image);
        assertArrayEquals(colors,result.image().getRGB(0,0,2,2,null,0,2));
        assertEquals(4,result.pixels()); assertEquals(4,result.colors());
    }
    @Test void mergesLargeSolidRegionWithoutChangingPixels() throws Exception {
        var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        var graphics=image.createGraphics(); graphics.setColor(java.awt.Color.GREEN); graphics.fillRect(0,0,256,256); graphics.dispose();
        var result=convert(image);
        assertEquals(1,result.rectangles().size());
        assertEquals(new RasterService.Rectangle(0,0,256,256,0xff00ff00),result.rectangles().getFirst());
    }
    @Test void rectangleCompressionHasNoGapsOrOverlapsForRandomPixelArt() throws Exception {
        var random=new Random(7911);
        for (int attempt=0;attempt<40;attempt++) {
            int width=1+random.nextInt(30),height=1+random.nextInt(30);
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<height;y++) for (int x=0;x<width;x++)
                image.setRGB(x,y,new int[]{0,0xffff0000,0xff0080ff,0x8000ff00}[random.nextInt(4)]);
            if (image.getRGB(0,0)==0) image.setRGB(0,0,0xffffffff);
            var result=convert(image); int[] rebuilt=new int[width*height];
            for (var rectangle:result.rectangles()) for (int y=rectangle.y0();y<rectangle.y1();y++) for (int x=rectangle.x0();x<rectangle.x1();x++) {
                assertEquals(0,rebuilt[y*width+x],"Overlapping rectangles"); rebuilt[y*width+x]=rectangle.argb();
            }
            assertArrayEquals(result.image().getRGB(0,0,width,height,null,0,width),rebuilt);
        }
    }
    @Test void transparentPixelsStayEmptyWhenPartialAlphaIsMadeOpaque() throws Exception {
        var image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0); image.setRGB(1,0,0x80ff0000);
        var result=RasterService.convert(image,new RasterService.Options(0,0,true,1,false),new JobControl());
        assertEquals(1,result.pixels()); assertEquals(0xffff0000,result.image().getRGB(1,0));
    }
    @Test void scalingPreservesNonPremultipliedLowAlphaRgbExactly() throws Exception {
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0x03123456);
        var result=RasterService.convert(image,new RasterService.Options(3,2,false,1,false),new JobControl());
        for (int color:result.image().getRGB(0,0,3,2,null,0,3)) assertEquals(0x03123456,color);
    }
    @Test void automaticallyKeepsAspectRatioAndUsesNearestPixelScaling() throws Exception {
        var image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffff0000); image.setRGB(1,0,0xff0000ff);
        var result=RasterService.convert(image,new RasterService.Options(8,0,false,1,false),new JobControl());
        assertEquals(8,result.image().getWidth()); assertEquals(4,result.image().getHeight());
        assertEquals(0xffff0000,result.image().getRGB(3,2)); assertEquals(0xff0000ff,result.image().getRGB(4,2));
    }
    @Test void mirrorReversesColumnsWithoutChangingColors() throws Exception {
        var image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffff0000); image.setRGB(1,0,0xff0000ff);
        var result=RasterService.convert(image,new RasterService.Options(0,0,false,1,true),new JobControl());
        assertArrayEquals(new int[]{0xff0000ff,0xffff0000},result.image().getRGB(0,0,2,1,null,0,2));
    }
    @Test void readsQuotedChineseFilePath() throws Exception {
        var image=new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,0xffffcc00);
        Path path=directory.resolve("像素画 测试.png"); ImageIO.write(image,"PNG",path.toFile());
        var loaded=RasterService.read("\""+path+"\"",new JobControl());
        assertEquals(0xffffcc00,loaded.getRGB(0,0)); assertEquals(3,loaded.getWidth());
    }
    @Test void readsJpegAndIndexedPngWithoutAssumingByteRasterLayout() throws Exception {
        for (int type:new int[]{BufferedImage.TYPE_INT_RGB,BufferedImage.TYPE_BYTE_INDEXED}) {
            var image=new BufferedImage(4,3,type); var bytes=new ByteArrayOutputStream();
            assertTrue(ImageIO.write(image,type==BufferedImage.TYPE_INT_RGB?"JPEG":"PNG",bytes));
            var loaded=RasterService.decode(bytes.toByteArray(),new JobControl());
            assertEquals(4,loaded.getWidth()); assertEquals(3,loaded.getHeight());
        }
    }
    @Test void rejectsUnsupportedDataAndMissingFile() {
        assertThrows(RasterException.class,() -> RasterService.decode(new byte[]{1,2,3},new JobControl()));
        var exception=assertThrows(RasterException.class,() -> RasterService.read(directory.resolve("missing.png").toString(),new JobControl()));
        assertEquals("file_missing",exception.key);
    }
    @Test void boundsOutputAndColorCountsAndRejectsTransparentImage() {
        assertThrows(RasterException.class,() -> RasterService.checkSize(512,512));
        assertThrows(RasterException.class,() -> RasterService.checkSize(2048,1));
        assertThrows(RasterException.class,() -> convert(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB)));
        var image=new BufferedImage(65,65,BufferedImage.TYPE_INT_RGB);
        for (int i=0;i<65*65;i++) image.setRGB(i%65,i/65,0xff000000|i);
        var exception=assertThrows(RasterException.class,() -> convert(image)); assertEquals("colors",exception.key);
    }
    @Test void cancellationStopsBeforeDoingWork() {
        var job=new JobControl(); job.cancelled.set(true);
        assertThrows(CancellationException.class,() -> RasterService.convert(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),
            new RasterService.Options(0,0,false,1,false),job));
    }
    @Test void textHasRequestedColorAndSupportsMultilineAndRotation() throws Exception {
        var normal=new RasterService.Text("LT\\nLT","Dialog",24,Font.BOLD,0xff12ab34,0,false);
        var rendered=RasterService.text(normal,new JobControl());
        var result=convert(rendered);
        assertEquals(1,result.colors()); assertEquals(0xff12ab34,result.rectangles().getFirst().argb());
        var single=RasterService.text(new RasterService.Text("LT","Dialog",24,Font.BOLD,0xff12ab34,0,false),new JobControl());
        assertTrue(rendered.getHeight()>single.getHeight());
        var rotated=RasterService.text(new RasterService.Text("LT\\nLT","Dialog",24,Font.BOLD,0xff12ab34,90,false),new JobControl());
        assertTrue(Math.abs(rendered.getWidth()-rotated.getHeight())<=1);
        assertTrue(Math.abs(rendered.getHeight()-rotated.getWidth())<=1);
    }
    @Test void fontNameAndInputLimitsAreValidated() {
        assertThrows(RasterException.class,() -> RasterService.text(new RasterService.Text("LT","missing-font-82971",24,0,-1,0,false),new JobControl()));
        assertThrows(RasterException.class,() -> RasterService.text(new RasterService.Text(" ","Dialog",24,0,-1,0,false),new JobControl()));
        assertThrows(RasterException.class,() -> RasterService.text(new RasterService.Text("x".repeat(3000),"Dialog",24,0,-1,0,false),new JobControl()));
    }
    @Test void colorParsingAndQuantizationPreserveAlphaAndChannels() throws Exception {
        assertEquals(0xff123456,RasterService.parseColor("#123456"));
        assertEquals(0x80123456,RasterService.parseColor("80123456"));
        assertEquals(0xffff0000,RasterService.parseColor("0xFF0000"));
        assertThrows(RasterException.class,() -> RasterService.parseColor("white"));
        assertEquals(0x80102030,RasterService.quantize(0x80112332,16));
    }
}
