package com.docvin.alet.raster;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** ALET's image/font -> colored LittleTiles workflow, without game-thread IO. */
public final class RasterService {
    public static final int MAX_PIXELS = 65_536;
    public static final int MAX_EDGE = 1024;
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    public static final int MAX_SOURCE_PIXELS = 16_777_216;
    public static final int MAX_COLORS = 4096;
    public record Options(int width, int height, boolean ignoreAlpha, int colorStep, boolean mirror) {
        public Options {
            if (width < 0 || height < 0 || width > MAX_EDGE || height > MAX_EDGE || colorStep < 1 || colorStep > 64)
                throw new IllegalArgumentException("Invalid raster settings");
        }
    }
    public record Text(String content, String font, int size, int style, int argb, double rotation, boolean antialias) {}
    public record Rectangle(int x0, int y0, int x1, int y1, int argb) {}
    public record Result(BufferedImage image, List<Rectangle> rectangles, int pixels, int colors) {}
    private record Run(int x0, int x1, int argb) {}
    private static final class MutableRectangle {
        final int x0, y0, x1, argb;
        int y1;
        MutableRectangle(Run run, int y) { x0=run.x0; x1=run.x1; argb=run.argb; y0=y; y1=y+1; }
        Rectangle freeze() { return new Rectangle(x0,y0,x1,y1,argb); }
    }
    private RasterService() {}
    public static String cleanPath(String input) {
        String value = input.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
            value=value.substring(1,value.length()-1);
        return value;
    }
    public static BufferedImage read(String input, JobControl control) throws IOException {
        control.check();
        String source=cleanPath(input);
        if (source.isEmpty()) throw new RasterException("source_empty");
        byte[] bytes;
        if (source.toLowerCase(Locale.ROOT).startsWith("https://") || source.toLowerCase(Locale.ROOT).startsWith("http://")) {
            URLConnection connection;
            try { connection=URI.create(source).toURL().openConnection(); }
            catch (IllegalArgumentException e) { throw new RasterException("url_invalid"); }
            connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent","ALET-Images-Text/0.1.0");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new RasterException("file_size");
            try (InputStream stream=connection.getInputStream()) { bytes=readBounded(stream,control); }
        } else {
            Path path=Path.of(source);
            if (!Files.isRegularFile(path)) throw new RasterException("file_missing",source);
            if (Files.size(path) > MAX_BYTES) throw new RasterException("file_size");
            try (InputStream stream=Files.newInputStream(path)) { bytes=readBounded(stream,control); }
        }
        return decode(bytes,control);
    }
    private static byte[] readBounded(InputStream stream, JobControl control) throws IOException {
        var output=new java.io.ByteArrayOutputStream();
        byte[] buffer=new byte[8192]; int count;
        while ((count=stream.read(buffer)) != -1) {
            control.check();
            if (output.size()+(long)count > MAX_BYTES) throw new RasterException("file_size");
            output.write(buffer,0,count);
        }
        return output.toByteArray();
    }
    public static BufferedImage decode(byte[] bytes, JobControl control) throws IOException {
        control.check();
        try (var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new RasterException("image_invalid");
            var readers=ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new RasterException("image_invalid");
            var reader=readers.next();
            try {
                reader.setInput(input);
                long width=reader.getWidth(0),height=reader.getHeight(0);
                if (width < 1 || height < 1 || width*height > MAX_SOURCE_PIXELS)
                    throw new RasterException("source_size");
                control.check(); return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    public static List<String> fonts() {
        return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    public static BufferedImage text(Text request, JobControl control) throws IOException {
        control.check();
        String content=request.content().replace("\\n","\n");
        if (content.isBlank()) throw new RasterException("text_empty");
        if (content.length() > 2048) throw new RasterException("text_length");
        if (request.size() < 4 || request.size() > 256 || !Double.isFinite(request.rotation()))
            throw new RasterException("text_settings");
        Font font;
        String family=cleanPath(request.font());
        if (family.toLowerCase(Locale.ROOT).endsWith(".ttf") || family.toLowerCase(Locale.ROOT).endsWith(".otf")) {
            Path path=Path.of(family);
            if (!Files.isRegularFile(path)) throw new RasterException("font_missing",family);
            if (Files.size(path) > MAX_BYTES) throw new RasterException("file_size");
            try (var stream=Files.newInputStream(path)) { font=Font.createFont(Font.TRUETYPE_FONT,stream); }
            catch (FontFormatException e) { throw new RasterException("font_invalid"); }
            font=font.deriveFont(request.style(),(float)request.size());
        } else {
            boolean known=List.of("Dialog","DialogInput","SansSerif","Serif","Monospaced").stream().anyMatch(family::equalsIgnoreCase)
                || fonts().stream().anyMatch(family::equalsIgnoreCase);
            if (!known) throw new RasterException("font_missing",family);
            font=new Font(family,request.style(),request.size());
        }
        var probe=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=probe.createGraphics(); g.setFont(font);
        var metrics=g.getFontMetrics(); String[] lines=content.split("\n",-1);
        int lineHeight=metrics.getHeight(),ascent=metrics.getAscent();
        int width=Arrays.stream(lines).mapToInt(metrics::stringWidth).max().orElse(1)+8;
        int height=lineHeight*lines.length+8;
        g.dispose(); checkSize(width,height);
        double radians=Math.toRadians(request.rotation()%360);
        double sin=Math.abs(Math.sin(radians)),cos=Math.abs(Math.cos(radians));
        int rotatedWidth=Math.max(1,(int)Math.ceil(width*cos+height*sin));
        int rotatedHeight=Math.max(1,(int)Math.ceil(height*cos+width*sin));
        checkSize(rotatedWidth,rotatedHeight);
        var image=new BufferedImage(rotatedWidth,rotatedHeight,BufferedImage.TYPE_INT_ARGB);
        g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,request.antialias()?RenderingHints.VALUE_TEXT_ANTIALIAS_ON:RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        AffineTransform transform=new AffineTransform();
        transform.translate(rotatedWidth/2.0,rotatedHeight/2.0);
        transform.rotate(radians); transform.translate(-width/2.0,-height/2.0);
        g.setTransform(transform); g.setFont(font); g.setColor(new java.awt.Color(request.argb(),true));
        for (int i=0;i<lines.length;i++) { control.check(); g.drawString(lines[i],4,4+ascent+i*lineHeight); }
        g.dispose(); return image;
    }
    public static void checkSize(int width, int height) throws RasterException {
        if (width < 1 || height < 1 || width > MAX_EDGE || height > MAX_EDGE || (long)width*height > MAX_PIXELS)
            throw new RasterException("output_size",MAX_EDGE,MAX_PIXELS);
    }
    public static Result convert(BufferedImage source, Options options, JobControl control) throws IOException {
        control.check(); control.phase="pixels";
        int width=options.width(),height=options.height();
        if (width==0 && height==0) { width=source.getWidth(); height=source.getHeight(); }
        else if (width==0) width=Math.max(1,(int)Math.round((double)source.getWidth()*height/source.getHeight()));
        else if (height==0) height=Math.max(1,(int)Math.round((double)source.getHeight()*width/source.getWidth()));
        checkSize(width,height);
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        // Copy nearest source colors directly: Graphics2D alpha compositing can round RGB channels.
        for (int y=0;y<height;y++) {
            control.check();
            int sourceY=Math.min(source.getHeight()-1,(int)((y+0.5)*source.getHeight()/height));
            for (int x=0;x<width;x++) {
                int sourceX=Math.min(source.getWidth()-1,(int)((x+0.5)*source.getWidth()/width));
                image.setRGB(x,y,source.getRGB(sourceX,sourceY));
            }
        }
        var palette=new java.util.HashSet<Integer>(); int pixels=0;
        var rectangles=new ArrayList<Rectangle>();
        Map<Run,MutableRectangle> previous=new LinkedHashMap<>();
        for (int y=0;y<height;y++) {
            control.check(); Map<Run,MutableRectangle> current=new LinkedHashMap<>();
            int[] row=image.getRGB(0,y,width,1,null,0,width);
            for (int x=0;x<width;x++) {
                int color=row[options.mirror()?width-x-1:x];
                if ((color>>>24)!=0) {
                    color=quantize(color,options.colorStep());
                    if (options.ignoreAlpha()) color |= 0xff000000;
                    pixels++; palette.add(color);
                    if (palette.size() > MAX_COLORS) throw new RasterException("colors",MAX_COLORS);
                }
                image.setRGB(x,y,color);
            }
            for (int x=0;x<width;) {
                int start=x,color=image.getRGB(x,y);
                while (++x<width && image.getRGB(x,y)==color) {}
                if ((color>>>24)==0) continue;
                var run=new Run(start,x,color);
                MutableRectangle rectangle=previous.remove(run);
                if (rectangle==null) rectangle=new MutableRectangle(run,y);
                else rectangle.y1=y+1;
                current.put(run,rectangle);
            }
            for (var rectangle:previous.values()) rectangles.add(rectangle.freeze());
            previous=current; control.percent=20+50*(y+1)/height;
        }
        for (var rectangle:previous.values()) rectangles.add(rectangle.freeze());
        if (rectangles.isEmpty()) throw new RasterException("transparent");
        return new Result(image,List.copyOf(rectangles),pixels,palette.size());
    }
    public static int quantize(int argb, int step) {
        int r=Math.min(255,Math.round(((argb>>>16)&255)/(float)step)*step);
        int g=Math.min(255,Math.round(((argb>>>8)&255)/(float)step)*step);
        int b=Math.min(255,Math.round((argb&255)/(float)step)*step);
        return (argb&0xff000000)|(r<<16)|(g<<8)|b;
    }
    public static int parseColor(String text) throws RasterException {
        String value=text.trim().replaceFirst("^(#|0[xX])","");
        if (!value.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new RasterException("color");
        long number=Long.parseLong(value,16);
        return value.length()==6 ? (int)(number|0xff000000L) : (int)number;
    }
}
