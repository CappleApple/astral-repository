package com.cappleapple.astralrepository.port;

import com.cappleapple.astralrepository.content.AstralContent;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockItemLocalizationTest {
    @BeforeAll static void bootstrap(){PortTestBootstrap.initialize();}
    @Test void everyRegisteredBlockItemUsesItsBlockTranslation() throws Exception {
        try(var input=getClass().getResourceAsStream("/assets/astral_repository/lang/en_us.json")){
            assertNotNull(input);
            var translations=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(10,AstralContent.BLOCKS.getEntries().size());
            for(var entry:AstralContent.BLOCKS.getEntries()){
                String key="block.astral_repository."+entry.getId().getPath();
                assertTrue(translations.has(key),key);
                assertEquals(key,entry.get().asItem().getDescriptionId(),entry.getId().toString());
                assertEquals(entry.get().getName(),new ItemStack(entry.get()).getHoverName(),entry.getId().toString());
            }
        }
    }
}
