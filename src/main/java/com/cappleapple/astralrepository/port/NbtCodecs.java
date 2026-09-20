package com.cappleapple.astralrepository.port;

import com.mojang.serialization.Codec;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.fluids.FluidStack;

/** Registry-aware codecs retaining the existing mod's compound field names. */
public final class NbtCodecs {
    public static ItemStack item(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    public static FluidStack fluid(HolderLookup.Provider registries, CompoundTag tag) {
        return FluidStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(FluidStack.EMPTY);
    }

    public static Tag save(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static Tag save(FluidStack stack, HolderLookup.Provider registries) {
        return FluidStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static <T extends SavedData> SavedDataType<T> savedType(String name, Supplier<T> constructor,
            HolderLookup.Provider registries, BiFunction<CompoundTag, HolderLookup.Provider, T> load,
            BiFunction<T, HolderLookup.Provider, CompoundTag> save) {
        Codec<T> codec = CompoundTag.CODEC.xmap(tag -> load.apply(tag, registries), value -> save.apply(value, registries));
        return new SavedDataType<>(Identifier.withDefaultNamespace(name), constructor, codec);
    }

    private NbtCodecs() {}
}
