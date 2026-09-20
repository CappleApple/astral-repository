package com.cappleapple.astralrepository.content;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrystalNodeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);
    private final NodeKind kind;
    private final int tier;
    public CrystalNodeBlock(Properties properties, NodeKind kind, int tier) {
        super(properties); this.kind = kind; this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }
    public NodeKind kind() { return kind; }
    public int tier() { return tier; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite()); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return switch(state.getValue(FACING)){
        case DOWN->SHAPE;case UP->Block.box(2,1,2,14,16,14);
        case NORTH->Block.box(2,2,0,14,14,15);case SOUTH->Block.box(2,2,1,14,14,16);
        case WEST->Block.box(0,2,2,15,14,14);case EAST->Block.box(1,2,2,16,14,14);
    }; }
    @Override public BlockState rotate(BlockState state,net.minecraft.world.level.block.Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrystalNodeBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(PhysicalProgramming.use(player.getItemInHand(hand),level,pos,player,hand,hit))return InteractionResult.sidedSuccess(level.isClientSide);
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node) {
            if (kind == NodeKind.NEXUS || kind == NodeKind.STORAGE || kind == NodeKind.BUFFER) ContentHooks.openNexus.accept(serverPlayer, GlobalPos.of(level.dimension(), pos));
            else PhysicalProgramming.describe(serverPlayer, node);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack drop = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CrystalNodeBlockEntity node) {
            drop.getOrCreateTag().put("BlockEntityTag",node.saveWithFullMetadata());
        }
        return List.of(drop);
    }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node
                && node.hasInventory() && (node.inventory().used() > 0 || !node.tank().getFluid().isEmpty() || node.energy().getEnergyStored() > 0)) {
            ItemStack drop = new ItemStack(this);
            drop.getOrCreateTag().put("BlockEntityTag",node.saveWithFullMetadata());
            Block.popResource(level, pos, drop);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) ContentHooks.topologyChanged.accept(server, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}

