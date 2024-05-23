package tfar.nations2;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class Nations2ClientForge {

    public static void clientSetup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(Nations2ClientForge::registerClientCommands);
    }

    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("glow")
                .executes(Nations2ClientForge::toggleGlow));
    }

    private static int toggleGlow(CommandContext<CommandSourceStack> commandContext) {
        //NationsClient.toggleGlow();
        return 1;
    }

}
