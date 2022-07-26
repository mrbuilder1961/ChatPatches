package obro1961.wmch.util;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import obro1961.wmch.config.Option;
import obro1961.wmch.mixins.ChatHudAccessorMixin;

public class CopyMessageCommand {
    private static MinecraftClient client;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                literal("copymessage")
                        .then(
                                literal("index")
                                        .then(
                                                argument("message_index",
                                                        IntegerArgumentType.integer(0, Option.MAX_MESSAGES.get()))
                                                        .suggests(CopyMessageCommand::indexSuggestions)
                                                        .executes(CopyMessageCommand::executeIndex)))
                        .then(
                                literal("help")
                                        .executes(CopyMessageCommand::executeHelp)));
    }

    private static int executeIndex(CommandContext<FabricClientCommandSource> context) {
        client = context.getSource().getClient();
        List<ChatHudLine<Text>> messages = ((ChatHudAccessorMixin) client.inGameHud.getChatHud()).getMessages();
        int i = context.getArgument("message_index", Integer.class);

        if(messages.size() > i) {
            String message = messages.get(i).getText().getString();

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
        context.getSource().sendFeedback(Text.of(
                "<message_index> is a number that represents a chat message, with zero being the most recent message."));
        context.getSource().sendFeedback(
                Text.of("For example, \"/copymessage index 6\" would copy the 7th message from the bottom."));

        return 1;
    }

    private static CompletableFuture<Suggestions> indexSuggestions(CommandContext<FabricClientCommandSource> context,
            SuggestionsBuilder builder) {
        client = context.getSource().getClient();
        List<ChatHudLine<Text>> messages = ((ChatHudAccessorMixin) client.inGameHud.getChatHud()).getMessages();

        // loops over each message for suggesting
        for(ChatHudLine<Text> line : messages)
            builder.suggest(messages.indexOf(line), line.getText());

        return builder.buildFuture();
    }
}
