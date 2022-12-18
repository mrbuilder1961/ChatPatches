package mechanicalarcane.wmch.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import mechanicalarcane.wmch.WMCH;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CopyMessageCommand {
    private static MinecraftClient client;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("copymessage")
                .then(
                    literal("index")
                        .then(
                            argument("message_index", IntegerArgumentType.integer(0, WMCH.config.maxMsgs))
                                .suggests(CopyMessageCommand::indexSuggestions)
                                .executes(CopyMessageCommand::executeIndex)
                        )
                )
                .then(
                    literal("help")
                        .executes(CopyMessageCommand::executeHelp)
                )
        );
    }

    private static int executeIndex(CommandContext<FabricClientCommandSource> context) {
        client = context.getSource().getClient();
        List<ChatHudLine> messages = Util.chatHud(client).getMessages();
        int i = context.getArgument("message_index", Integer.class);

        if(messages.size() > i) {
            String message = messages.get(i).content().getString();

            client.keyboard.setClipboard(message);
            context.getSource().sendFeedback(Text.translatable("text.wmch.copymessage.index", i, message));
            return 1;
        } else {
            context.getSource().sendError(Text.translatable("text.wmch.copymessage.index.missing", i));
            return 0;
        }
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.of("Command syntax: \"/copymessage index <message_index>\""));
        context.getSource().sendFeedback(Text.of("<message_index> is a number that represents a chat message, with zero being the most recent message."));
        context.getSource().sendFeedback(Text.of("For example, \"/copymessage index 6\" would copy the 7th message from the bottom."));

        return 1;
    }


    private static CompletableFuture<Suggestions> indexSuggestions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        client = context.getSource().getClient();
        List<ChatHudLine> messages = Util.chatHud(client).getMessages();

        // loops over each message for suggesting
        for(ChatHudLine line : messages)
            builder.suggest(messages.indexOf(line), line.content());

        return builder.buildFuture();
    }
}
