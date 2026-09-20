package com.cappleapple.astralrepository.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** One dimension's player positions, snapshotted once per server tick without loading chunks. */
final class VisualAudienceIndex<T> {
    // Includes the spline's bend, rune offsets and configurable two-block variation.
    private static final double RANGE = 132;
    private static final int CELL_SIZE = 128;
    record Observer<T>(T value, Vec3 position) {}
    private final List<Observer<T>> observers;
    private final Map<Long, List<Observer<T>>> cells = new HashMap<>();
    private double minObserverX=Double.POSITIVE_INFINITY,minObserverY=minObserverX,minObserverZ=minObserverX;
    private double maxObserverX=Double.NEGATIVE_INFINITY,maxObserverY=maxObserverX,maxObserverZ=maxObserverX;

    VisualAudienceIndex(List<Observer<T>> observers) {
        this.observers = List.copyOf(observers);
        for (var observer : observers) {
            cells.computeIfAbsent(key(cell(observer.position.x), cell(observer.position.z)), k -> new ArrayList<>()).add(observer);
            var p=observer.position;
            minObserverX=Math.min(minObserverX,p.x);maxObserverX=Math.max(maxObserverX,p.x);
            minObserverY=Math.min(minObserverY,p.y);maxObserverY=Math.max(maxObserverY,p.y);
            minObserverZ=Math.min(minObserverZ,p.z);maxObserverZ=Math.max(maxObserverZ,p.z);
        }
    }

    List<T> nearby(List<BlockPos> route) {
        if (observers.isEmpty()) return List.of();
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX;
        double maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(int i=0;i<route.size();i++){
            var point=route.get(i);
            minX=Math.min(minX,point.getX()+.5);maxX=Math.max(maxX,point.getX()+.5);
            minY=Math.min(minY,point.getY()+.5);maxY=Math.max(maxY,point.getY()+.5);
            minZ=Math.min(minZ,point.getZ()+.5);maxZ=Math.max(maxZ,point.getZ()+.5);
        }
        minX-=RANGE;minY-=RANGE;minZ-=RANGE;maxX+=RANGE;maxY+=RANGE;maxZ+=RANGE;
        // The expanded route bounds are conservative, including observers beside intermediate legs.
        if(maxX<minObserverX||minX>maxObserverX||maxY<minObserverY||minY>maxObserverY||maxZ<minObserverZ||minZ>maxObserverZ)return List.of();
        int x0=cell(minX),x1=cell(maxX),z0=cell(minZ),z1=cell(maxZ);
        long visits=(long)(x1-x0+1)*(z1-z0+1);
        List<T> result=null;
        // Never walk millions of empty grid cells for a long-range/dimensional route.
        if(visits>observers.size()){
            for(int i=0;i<observers.size();i++){
                var observer=observers.get(i);
                if(withinBounds(observer.position,minX,minY,minZ,maxX,maxY,maxZ)&&nearRoute(observer.position,route)){
                    if(result==null)result=new ArrayList<>();result.add(observer.value);
                }
            }
        }else{
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
                var bucket=cells.get(key(x,z));if(bucket==null)continue;
                for(int i=0;i<bucket.size();i++){
                    var observer=bucket.get(i);
                    if(withinBounds(observer.position,minX,minY,minZ,maxX,maxY,maxZ)&&nearRoute(observer.position,route)){
                        if(result==null)result=new ArrayList<>();result.add(observer.value);
                    }
                }
            }
        }
        return result==null?List.of():List.copyOf(result);
    }
    private static boolean withinBounds(Vec3 point,double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        return point.x>=minX&&point.x<=maxX&&point.y>=minY&&point.y<=maxY&&point.z>=minZ&&point.z<=maxZ;
    }

    static boolean nearRoute(Vec3 observer, List<BlockPos> route) {
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i - 1); var b = route.get(i);
            double ax = a.getX() + .5, ay = a.getY() + .5, az = a.getZ() + .5;
            double dx = (double) b.getX() - a.getX(), dy = (double) b.getY() - a.getY(), dz = (double) b.getZ() - a.getZ();
            double px = observer.x - ax, py = observer.y - ay, pz = observer.z - az;
            double length = dx * dx + dy * dy + dz * dz;
            double t = length == 0 ? 0 : com.cappleapple.astralrepository.platform.Backport.clamp((px * dx + py * dy + pz * dz) / length, 0, 1);
            px -= dx * t; py -= dy * t; pz -= dz * t;
            if (px * px + py * py + pz * pz < RANGE * RANGE) return true;
        }
        return false;
    }

    private static int cell(double value) { return (int) Math.floor(value / CELL_SIZE); }
    private static long key(int x, int z) { return (long) x << 32 ^ (z & 0xffffffffL); }
}
