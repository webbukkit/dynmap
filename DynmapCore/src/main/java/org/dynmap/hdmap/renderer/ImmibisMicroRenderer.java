package org.dynmap.hdmap.renderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class ImmibisMicroRenderer extends CustomRenderer {
    private static final String[] tileFields = { "ICMP" };
    /* Defined texture indexes
     * 1 = stone
     * 2 = grass
     * 3 = dirt
     * 4 = cobblestone
     * 5 = planks:0
     * 6 = planks:1
     * 7 = planks:2
     * 8 = planks:3
     * 9 = bedrock
     * 10 = sand
     * 11 = gravel
     * 12 = oreGold
     * 13 = oreIron
     * 14 = oreCoal
     * 15 = wood:0
     * 16 = wood:1
     * 17 = wood:2
     * 18 = wood:3
     * 19 = leaves:0
     * 20 = leaves:1
     * 21 = leaves:2
     * 22 = leaves:3
     * 23 = sponge
     * 24 = glass
     * 25 = oreLapis
     * 26 = blockLapis
     * 27 = dispenser
     * 28 = sandStone
     * 29 = music
     * 30 = pistonStickyBase
     * 31 = pistonBase
     * 32 = cloth:0
     * 33 = cloth:1
     * 34 = cloth:2
     * 35 = cloth:3
     * 36 = cloth:4
     * 37 = cloth:5
     * 38 = cloth:6
     * 39 = cloth:7
     * 40 = cloth:8
     * 41 = cloth:9
     * 42 = cloth:10
     * 43 = cloth:11
     * 44 = cloth:12
     * 45 = cloth:13
     * 46 = cloth:14
     * 47 = cloth:15
     * 48 = blockGold
     * 49 = blockIron
     * 50 = brick
     * 51 = tnt
     * 52 = bookShelf
     * 53 = cobblestoneMossy
     * 54 = obsidian
     * 55 = mobSpawner
     * 56 = oreDiamond
     * 57 = blockDiamond
     * 58 = workbench
     * 59 = furnaceIdle
     * 60 = oreRedstone
     * 61 = blockSnow
     * 62 = blockClay
     * 63 = jukebox
     * 64 = pumpkin
     * 65 = netherrack
     * 66 = slowSand
     * 67 = glowStone
     * 68 = pumpkinLantern
     * 69 = stoneBrick
     * 70 = melon
     * 71 = mycelium
     * 72 = netherBrick
     * 73 = whiteStone:0
     * 74 = whiteStone:1
     * 75 = oreEmerald
     * 76 = blockEmerald
     * 77 = commandBlock
     * 78 = sandStone:1
     * 79 = sandStone:2
     * 80 = redstoneLampIdle
     * 81 = stoneBrick:1
     * 82 = stoneBrick:2
     * 83 = stoneBrick:3
     * 84 = blockRedstone
     * 85 = oreNetherQuartz
     * 86 = blockNetherQuartz:0
     * 87 = blockNetherQuartz:1
     * 88 = blockNetherQuartz:2
     * 89 = dropper
    */
    private static final int NUM_TEXTURES = 102;

    /* Texture index = material index in RP */
    private static final int materialTextureMap[][] = {
        { 0 }, // 0 = ?
        { 0 }, // 1 = Stone (stone:0)
        { 0 }, // 2 = N/A
        { 1 }, // 3 = dirt
        { 2 }, // 4 = cobblestone
        { 3 }, // 5 = planks:0
        { 4 }, // 6 = planks:1
        { 5 }, // 7 = planks:2
        { 6 }, // 8 = planks:3
        { 7 }, // 9 = bedrock
        { 8 }, // 10 = sand
        { 9 }, // 11 = gravel
        { 10 }, // 12 = oreGold
        { 11 }, // 13 = oreIron
        { 12 }, // 14 = oreCoal
        { 13, 13, 14, 14, 14, 14 }, // 15 = wood:0
        { 13, 13, 15, 15, 15, 15 }, // 16 = wood:1
        { 13, 13, 16, 16, 16, 16 }, // 17 = wood:2
        { 13, 13, 17, 17, 17, 17 }, // 18 = wood:3
        { 0 }, // 19 = N/A
        { 0 }, // 20 = N/A
        { 0 }, // 21 = N/A
        { 0 }, // 22 = N/A
        { 18 }, // 23 = sponge
        { 19 }, // 24 = glass
        { 20 }, // 25 = oreLapis
        { 21 }, // 26 = blockLapis
        { 22, 22, 23, 24, 24, 24 }, // 27 = dispenser
        { 25, 26, 27, 27, 27, 27 }, // 28 = sandStone:0
        { 28 }, // 29 = music
        { 29, 30, 31, 31, 31, 31 }, // 30 = pistonStickyBase
        { 32, 33, 31, 31, 31, 31 } , // 31 = pistonBase
        { 34 }, // 32 = cloth:0
        { 35 }, // 33 = cloth:1
        { 36 }, // 34 = cloth:2
        { 37 }, // 35 = cloth:3
        { 38 }, // 36 = cloth:4
        { 39 }, // 37 = cloth:5
        { 40 }, // 38 = cloth:6
        { 41 }, // 39 = cloth:7
        { 42 }, // 40 = cloth:8
        { 43 }, // 41 = cloth:9
        { 44 }, // 42 = cloth:10
        { 45 }, // 43 = cloth:11
        { 46 }, // 44 = cloth:12
        { 47 }, // 45 = cloth:13
        { 48 }, // 46 = cloth:14
        { 49 }, // 47 = cloth:15
        { 50 }, // 48 = blockGold
        { 51 }, // 49 = blockIron
        { 52 }, // 50 = brick
        { 53 }, // 51 = tnt
        { 3, 3, 54, 54, 54, 54 }, // 52 = bookShelf
        { 55 }, // 53 = cobblestoneMossy
        { 56 }, // 54 = obsidian
        { 57 }, // 55 = mobSpawner
        { 58 }, // 56 = oreDiamond
        { 59 }, // 57 = blockDiamond
        { 60, 60, 61, 61, 62, 62 }, // 58 = workbench
        { 22, 22, 63, 64, 64, 64 }, // 59 = furnaceIdle
        { 65 }, // 60 = oreRedstone
        { 66 }, // 61 = blockSnow
        { 67 }, // 62 = blockClay
        { 68, 68, 69, 69, 69, 69 }, //  63 = jukebox
        { 70, 70, 71, 71, 71, 71 }, // 64 = pumpkin
        { 72 }, // 65 = netherrack
        { 73 }, // 66 = slowSand
        { 74 }, // 67 = glowStone
        { 70, 70, 75, 71, 71, 71 }, // 68 = pumpkinLantern
        { 76 }, // 69 = stoneBrick:0
        { 77, 77, 78, 78, 78, 78 }, // 70 = melon
        { 1, 79, 80, 80, 80, 80 }, // 71 = mycelium
        { 81 }, // 72 = netherBrick
        { 82 }, // 73 = whiteStone:0
        { 82 }, // 74 = whiteStone:1
        { 83 }, // 75 = oreEmerald
        { 84 }, // 76 = blockEmerald
        { 85 }, // 77 = commandBlock
        { 26, 26, 86, 86, 86, 86 }, // 78 = sandStone:1
        { 26, 26, 87, 87, 87, 87 }, // 79 = sandStone:2
        { 88 }, // 80 = redstoneLampIdle
        { 89 }, // 81 = stoneBrick:1
        { 90 }, // 82 = stoneBrick:2
        { 91 }, // 83 = stoneBrick:3
        { 92 }, // 84 = blockRedstone
        { 93 }, // 85 = oreNetherQuartz
        { 94, 95, 96, 96, 96, 96 }, // 86 = blockNetherQuartz:0
        { 94, 97, 98, 98, 98, 98 }, // 87 = blockNetherQuartz:1
        { 99, 99, 100, 100, 100, 100 }, // 88 = blockNetherQuartz:2
        { 22, 101, 22, 22, 22, 22 } // 89 = dropper
    };
    @Override
    public boolean initializeRenderer(RenderPatchFactory rpf, String blkname, BitSet blockdatamask, Map<String,String> custparm) {
        if(!super.initializeRenderer(rpf, blkname, blockdatamask, custparm))
            return false;
        /* Flesh out sides map */
        for(int i = 0; i < materialTextureMap.length; i++) {
            if(materialTextureMap[i].length < 6) {
                int[] sides = new int[6];
                Arrays.fill(sides,  materialTextureMap[i][0]);
                materialTextureMap[i] = sides;
            }
        }
        return true;
    }

    @Override
    public int getMaximumTextureCount() {
        return NUM_TEXTURES;
    }
    
    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext ctx) {
        Object v = ctx.getBlockTileEntityField("ICMP");

        /* Build patch list */
        ArrayList<RenderPatch> list = new ArrayList<RenderPatch>();
        if ((v != null) && (v instanceof List)) {
            List<?> lv = (List<?>) v;
            for (Object lval : lv) {
                if (lval instanceof Map) {
                    Map<?, ?> mv = (Map<?,?>) lval;
                    Integer type = (Integer)mv.get("type");
                    Byte pos = (Byte)mv.get("pos");
                    if ((type != null) && (pos != null)) {
                        addPatchesFor(ctx.getPatchFactory(), list, type, pos);
                    }
                }
            }
        }
        return list.toArray(new RenderPatch[list.size()]);
    }
    
    private boolean isHollow(int shape, int thickness) {
        return (shape == 3);
    }
    private double getThickness(int shape, int thickness) {
        return (0.125 * thickness);
    }

    private enum AxisPos {
        CENTER,
        NEGATIVE,
        POSITIVE,
        SPAN
    };
    
    private static final AxisPos axes_by_pos[][] = {
        { AxisPos.CENTER, AxisPos.CENTER, AxisPos.CENTER }, // Centre
        { AxisPos.NEGATIVE, AxisPos.SPAN, AxisPos.SPAN }, // FaceNX
        { AxisPos.POSITIVE, AxisPos.SPAN, AxisPos.SPAN }, // FacePX
        { AxisPos.SPAN, AxisPos.NEGATIVE, AxisPos.SPAN }, // FaceNY
        { AxisPos.SPAN, AxisPos.POSITIVE, AxisPos.SPAN }, // FacePY
        { AxisPos.SPAN, AxisPos.SPAN, AxisPos.NEGATIVE }, // FaceNZ
        { AxisPos.SPAN, AxisPos.SPAN, AxisPos.POSITIVE }, // FacePZ
        { AxisPos.NEGATIVE, AxisPos.NEGATIVE, AxisPos.SPAN }, // EdgeNXNY
        { AxisPos.NEGATIVE, AxisPos.POSITIVE, AxisPos.SPAN }, // EdgeNXPY
        { AxisPos.POSITIVE, AxisPos.NEGATIVE, AxisPos.SPAN }, // EdgePXNY
        { AxisPos.POSITIVE, AxisPos.POSITIVE, AxisPos.SPAN }, // EdgePXPY
        { AxisPos.NEGATIVE, AxisPos.SPAN, AxisPos.NEGATIVE }, // EdgeNXNZ
        { AxisPos.NEGATIVE, AxisPos.SPAN, AxisPos.POSITIVE }, // EdgeNXPZ
        { AxisPos.POSITIVE, AxisPos.SPAN, AxisPos.NEGATIVE }, // EdgePXNZ
        { AxisPos.POSITIVE, AxisPos.SPAN, AxisPos.POSITIVE }, // EdgePXPZ
        { AxisPos.SPAN, AxisPos.NEGATIVE, AxisPos.NEGATIVE }, // EdgeNYNZ
        { AxisPos.SPAN, AxisPos.NEGATIVE, AxisPos.POSITIVE }, // EdgeNYPZ
        { AxisPos.SPAN, AxisPos.POSITIVE, AxisPos.NEGATIVE }, // EdgePYNZ
        { AxisPos.SPAN, AxisPos.POSITIVE, AxisPos.POSITIVE }, // EdgePYPZ
        { AxisPos.NEGATIVE, AxisPos.NEGATIVE, AxisPos.NEGATIVE }, // CornerNXNYNZ
        { AxisPos.NEGATIVE, AxisPos.NEGATIVE, AxisPos.POSITIVE }, // CornerNXNYPZ
        { AxisPos.NEGATIVE, AxisPos.POSITIVE, AxisPos.NEGATIVE }, // CornerNXPYNZ
        { AxisPos.NEGATIVE, AxisPos.POSITIVE, AxisPos.POSITIVE }, // CornerNXPYPZ
        { AxisPos.POSITIVE, AxisPos.NEGATIVE, AxisPos.NEGATIVE }, // CornerPXNYNZ
        { AxisPos.POSITIVE, AxisPos.NEGATIVE, AxisPos.POSITIVE }, // CornerPXNYPZ
        { AxisPos.POSITIVE, AxisPos.POSITIVE, AxisPos.NEGATIVE }, // CornerPXPYNZ
        { AxisPos.POSITIVE, AxisPos.POSITIVE, AxisPos.POSITIVE }, // CornerPXPYPZ
        { AxisPos.SPAN, AxisPos.CENTER, AxisPos.CENTER }, // PostX
        { AxisPos.CENTER, AxisPos.SPAN, AxisPos.CENTER }, // PostY
        { AxisPos.CENTER, AxisPos.CENTER, AxisPos.SPAN } // PostZ
    };
    
    private double getAxisMin(AxisPos ap, double thick) {
        switch (ap) {
            case POSITIVE:
                return 1.0 - thick;
            case CENTER:
                return 0.5 - (thick / 2.0);
            default:
                return 0.0;
        }
    }
    private double getAxisMax(AxisPos ap, double thick) {
        switch (ap) {
            case NEGATIVE:
                return thick;
            case CENTER:
                return 0.5 + (thick / 2.0);
            default:
                return 1.0;
        }
    }
    
    private void addPatchesFor(RenderPatchFactory rpf, ArrayList<RenderPatch> list, int type, int pos) {
        int[] sides;
        int material = (type >> 6) & 0xFFFF; // Get material type
        int shape = (type >> 3) & 0x7; // Get shape
        int thickness = (type & 0x7) + 1; // Get thickness

        if((material < 0) || (material >= materialTextureMap.length)) {
            material = 0;
        }
        if ((pos < 0) || (pos >= axes_by_pos.length)) {
            pos = 0;
        }
        sides = materialTextureMap[material];   /* Get sides map for texture */
        double thick = getThickness(shape, thickness);
        if (thick <= 0.0) return;
        if (thick > 1.0) thick = 1.0;
        
        /* If a hollow block, handle specially */
        if(isHollow(shape, thickness)) {
            switch(pos) {
                case 1: /* X min cover */
                    CustomRenderer.addBox(rpf, list, 0, thick, 0, 0.75, 0, 0.25, sides);
                    CustomRenderer.addBox(rpf, list, 0, thick, 0.75, 1, 0, 0.75, sides);
                    CustomRenderer.addBox(rpf, list, 0, thick, 0.25, 1, 0.75, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0, thick, 0, 0.25, 0.25, 1, sides);
                    break;
                case 2: /* X max cover */
                    CustomRenderer.addBox(rpf, list, 1-thick, 1, 0, 0.75, 0, 0.25, sides);
                    CustomRenderer.addBox(rpf, list, 1-thick, 1, 0.75, 1, 0, 0.75, sides);
                    CustomRenderer.addBox(rpf, list, 1-thick, 1, 0.25, 1, 0.75, 1, sides);
                    CustomRenderer.addBox(rpf, list, 1-thick, 1, 0, 0.25, 0.25, 1, sides);
                    break;
                case 3: /* Bottom cover */
                    CustomRenderer.addBox(rpf, list, 0, 0.75, 0, thick, 0, 0.25, sides);
                    CustomRenderer.addBox(rpf, list, 0.75, 1, 0, thick, 0, 0.75, sides);
                    CustomRenderer.addBox(rpf, list, 0.25, 1, 0, thick, 0.75, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0, 0.25, 0, thick, 0.25, 1, sides);
                    break;
                case 4: /* Top cover */
                    CustomRenderer.addBox(rpf, list, 0, 0.75, 1-thick, 1, 0, 0.25, sides);
                    CustomRenderer.addBox(rpf, list, 0.75, 1, 1-thick, 1, 0, 0.75, sides);
                    CustomRenderer.addBox(rpf, list, 0.25, 1, 1-thick, 1, 0.75, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0, 0.25, 1-thick, 1, 0.25, 1, sides);
                    break;
                case 5: /* Z min cover */
                    CustomRenderer.addBox(rpf, list, 0, 0.75, 0, 0.25, 0, thick, sides);
                    CustomRenderer.addBox(rpf, list, 0.75, 1, 0, 0.75, 0, thick, sides);
                    CustomRenderer.addBox(rpf, list, 0.25, 1, 0.75, 1, 0, thick, sides);
                    CustomRenderer.addBox(rpf, list, 0, 0.25, 0.25, 1, 0, thick, sides);
                    break;
                case 6: /* Z max cover */
                    CustomRenderer.addBox(rpf, list, 0, 0.75, 0, 0.25, 1-thick, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0.75, 1, 0, 0.75, 1-thick, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0.25, 1, 0.75, 1, 1-thick, 1, sides);
                    CustomRenderer.addBox(rpf, list, 0, 0.25, 0.25, 1, 1-thick, 1, sides);
                    break;
            }
        }
        else {
            CustomRenderer.addBox(rpf, list, getAxisMin(axes_by_pos[pos][0], thick), getAxisMax(axes_by_pos[pos][0], thick), getAxisMin(axes_by_pos[pos][1], thick), getAxisMax(axes_by_pos[pos][1], thick), getAxisMin(axes_by_pos[pos][2], thick), getAxisMax(axes_by_pos[pos][2], thick), sides);
        }
    }
    
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return tileFields;
    }
}
