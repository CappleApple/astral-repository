package com.cappleapple.astralrepository.content;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared visible cells and click targets, fitted to a reachable outer outline rectangle. */
public final class RuneLayout {
    public record Cell(Vec3 center,Vec3 right,Vec3 up,Vec3 normal,double size) {}
    public static List<Cell> cells(BlockGetter level,BlockPos pos,Direction face,int count){
        if(count<=0)return List.of();count=Math.min(count,RuneSurface.MAX_LAYERS);
        var state=level.getBlockState(pos);var shape=state.getShape(level,pos);if(shape.isEmpty())return List.of();
        Vec3 normal=Vec3.atLowerCornerOf(face.getNormal());
        Vec3 up=face.getAxis().isVertical()?new Vec3(0,0,-1):new Vec3(0,1,0),right=up.cross(normal).normalize();
        double plane=edge(shape.bounds(),face),best=-1;AABB bounds=null;
        // Only boxes reaching the outermost plane are candidates. A union's bounding rectangle
        // can cover empty space, such as the recessed hopper funnel or the opening in its top.
        for(AABB box:shape.toAabbs()){
            if(Math.abs(edge(box,face)-plane)>0.0000001)continue;
            double area=extent(box,right)*extent(box,up);
            if(area>best){bounds=box;best=area;}
        }
        if(bounds==null||best<=0)return List.of();
        Vec3 center=new Vec3((bounds.minX+bounds.maxX)/2,(bounds.minY+bounds.maxY)/2,(bounds.minZ+bounds.maxZ)/2);
        double offset=switch(face){case WEST->center.x-plane;case EAST->plane-center.x;case DOWN->center.y-plane;case UP->plane-center.y;case NORTH->center.z-plane;case SOUTH->plane-center.z;};
        center=center.add(normal.scale(offset+0.002)).add(Vec3.atLowerCornerOf(pos));
        boolean chest=state.getBlock() instanceof AbstractChestBlock<?>;
        if(chest&&!face.getAxis().isVertical())center=center.add(0,-0.11,0);
        double width=Math.min(0.72,extent(bounds,right)*0.9);
        double height=Math.min(chest&&!face.getAxis().isVertical()?0.40:0.72,extent(bounds,up)*0.9);
        // Every count reserves the same four-slot footprint, so adding a rune never shrinks one.
        double size=Math.min(width/2,height/2);
        double[][] offsets=switch(count){
            case 1->new double[][]{{0,0}};
            case 2->new double[][]{{-.5,0},{.5,0}};
            case 3->new double[][]{{0,.5},{-.5,-.5},{.5,-.5}};
            default->new double[][]{{-.5,.5},{.5,.5},{-.5,-.5},{.5,-.5}};
        };
        if(count>4){
            int columns=(int)Math.ceil(Math.sqrt(count*width/height)),rows=(count+columns-1)/columns;
            size=Math.min(width/columns,height/rows);offsets=new double[count][2];
            for(int i=0;i<count;i++){offsets[i][0]=2*((i%columns+.5)/columns-.5);offsets[i][1]=2*(.5-(i/columns+.5)/rows);}
        }
        List<Cell> cells=new ArrayList<>(count);
        for(double[] placement:offsets)cells.add(new Cell(center.add(right.scale(placement[0]*width/2)).add(up.scale(placement[1]*height/2)),right,up,normal,size));
        return List.copyOf(cells);
    }
    public record Position(double u,double v) {}
    public record Frame(Cell center,double width,double height) {}
    public static Frame frame(BlockGetter level,BlockPos pos,Direction face){
        var old=cells(level,pos,face,1);if(old.isEmpty())return null;Cell c=old.getFirst();var shape=level.getBlockState(pos).getShape(level,pos);var bounds=shape.toAabbs().stream().filter(box->Math.abs(edge(box,face)-edge(shape.bounds(),face))<.0000001).max(java.util.Comparator.comparingDouble(box->extent(box,c.right())*extent(box,c.up()))).orElse(shape.bounds());
        double width=extent(bounds,c.right())*.94,height=extent(bounds,c.up())*.92;
        if(level.getBlockState(pos).getBlock() instanceof AbstractChestBlock<?> && !face.getAxis().isVertical())height=Math.min(height,.48);
        return new Frame(c,width,height);
    }
    public static List<Cell> placed(BlockGetter level,BlockPos pos,Direction face,List<Position> positions){
        Frame f=frame(level,pos,face);if(f==null)return List.of();var legacy=cells(level,pos,face,positions.size());
        double size=Math.min(com.cappleapple.astralrepository.AstralServerConfig.runeSize.get(),Math.min(f.width,f.height));
        for(int attempt=0;attempt<24;attempt++,size*=.9){List<Cell> result=new ArrayList<>();boolean fits=true;
            for(int i=0;i<positions.size();i++){Position p=positions.get(i);if(!Double.isFinite(p.u)||!Double.isFinite(p.v)){Vec3 d=legacy.get(i).center.subtract(f.center.center);p=new Position(d.dot(f.center.right),d.dot(f.center.up));}
                Position resolved=nearest(f,size,p,result);if(resolved==null){fits=false;break;}result.add(at(f,resolved,size));
            }if(fits)return List.copyOf(result);
        }return List.of();
    }
    public static List<Cell> placed(RuneSurface surface){return placed(surface.getLevel(),surface.getBlockPos(),surface.facing(),surface.layers().stream().map(l->new Position(l.u(),l.v())).toList());}
    private static Cell at(Frame f,Position p,double size){Cell c=f.center;return new Cell(c.center.add(c.right.scale(p.u)).add(c.up.scale(p.v)),c.right,c.up,c.normal,size);}
    public static Position nearest(Frame f,double size,Position wanted,List<Cell> occupied){
        double hx=(f.width-size)/2,hy=(f.height-size)/2;if(hx<0||hy<0)return null;
        List<Double> xs=new ArrayList<>(List.of(Math.clamp(wanted.u,-hx,hx),-hx,hx)),ys=new ArrayList<>(List.of(Math.clamp(wanted.v,-hy,hy),-hy,hy));
        for(Cell c:occupied){Vec3 d=c.center.subtract(f.center.center);double separation=(size+c.size)/2+.003;xs.add(Math.clamp(d.dot(c.right)-separation,-hx,hx));xs.add(Math.clamp(d.dot(c.right)+separation,-hx,hx));ys.add(Math.clamp(d.dot(c.up)-separation,-hy,hy));ys.add(Math.clamp(d.dot(c.up)+separation,-hy,hy));}
        Position best=null;double distance=Double.POSITIVE_INFINITY;
        for(double x:xs)for(double y:ys){boolean free=true;Cell candidate=at(f,new Position(x,y),size);for(Cell c:occupied){Vec3 d=candidate.center.subtract(c.center);double gap=(size+c.size)/2+.002;if(Math.abs(d.dot(c.right))<gap&&Math.abs(d.dot(c.up))<gap){free=false;break;}}double dd=(x-wanted.u)*(x-wanted.u)+(y-wanted.v)*(y-wanted.v);if(free&&dd<distance){distance=dd;best=new Position(x,y);}}
        return best;
    }
    public static Position placement(RuneSurface surface,Vec3 hit){Frame f=frame(surface.getLevel(),surface.getBlockPos(),surface.facing());if(f==null)return null;Vec3 d=hit.subtract(f.center.center);double size=Math.min(com.cappleapple.astralrepository.AstralServerConfig.runeSize.get(),Math.min(f.width,f.height));return nearest(f,size,new Position(d.dot(f.center.right),d.dot(f.center.up)),placed(surface));}
    public static int selectedIndex(List<Cell> cells,Vec3 hit){for(int i=0;i<cells.size();i++){Cell c=cells.get(i);Vec3 d=hit.subtract(c.center);if(Math.abs(d.dot(c.right))<=c.size/2&&Math.abs(d.dot(c.up))<=c.size/2&&Math.abs(d.dot(c.normal))<.035)return i;}return -1;}
    private static double edge(AABB box,Direction face){return switch(face){case WEST->box.minX;case EAST->box.maxX;case DOWN->box.minY;case UP->box.maxY;case NORTH->box.minZ;case SOUTH->box.maxZ;};}
    private static double extent(AABB box,Vec3 axis){return Math.abs(axis.x)*box.getXsize()+Math.abs(axis.y)*box.getYsize()+Math.abs(axis.z)*box.getZsize();}
    public static int selectedIndex(BlockGetter level,BlockPos pos,Direction face,Vec3 hit,int count){
        var cells=cells(level,pos,face,count);
        for(int i=0;i<cells.size();i++){var cell=cells.get(i);Vec3 delta=hit.subtract(cell.center());if(Math.abs(delta.dot(cell.right()))<=cell.size()*0.39&&Math.abs(delta.dot(cell.up()))<=cell.size()*0.39&&Math.abs(delta.dot(cell.normal()))<0.035)return i;}
        return -1;
    }
    private RuneLayout(){}
}
