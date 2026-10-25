package com.docvin.alet.client;

import com.docvin.alet.blueprint.BlueprintEncoder;
import com.docvin.alet.raster.RasterService;
import team.creative.littletiles.common.grid.LittleGrid;

/** Kept across screen closes and tab changes for this client session. */
public final class ToolSettings {
    public static final ToolSettings INSTANCE=new ToolSettings();
    public boolean textMode;
    public String source="", text="小方块 LT", family="Dialog", fontSize="24", color="FFFFFF";
    public String width="96",height="0",rotation="0",thickness="1",colorStep="1";
    public boolean ignoreAlpha,mirror,antialias;
    public int grid,style;
    public BlueprintEncoder.Plane plane=BlueprintEncoder.Plane.XY;
    public void ensureGrid() {
        if (grid==0 || grid>LittleGrid.getMax().count || LittleGrid.tryGet(grid)==null)
            grid=Math.min(32,LittleGrid.getMax().count);
    }
    public void nextGrid() {
        for (int i=0;i<LittleGrid.gridCount();i++) if (LittleGrid.gridByIndex(i).count==grid) {
            grid=LittleGrid.gridByIndex((i+1)%LittleGrid.gridCount()).count; return;
        }
        grid=LittleGrid.MIN.count;
    }
    public RasterService.Options rasterOptions(boolean text) {
        return new RasterService.Options(text?0:Integer.parseInt(width),text?0:Integer.parseInt(height),
            ignoreAlpha,Integer.parseInt(colorStep),mirror);
    }
}
