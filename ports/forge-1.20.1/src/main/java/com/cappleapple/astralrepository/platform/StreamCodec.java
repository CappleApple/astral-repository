package com.cappleapple.astralrepository.platform;
public record StreamCodec<B,T>(java.util.function.BiConsumer<B,T> writer, java.util.function.Function<B,T> reader) {
 public static <B,T> StreamCodec<B,T> of(java.util.function.BiConsumer<B,T> writer,java.util.function.Function<B,T> reader){return new StreamCodec<>(writer,reader);}
 public void encode(B buffer,T value){writer.accept(buffer,value);} public T decode(B buffer){return reader.apply(buffer);}
}
