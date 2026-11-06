package com.cappleapple.astralrepository.crafting;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeKnowledgeTest {
    @Test void nativeProfilesRetainTheirOriginalCanonicalRecipeForExclusion() {
        assertEquals(Identifier.parse("oritech:refinery/rawsheol/iron"), NativeRecipeKnowledge.canonicalId(
                "astral_repository:native/oritech/stable-profile/oritech/native/projected/oritech:refinery/rawsheol/iron"));
    }
    @Test void implicitServerRecipeIdRemainsStableWithoutARecipeManagerHolder() {
        assertEquals(Identifier.parse("astral_repository:implicit_conversion"), NativeRecipeKnowledge.canonicalId(
                "astral_repository:native/mod/profile/astral_repository:implicit_conversion"));
        assertEquals(Identifier.parse("mod:implicit_conversion"), NativeRecipeKnowledge.canonicalId("mod:implicit_conversion"));
    }
}
