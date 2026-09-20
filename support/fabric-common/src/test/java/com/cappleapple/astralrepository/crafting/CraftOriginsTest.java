package com.cappleapple.astralrepository.crafting;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class CraftOriginsTest {
    @Test void ingredientsAndIntermediatesKeepTheirPhysicalOrigins(){var origins=new CraftOrigins<String,String>();origins.add("iron",6,"chest");origins.add("iron",2,"furnace");assertEquals(Map.of("chest",4L),origins.take("iron",4,"nexus"));assertEquals(Map.of("chest",2L,"furnace",2L),origins.take("iron",4,"nexus"));assertTrue(origins.positions("iron").isEmpty());origins.add("trapdoor",1,"table");assertEquals(Map.of("table",1L),origins.take("trapdoor",1,"nexus"));}
    @Test void oldRecoveryWithoutLocationsHasAnExplicitFallback(){var origins=new CraftOrigins<String,String>();assertEquals(Map.of("nexus",3L),origins.take("iron",3,"nexus"));}
}
