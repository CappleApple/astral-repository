package com.cappleapple.astralrepository.platform;
public final class RegisterPayloadHandlersEvent extends net.minecraftforge.eventbus.api.Event implements net.minecraftforge.fml.event.IModBusEvent {
 public PayloadRegistrar registrar(String version){return new PayloadRegistrar();}
}
