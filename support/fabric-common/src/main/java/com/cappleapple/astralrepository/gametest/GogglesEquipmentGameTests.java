package com.cappleapple.astralrepository.gametest;

import com.cappleapple.astralrepository.content.AstralContent;
import com.cappleapple.astralrepository.content.GogglesEquipment;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("astral_repository")
@PrefixGameTestTemplate(false)
public final class GogglesEquipmentGameTests {
    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void gogglesRequireEquipmentAndKeepVanillaHeadSupport(GameTestHelper h) {
        var player = player(h);
        var goggles = new ItemStack(AstralContent.RESONANCE_GOGGLES.get());
        h.assertTrue(!GogglesEquipment.isWearing(player), "An unequipped player has no goggles visibility");
        player.setItemInHand(InteractionHand.MAIN_HAND, goggles.copy());
        player.setItemInHand(InteractionHand.OFF_HAND, goggles.copy());
        player.getInventory().setItem(9, goggles.copy());
        h.assertTrue(!GogglesEquipment.isWearing(player), "Carrying goggles does not confer worn visibility");
        player.setItemSlot(EquipmentSlot.HEAD, goggles.copy());
        h.assertTrue(GogglesEquipment.isWearing(player), "Vanilla head equipment grants goggles visibility");
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        h.assertTrue(!GogglesEquipment.isWearing(player), "Replacing the goggles immediately removes visibility");
        h.succeed();
    }

    @GameTest(templateNamespace="astral_repository", template="empty_workshop")
    public static void optionalCuriosHeadSlotIsFunctionalAndCosmeticsDoNotGrantVisibility(GameTestHelper h) {
        if (!ModList.get().isLoaded("curios")) {
            h.assertTrue(!Boolean.getBoolean("astral_repository.curiosTest"), "Curios gate requires Curios to be installed");
            h.succeed();
            return;
        }
        CuriosAssertions.verify(h, player(h));
        h.succeed();
    }

    private static FakePlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "AstralGogglesTest"));
    }

    private static final class CuriosAssertions {
        private static void verify(GameTestHelper h, FakePlayer player) {
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow();
            var head = inventory.getStacksHandler("head").orElseThrow();
            var goggles = new ItemStack(AstralContent.RESONANCE_GOGGLES.get());
            h.assertTrue(head.getStacks().getSlots() >= 1, "Datapack provides a player Curios head slot");
            h.assertTrue(head.getStacks().isItemValid(0, goggles), "The goggles' Curios head tag permits equipping");
            h.assertTrue(top.theillusivec4.curios.api.CuriosApi.getCurio(goggles).isPresent(), "Optional item capability is registered");
            head.getCosmeticStacks().setStackInSlot(0, goggles.copy());
            h.assertTrue(!GogglesEquipment.isWearing(player), "Cosmetic goggles do not grant functional visibility");
            head.getCosmeticStacks().setStackInSlot(0, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            head.getStacks().setStackInSlot(0, goggles.copy());
            h.assertTrue(GogglesEquipment.isWearing(player), "Curios goggles grant visibility while vanilla helmet is equipped");
            head.getRenders().set(0, false);
            h.assertTrue(GogglesEquipment.isWearing(player), "Hiding the worn model does not disable functional goggles");
            head.getActiveStates().set(0, false);
            h.assertTrue(!GogglesEquipment.isWearing(player), "Inactive Curios slots do not grant visibility");
            head.getActiveStates().set(0, true);
            head.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            h.assertTrue(!GogglesEquipment.isWearing(player), "Unequipping Curios goggles immediately removes visibility");
        }
    }

    private GogglesEquipmentGameTests() {}
}
