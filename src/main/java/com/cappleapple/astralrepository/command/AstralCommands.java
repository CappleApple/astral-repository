package com.cappleapple.astralrepository.command;
import com.cappleapple.astralrepository.network.NetworkManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
/** Read-only operator diagnostics; all programming remains in-world. */
public final class AstralCommands {
    public static void register(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("astral_repository").requires(s->s.hasPermission(2))
            .then(Commands.literal("inspect").then(Commands.argument("position",BlockPosArgument.blockPos()).executes(context->{
                var source=context.getSource();var pos=BlockPosArgument.getLoadedBlockPos(context,"position");
                var network=NetworkManager.get(source.getServer()).networkAt(GlobalPos.of(source.getLevel().dimension(),pos));
                if(network==null){source.sendFailure(Component.literal("No loaded crystal network at "+pos.toShortString()));return 0;}
                var items=network.snapshot();long total=0;for(long count:items.values())total=com.cappleapple.astralrepository.network.NetworkInventoryIndex.saturatingAdd(total,count);
                String report=network.status().replace('\n',' ')+"; "+items.size()+" item types; "+total+" items";
                source.sendSuccess(()->Component.literal(report),false);return 1;
            }))));
    }
    private AstralCommands(){}
}
