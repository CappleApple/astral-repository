package com.cappleapple.astralrepository.platform.capabilities;
import com.cappleapple.astralrepository.content.AstralContent;
public final class NativeNodeCapabilities {public static void register(){Capabilities.ItemHandler.BLOCK.register(AstralContent.NODE_ENTITY.get(),(node,side)->node.hasInventory()?node.inventory():null);Capabilities.FluidHandler.BLOCK.register(AstralContent.NODE_ENTITY.get(),(node,side)->node.hasInventory()?node.tank():null);Capabilities.EnergyStorage.BLOCK.register(AstralContent.NODE_ENTITY.get(),(node,side)->node.hasInventory()?node.energy():null);}}
