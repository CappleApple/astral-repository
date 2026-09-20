package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.RuneLayout;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class RuneLayoutGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void hopperLayersSitOnRayReachableOutlineFaces(GameTestHelper h){
        BlockPos relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.HOPPER);BlockPos pos=h.absolutePos(relative);
        for(Direction face:Direction.values())verifyCells(h,pos,face,4);
        var north=RuneLayout.cells(h.getLevel(),pos,Direction.NORTH,1).get(0);
        h.assertTrue(north.center().y-pos.getY()>=0.625,"Hopper side glyph lies on the outer upper wall, above its recessed funnel");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void narrowContainersBoundTheirGlyphsAndChestBodyPlacementRemainsStable(GameTestHelper h){
        BlockPos relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.BREWING_STAND);BlockPos pos=h.absolutePos(relative);
        verifyCells(h,pos,Direction.UP,1);verifyCells(h,pos,Direction.NORTH,1);
        h.assertTrue(RuneLayout.cells(h.getLevel(),pos,Direction.UP,1).get(0).size()<=0.125,"Brewing stand top glyph fits the narrow physical stem");
        h.setBlock(relative,Blocks.ENCHANTING_TABLE);verifyCells(h,pos,Direction.NORTH,4);verifyCells(h,pos,Direction.UP,4);
        h.setBlock(relative,Blocks.CHEST);verifyCells(h,pos,Direction.NORTH,4);
        double height=RuneLayout.cells(h.getLevel(),pos,Direction.NORTH,1).get(0).center().y-pos.getY();
        h.assertTrue(height>0.25&&height<0.4,"Chest side runes remain on the lower body below the moving lid");h.succeed();
    }
    @GameTest(templateNamespace="astral_repository",template="empty_workshop")
    public static void oneThroughFourUseFixedSizePatternsAndOnlyGlyphBoundsAreClickable(GameTestHelper h){
        BlockPos relative=new BlockPos(3,2,3);h.setBlock(relative,Blocks.BARREL);BlockPos pos=h.absolutePos(relative);
        double[][][] expected={{{0,0}},{{-.5,0},{.5,0}},{{0,.5},{-.5,-.5},{.5,-.5}},{{-.5,.5},{.5,.5},{-.5,-.5},{.5,-.5}}};
        for(Direction face:Direction.values()){
            var single=RuneLayout.cells(h.getLevel(),pos,face,1).get(0);double size=single.size();
            for(int count=1;count<=4;count++){
                var cells=RuneLayout.cells(h.getLevel(),pos,face,count);h.assertTrue(cells.size()==count,"Visible layer count matches placement");
                for(int i=0;i<count;i++){
                    var cell=cells.get(i);var delta=cell.center().subtract(single.center());
                    h.assertTrue(Math.abs(cell.size()-size)<0.0000001,"Glyph size remains fixed from one through four layers");
                    h.assertTrue(Math.abs(delta.dot(cell.right())-expected[count-1][i][0]*size)<0.0000001&&Math.abs(delta.dot(cell.up())-expected[count-1][i][1]*size)<0.0000001,"Centered, paired, triangle and quadrant positions match the shared layout");
                    h.assertTrue(RuneLayout.selectedIndex(h.getLevel(),pos,face,cell.center(),count)==i,"Actual glyph center selects its own rune");
                    h.assertTrue(RuneLayout.selectedIndex(h.getLevel(),pos,face,cell.center().add(cell.right().scale(size*.44)),count)==-1,"Blank cell margin outside the visible glyph never selects a rune");
                }
                verifyCells(h,pos,face,count);
            }
        }
        h.setBlock(relative,Blocks.CHEST);
        var center=RuneLayout.cells(h.getLevel(),pos,Direction.NORTH,1).get(0);
        for(int count=1;count<=4;count++){
            var cells=RuneLayout.cells(h.getLevel(),pos,Direction.NORTH,count);
            for(int i=0;i<count;i++){
                var cell=cells.get(i);var delta=cell.center().subtract(center.center());
                h.assertTrue(Math.abs(cell.size()-.20)<0.0000001,"Wide chest glyph size remains based on the smaller four-slot dimension");
                h.assertTrue(Math.abs(delta.dot(cell.right())-expected[count-1][i][0]*.36)<0.0000001&&Math.abs(delta.dot(cell.up())-expected[count-1][i][1]*.20)<0.0000001,"Wide chest centers use independent horizontal and vertical quadrants");
            }
            verifyCells(h,pos,Direction.NORTH,count);
        }
        h.succeed();
    }
    private static void verifyCells(GameTestHelper h,BlockPos pos,Direction face,int count){
        var cells=RuneLayout.cells(h.getLevel(),pos,face,count);h.assertTrue(cells.size()==count,"Requested cells exist on "+face);
        var shape=h.getLevel().getBlockState(pos).getShape(h.getLevel(),pos);
        for(int i=0;i<count;i++){
            var cell=cells.get(i);
            for(double x:new double[]{-0.38,0,0.38})for(double y:new double[]{-0.38,0,0.38}){
                var sample=cell.center().add(cell.right().scale(cell.size()*x)).add(cell.up().scale(cell.size()*y));
                var hit=shape.clip(sample.add(cell.normal().scale(2)),sample.subtract(cell.normal().scale(2)),pos);
                h.assertTrue(hit!=null&&hit.getDirection()==face,"Actual outline ray hits the rendered rune face on "+face);
                h.assertTrue(RuneLayout.selectedIndex(h.getLevel(),pos,face,hit.getLocation(),count)==i,"Actual ray hit selects the same visible layer on "+face);
            }
        }
    }
}
