package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.AstralConfig;
import com.cappleapple.astralrepository.content.*;
import com.cappleapple.astralrepository.network.NetworkManager;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class PowerGameTests {
    @GameTest(templateNamespace="astral_repository",template="empty_workshop",timeoutTicks=300,batch="astral_power")
    public static void physicalPowerNodePaysImmediatelyAndPreservesUnusedFuelCredit(GameTestHelper h) {
        BlockPos nexus=new BlockPos(4,2,4),bufferPos=nexus.east(2),powerPos=bufferPos.above();
        h.setBlock(nexus,AstralContent.STORAGE_NEXUS.get());
        h.setBlock(bufferPos,AstralContent.SEED_STORAGE_CRYSTAL.get());
        h.setBlock(powerPos,AstralContent.POWER_NODE.get());
        for(BlockPos pos:List.of(nexus,bufferPos,powerPos)) ((CrystalNodeBlockEntity)h.getBlockEntity(pos)).setChannel(13);
        var manager=NetworkManager.get(h.getLevel().getServer());
        var nexusNode=(CrystalNodeBlockEntity)h.getBlockEntity(nexus);
        manager.toggleLink(nexusNode.address(),((CrystalNodeBlockEntity)h.getBlockEntity(bufferPos)).address());
        manager.toggleLink(nexusNode.address(),((CrystalNodeBlockEntity)h.getBlockEntity(powerPos)).address());
        var buffer=(CrystalNodeBlockEntity)h.getBlockEntity(bufferPos);
        var origin=GlobalPos.of(h.getLevel().dimension(),h.absolutePos(nexus));
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"power-test"),ClientInformation.createDefault());
        h.succeedWhen(() -> {
            var network=NetworkManager.get(h.getLevel().getServer()).networkAt(origin);
            h.assertTrue(network!=null&&network.nodes().size()>=3,"Power Node joined the network");
            boolean enabled=AstralConfig.powerEnabled.get();
            String mode=AstralConfig.powerMode.get();
            List<? extends String> allowed=AstralConfig.powerProviders.get();
            double transfer=AstralConfig.transferCost.get();
            String fuel=AstralConfig.itemFuel.get();
            int fuelValue=AstralConfig.itemFuelValue.get();
            try {
                AstralConfig.transferCost.set(25D); AstralConfig.powerProviders.set(List.of("energy")); AstralConfig.powerMode.set("any");
                buffer.energy().extractEnergy(Integer.MAX_VALUE,false); buffer.energy().receiveEnergy(5000,false);
                AstralConfig.powerEnabled.set(false);
                h.assertTrue(network.payForAccess(player,origin,false),"Disabled power permits immediate operation");
                h.assertTrue(buffer.energy().getEnergyStored()==5000,"Disabled power consumes nothing");
                AstralConfig.powerEnabled.set(true);
                h.assertTrue(network.payForAccess(player,origin,false),"Attached FE powers the operation");
                h.assertTrue(buffer.energy().getEnergyStored()==4975,"Exactly the configured FE cost was paid");
                AstralConfig.powerProviders.set(List.of("source","energy"));
                h.assertTrue(network.payForAccess(player,origin,false),"OR falls back to available FE");
                h.assertTrue(buffer.energy().getEnergyStored()==4950,"OR charges only its selected resource");
                AstralConfig.powerMode.set("all");
                h.assertTrue(!network.payForAccess(player,origin,false),"AND rejects a missing Source supply");
                h.assertTrue(buffer.energy().getEnergyStored()==4950,"Rejected preflight consumes no FE");
                AstralConfig.powerProviders.set(List.of("item","energy")); AstralConfig.itemFuel.set("minecraft:amethyst_shard"); AstralConfig.itemFuelValue.set(1000);
                buffer.inventory().insertItem(buffer.inventory().getSlots()-1,new ItemStack(Items.AMETHYST_SHARD),false);
                h.assertTrue(network.payForAccess(player,origin,false),"One backing machine contributes independent item and FE supplies");
                h.assertTrue(buffer.energy().getEnergyStored()==4925,"AND pays the FE component");
                h.assertTrue(network.payForAccess(player,origin,false),"Unused item fuel credit pays the next operation");
                h.assertTrue(buffer.energy().getEnergyStored()==4900,"Second AND operation also pays its FE component");
                var powerNode=(CrystalNodeBlockEntity)h.getBlockEntity(powerPos);
                powerNode.getPersistentData().remove("AstralPowerCredit_item");
                AstralConfig.powerProviders.set(List.of("item")); AstralConfig.powerMode.set("any");
                AstralConfig.itemFuelValue.set(1); AstralConfig.transferCost.set(65D);
                buffer.inventory().insertItem(buffer.inventory().getSlots()-1,new ItemStack(Items.AMETHYST_SHARD,128),false);
                h.assertTrue(network.payForAccess(player,origin,false),"A single storage provider can pay more than one stack of fuel");
                h.assertTrue(buffer.inventory().getStackInSlot(0).getCount()==63,"Multi-stack fuel payment consumes exactly 65 of 128 shards");
                h.assertTrue(buffer.energy().getEnergyStored()==4900,"Item-only payment leaves FE untouched");
            } finally {
                AstralConfig.powerEnabled.set(enabled); AstralConfig.powerMode.set(mode); AstralConfig.powerProviders.set(allowed);
                AstralConfig.transferCost.set(transfer); AstralConfig.itemFuel.set(fuel); AstralConfig.itemFuelValue.set(fuelValue);
            }
        });
    }
}