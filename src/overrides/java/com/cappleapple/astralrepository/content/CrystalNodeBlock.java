package com.cappleapple.astralrepository.content;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CrystalNodeBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
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
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return switch(state.getValue(FACING)){
        case DOWN->SHAPE;case UP->Block.box(2,1,2,14,16,14);
        case NORTH->Block.box(2,2,0,14,14,15);case SOUTH->Block.box(2,2,1,14,14,16);
        case WEST->Block.box(0,2,2,15,14,14);case EAST->Block.box(1,2,2,16,14,14);
    }; }
    @Override protected BlockState rotate(BlockState state,net.minecraft.world.level.block.Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrystalNodeBlockEntity(pos, state); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (PhysicalProgramming.use(stack, level, pos, player, hand, hit)) return com.cappleapple.astralrepository.port.Interactions.sidedSuccess(level.isClientSide());
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node) {
            if (kind == NodeKind.NEXUS || kind == NodeKind.STORAGE || kind == NodeKind.BUFFER) ContentHooks.openNexus.accept(serverPlayer, GlobalPos.of(level.dimension(), pos));
            else PhysicalProgramming.describe(serverPlayer, node);
        }
        return com.cappleapple.astralrepository.port.Interactions.sidedSuccess(level.isClientSide());
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack drop = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CrystalNodeBlockEntity node) {
            drop.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(node.getType(),node.saveWithFullMetadata(params.getLevel().registryAccess())));
        }
        return List.of(drop);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof CrystalNodeBlockEntity node
                && node.hasInventory() && (node.inventory().used() > 0 || !node.tank().getFluid().isEmpty() || node.energy().getEnergyStored() > 0)) {
            ItemStack drop = new ItemStack(this);
            drop.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(node.getType(),node.saveWithFullMetadata(level.registryAccess())));
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        ContentHooks.topologyChanged.accept(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }
}
