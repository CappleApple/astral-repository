package com.cappleapple.astralrepository.platform;
public class AstralBlockItem extends net.minecraft.world.item.BlockItem {
 public AstralBlockItem(net.minecraft.world.level.block.Block block,Properties properties){super(block,properties);}
 @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){consumer.accept(com.cappleapple.astralrepository.client.AstralMineralClient.itemExtension());}
}
