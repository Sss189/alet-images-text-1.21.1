package com.docvin.alet.blueprint;

import com.docvin.alet.raster.JobControl;
import com.docvin.alet.raster.RasterService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import team.creative.littletiles.common.math.box.LittleBox;
import team.creative.littletiles.common.math.vec.LittleVec;

/** Current LT collection format; game-thread registry lookup is captured before encoding. */
public final class BlueprintEncoder {
    public enum Plane { XY, XZ, ZY }
    private BlueprintEncoder() {}
    public static CompoundTag encode(RasterService.Result result, int grid, int thickness, Plane plane,
                                     String blockName, JobControl control) {
        if (thickness < 1 || thickness > grid) throw new IllegalArgumentException("Invalid thickness");
        control.phase="blueprint";
        var colors=new LinkedHashMap<Integer,List<LittleBox>>();
        int[] min={Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE},max={0,0,0};
        int done=0;
        for (var rectangle:result.rectangles()) {
            control.check();
            int y0=result.image().getHeight()-rectangle.y1(),y1=result.image().getHeight()-rectangle.y0();
            LittleBox box=switch (plane) {
                case XY -> new LittleBox(rectangle.x0(),y0,0,rectangle.x1(),y1,thickness);
                case XZ -> new LittleBox(rectangle.x0(),0,rectangle.y0(),rectangle.x1(),thickness,rectangle.y1());
                case ZY -> new LittleBox(0,y0,rectangle.x0(),thickness,y1,rectangle.x1());
            };
            colors.computeIfAbsent(rectangle.argb(),key -> new ArrayList<>()).add(box);
            min[0]=Math.min(min[0],box.minX); min[1]=Math.min(min[1],box.minY); min[2]=Math.min(min[2],box.minZ);
            max[0]=Math.max(max[0],box.maxX); max[1]=Math.max(max[1],box.maxY); max[2]=Math.max(max[2],box.maxZ);
            control.percent=70+20*++done/result.rectangles().size();
        }
        var boxes=new ListTag(); boolean translucent=false;
        for (var entry:colors.entrySet()) {
            control.check(); boxes.add(new IntArrayTag(new int[]{entry.getKey()}));
            if ((entry.getKey()>>>24)!=255) translucent=true;
            for (var box:entry.getValue()) boxes.add(box.getArrayTag());
        }
        var tiles=new CompoundTag(); tiles.put(blockName,boxes);
        var group=new CompoundTag(); group.put("t",tiles); group.putInt("grid",grid);
        new LittleVec(min[0],min[1],min[2]).save("min",group);
        new LittleVec(max[0]-min[0],max[1]-min[1],max[2]-min[2]).save("size",group);
        group.putInt("tiles",colors.size()); group.putInt("boxes",result.rectangles().size());
        if (translucent) group.putBoolean("trans",true);
        return group;
    }
}
