package com.cappleapple.astralrepository.platform;
public class AstralItem extends net.minecraft.world.item.Item {
 public AstralItem(Properties properties){super(properties);}
 @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){consumer.accept(com.cappleapple.astralrepository.client.AstralMineralClient.itemExtension());}
}
