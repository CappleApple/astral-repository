package com.cappleapple.astralrepository.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;


import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class AstralMineralItemRenderer implements net.minecraft.client.renderer.special.SpecialModelRenderer<ItemStack> {
    private final net.minecraft.resources.Identifier item;
    private final boolean head;
    public AstralMineralItemRenderer() { this(null,false); }
    private AstralMineralItemRenderer(net.minecraft.resources.Identifier item,boolean head) { this.item=item; this.head=head; }
    public record Unbaked(net.minecraft.resources.Identifier item,boolean head) implements net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked<ItemStack>{
        public static final com.mojang.serialization.MapCodec<Unbaked> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance->instance.group(
            net.minecraft.resources.Identifier.CODEC.fieldOf("item").forGetter(Unbaked::item),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("head",false).forGetter(Unbaked::head)).apply(instance,Unbaked::new));
        @Override public AstralMineralItemRenderer bake(net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext context){return new AstralMineralItemRenderer(item,head);}
        @Override public com.mojang.serialization.MapCodec<Unbaked> type(){return CODEC;}
    }
    @Override public ItemStack extractArgument(ItemStack stack){return stack.copy();}
    @Override public void getExtents(java.util.function.Consumer<org.joml.Vector3fc> out){
        if(item==null){out.accept(new org.joml.Vector3f(0));out.accept(new org.joml.Vector3f(1));return;}
        var key=item.getPath().equals("resonance_goggles")?AstralMineralClient.gogglesModel(head?ItemDisplayContext.HEAD:ItemDisplayContext.NONE):AstralModels.Key.standalone(item.withPath("item/"+item.getPath()+"_geometry"));
        var model=AstralModels.get(key);var random=net.minecraft.util.RandomSource.create(42);
        for(var side:java.util.Arrays.asList(null,net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST))
            for(var quad:model.getQuads(null,side,random))for(int i=0;i<4;i++)out.accept(new org.joml.Vector3f(quad.position(i)));
    }
    @Override public void submit(ItemStack stack,PoseStack poses,net.minecraft.client.renderer.SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){
        var buffers=new AstralBufferSource();renderByItem(stack,head?ItemDisplayContext.HEAD:ItemDisplayContext.NONE,new PoseStack(),buffers,light,overlay);
        if(foil&&(stack.is(com.cappleapple.astralrepository.content.AstralContent.ATTUNEMENT_WAND.get())||stack.is(com.cappleapple.astralrepository.content.AstralContent.ASTRAL_NEXUS.get())||stack.is(com.cappleapple.astralrepository.content.AstralContent.RESONANCE_GOGGLES.get())))buffers.addFoil();
        buffers.submit(poses,collector);
    }
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poses, AstralBufferSource buffers, int light, int overlay) {
        var client = Minecraft.getInstance();
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof com.cappleapple.astralrepository.content.CrystalNodeBlock) {
            var block=(com.cappleapple.astralrepository.content.CrystalNodeBlock)blockItem.getBlock();var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);String upgrade=null;
            if(data!=null){var tag=data.copyTagWithoutId();upgrade=CrystalModelRenderer.upgradedModel(block.kind(),tag.getIntOr("StorageTier",0),tag.getBooleanOr("LongRange",false),tag.getBooleanOr("Dimensional",false));}
            var model=AstralModels.get(upgrade==null?CrystalModelRenderer.modelLocation(block):CrystalModelRenderer.modelLocation(upgrade));
            CrystalModelRenderer.render(poses, buffers, null, model, CrystalModelRenderer.channel(stack), light, overlay);
            return;
        }
        boolean goggles = stack.is(com.cappleapple.astralrepository.content.AstralContent.RESONANCE_GOGGLES.get());
        boolean wand = stack.is(com.cappleapple.astralrepository.content.AstralContent.ATTUNEMENT_WAND.get());
        boolean remote = stack.is(com.cappleapple.astralrepository.content.AstralContent.ASTRAL_NEXUS.get());
        var location = goggles ? AstralMineralClient.gogglesModel(context) : wand ? AstralMineralClient.WAND_MODEL : remote ? AstralMineralClient.REMOTE_MODEL : stack.getItem() instanceof BlockItem
                ? AstralModels.Key.standalone(net.minecraft.resources.Identifier.fromNamespaceAndPath("astral_repository",
                        "item/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + "_geometry"))
                : AstralMineralClient.GEM_MODEL;
        var model = AstralModels.get(location);
        var material = AstralPlaneRenderType.ready() ? ((wand || remote || goggles) ? AstralPlaneRenderType.ASTRAL_OVERLAY : stack.is(com.cappleapple.astralrepository.content.AstralContent.ASTRAL_GEM.get()) ? AstralPlaneRenderType.GEM_ITEM : AstralPlaneRenderType.ASTRAL_PLANE) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        if (!wand && !goggles) {
            for(var face:java.util.Arrays.asList(null,net.minecraft.core.Direction.UP,net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.WEST))for(var quad:model.getQuads(null,face,net.minecraft.util.RandomSource.create(42)))buffers.getBuffer(AstralPlaneRenderType.withAtlas(material,quad.materialInfo().sprite().atlasLocation())).putBulkData(poses.last(),quad,1,1,1,1,light,overlay);
            return;
        }
        // Tint index 0 marks the wand crystal or goggles lenses; frames retain their base material.
        if (goggles) material = AstralPlaneRenderType.ready() ? AstralPlaneRenderType.GOGGLES_LENS
                : net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        var random = net.minecraft.util.RandomSource.create(42);
        // Submit opaque frames before transparent lenses; lenses never write depth.
        for (int pass = 0; pass < 2; pass++) {
            for (int face = 0; face <= net.minecraft.core.Direction.values().length; face++) {
                var direction = face == net.minecraft.core.Direction.values().length ? null : net.minecraft.core.Direction.values()[face];
                random.setSeed(42);
                for (var quad : model.getQuads(null, direction, random)) {
                    boolean crystal = quad.materialInfo().isTinted() && quad.materialInfo().tintIndex() == 0;
                    if (crystal != (pass == 1)) continue;
                    var consumer = buffers.getBuffer(crystal ? AstralPlaneRenderType.withAtlas(material,quad.materialInfo().sprite().atlasLocation()) : net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(quad.materialInfo().sprite().atlasLocation()));
                    consumer.putBulkData(poses.last(), quad, 1, 1, 1, goggles && crystal ? 0.55F : 1F, light, overlay);
                }
            }
        }
    }
}


