package com.cappleapple.astralrepository.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceTrailScheduleTest {
    private static List<Double> releases(long seed){
        var schedule=new ResourceTrailSchedule(100,seed);
        var releases=new ArrayList<Double>();
        for(int tick=100;tick<200;tick++){
            double release=schedule.poll(tick);
            if(!Double.isNaN(release)){
                assertTrue(release<=tick&&release>=tick-1);
                releases.add(release);
            }
        }
        return releases;
    }
    @Test void batchesHaveIndependentSubTickReleasePositionsAndIrregularIntervals(){
        var a=releases(7);
        assertEquals(a,releases(7));
        assertNotEquals(a,releases(8));
        assertTrue(a.size()>=30&&a.size()<=70);
        assertTrue(a.stream().allMatch(time->time!=Math.floor(time)));
        var intervals=new java.util.HashSet<Double>();
        for(int i=1;i<a.size();i++){
            double interval=a.get(i)-a.get(i-1);
            assertTrue(interval>=1&&interval<=3);
            intervals.add(interval);
        }
        assertTrue(intervals.size()>10);
    }
    @Test void resumingAfterSkippedTicksDoesNotReleaseAnOldBacklog(){
        var schedule=new ResourceTrailSchedule(0,7);
        assertTrue(schedule.poll(1000)>=999);
        assertTrue(Double.isNaN(schedule.poll(1000)));
    }
}
