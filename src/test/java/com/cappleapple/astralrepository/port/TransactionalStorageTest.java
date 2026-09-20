package com.cappleapple.astralrepository.port;

import com.cappleapple.astralrepository.content.CapacityInventory;
import com.cappleapple.astralrepository.content.ContentHooks;
import com.cappleapple.astralrepository.compat.StacksNotSlotsCapacity.Fraction;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionalStorageTest {
    @BeforeAll static void bootstrap(){com.cappleapple.astralrepository.port.PortTestBootstrap.initialize();}
    @Test void itemRollbackRestoresExactCapacityAndRevision(){
        var old=ContentHooks.capacityCostExact;var changed=new AtomicInteger();
        ContentHooks.capacityCostExact=stack->new Fraction(BigInteger.valueOf(2),BigInteger.valueOf(3));
        try{
            var items=new CapacityInventory(()->2L,changed::incrementAndGet);var iron=ItemResource.of(Items.IRON_INGOT);
            assertEquals(Integer.MAX_VALUE,items.getCapacityAsLong(0,ItemResource.EMPTY));
            try(var root=Transaction.openRoot()){
                assertThrows(IllegalArgumentException.class,()->items.insert(0,ItemResource.EMPTY,1,root));
                assertThrows(IllegalArgumentException.class,()->items.extract(0,iron,-1,root));
                assertEquals(3,items.insert(0,iron,6,root));assertEquals(2,items.used());assertEquals(0,changed.get());
                try(var nested=Transaction.open(root)){assertEquals(2,items.extract(0,iron,2,nested));nested.commit();}
                assertEquals(1,items.getAmountAsLong(0));
            }
            assertEquals(0,items.used());assertEquals(0,items.revision());assertEquals(0,items.getAmountAsLong(0));assertEquals(0,changed.get());
            try(var root=Transaction.openRoot()){assertEquals(3,items.insert(0,iron,3,root));root.commit();}
            assertEquals(2,items.used());assertEquals(1,changed.get());
        }finally{ContentHooks.capacityCostExact=old;}
    }
    @Test void committedNestedFluidTransferStillRollsBackWithParent(){
        var changed=new AtomicInteger();var tank=new AstralFluidTank(1000,changed::incrementAndGet);var water=FluidResource.of(Fluids.WATER);
        assertEquals(1000,tank.getCapacityAsLong(0,FluidResource.EMPTY));
        try(var root=Transaction.openRoot()){
            assertThrows(IllegalArgumentException.class,()->tank.insert(0,FluidResource.EMPTY,1,root));
            assertThrows(IllegalArgumentException.class,()->tank.extract(0,water,-1,root));
            assertEquals(800,tank.insert(0,water,800,root));
            try(var nested=Transaction.open(root)){assertEquals(200,tank.extract(0,water,200,nested));nested.commit();}
            assertEquals(600,tank.getAmountAsLong(0));
        }
        assertEquals(0,tank.getAmountAsLong(0));assertEquals(0,changed.get());
        try(var root=Transaction.openRoot()){assertEquals(1000,tank.insert(0,water,1200,root));root.commit();}
        assertEquals(1000,tank.getAmountAsLong(0));assertEquals(1,changed.get());
    }
    @Test void abortedNestedEnergyTransferLeavesCommittedParentIntact(){
        var changed=new AtomicInteger();var energy=new AstralEnergyStorage(1000,1000,changed::incrementAndGet);
        try(var root=Transaction.openRoot()){
            assertEquals(750,energy.insert(750,root));
            try(var nested=Transaction.open(root)){assertEquals(500,energy.extract(500,nested));}
            assertEquals(750,energy.getAmountAsLong());assertEquals(0,changed.get());root.commit();
        }
        assertEquals(750,energy.getAmountAsLong());assertEquals(1,changed.get());
        try(var root=Transaction.openRoot()){assertEquals(750,energy.extract(1000,root));}
        assertEquals(750,energy.getAmountAsLong());assertEquals(1,changed.get());
    }
}
