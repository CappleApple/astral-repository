package com.cappleapple.astralrepository.smoke;
import com.cappleapple.astralrepository.api.*;
import java.util.Map;
public final class RuneTransitSmoke {
 public static void run(net.minecraft.server.MinecraftServer server)throws Exception {
        RuneTransitGameTests.noninstantPushHoldsAllFourResourcesUntilTheirArrival(new GameTestHelper(server.overworld(), 0));
        RuneTransitGameTests.noninstantPullHoldsAllFourResourcesUntilTheirArrival(new GameTestHelper(server.overworld(), 1));
        RuneTransitGameTests.instantPushAndPullCommitAllFourResourcesOnDispatch(new GameTestHelper(server.overworld(), 2));
        RuneTransitGameTests.oneTickCadencePipelinesThreeBatchesWithoutFinishingThemEarly(new GameTestHelper(server.overworld(), 3));
        RuneTransitGameTests.stockTargetsCountAllInFlightReservationsBeforeSchedulingMore(new GameTestHelper(server.overworld(), 4));
        RuneTransitGameTests.multipleRunesShareDestinationStockReservationsForEveryMedium(new GameTestHelper(server.overworld(), 5));
        RuneTransitGameTests.fullDestinationRefundsAllFourOwnedBatchesAtArrival(new GameTestHelper(server.overworld(), 6));
        RuneTransitGameTests.removedDestinationRefundsOwnedBatchesWithoutUsingStaleCapabilities(new GameTestHelper(server.overworld(), 7));
        RuneTransitGameTests.savedFlightsPreserveEveryMediumAndRemainingTimeAcrossClockReset(new GameTestHelper(server.overworld(), 8));
        RuneTransitGameTests.uncertainArrivalIsRecordedWithoutRefundOrReplay(new GameTestHelper(server.overworld(), 9));
        RuneAggregateTransitGameTests.networkRunesUseActualStorageTravelTimesAndKeepPolicyAcrossRebuilds(new GameTestHelper(server.overworld(), 10));
        RuneAggregateTransitGameTests.perCallEnergyAcceptanceDoesNotSerializeOneTickFlights(new GameTestHelper(server.overworld(), 11));
  com.cappleapple.astralrepository.AstralRepository.LOGGER.info("ASTRAL_RUNE_TRANSIT_SMOKE_PASS: 12 regression scenarios; native items, fluids and energy; Source adapter; delayed and instant Push/Pull; 1-tick pipelines; shared stock reservations; removed/full destinations; persistence; uncertainty; aggregate physical paths and policies across rebuilds");
 }
 public static ResourceProvider scalar(String id,long[] amounts,int index){return new ResourceProvider(){
 public String id(){return id;}public Object identity(){return id;}public boolean valid(){return true;}public long capacity(){return 10000;}public net.minecraft.resources.Identifier resourceType(){return ResourceKinds.SOURCE;}
 public Map<ResourceKey,Long> snapshot(){return Map.of(ResourceKinds.ARS_SOURCE,amounts[index]);}
 public long insert(ResourceKey key,long n,boolean simulate){long accepted=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,10000-amounts[index]):0;if(!simulate)amounts[index]+=accepted;return accepted;}
 public long extract(ResourceKey key,long n,boolean simulate){long taken=key.equals(ResourceKinds.ARS_SOURCE)?Math.min(n,amounts[index]):0;if(!simulate)amounts[index]-=taken;return taken;}
 };}
 private RuneTransitSmoke(){}
}
