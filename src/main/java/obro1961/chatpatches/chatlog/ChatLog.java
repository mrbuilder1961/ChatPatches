package obro1961.chatpatches.chatlog;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Text;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static java.io.File.separator;

/**
 * Represents the chat log file in the
 * run directory located at {@link ChatLog#CHATLOG_PATH}.
 */
public class ChatLog {
    public static final String CHATLOG_PATH = ChatPatches.FABRIC_LOADER.getGameDir().toString() + separator + "logs" + separator + "chatlog.json";
    private static final Path file = Path.of(CHATLOG_PATH);
    private static final Gson json = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(Text.class, (JsonSerializer<Text>) (src, type, context) -> Text.Serializer.toJsonTree(src))
        .registerTypeAdapter(Text.class, (JsonDeserializer<Text>) (json, type, context) -> Text.Serializer.fromJson(json))
        .registerTypeAdapter(Text.class, (InstanceCreator<Text>) type -> Text.empty())
    .create();

    private static boolean savedAfterCrash = false;
    private static ChatLog.Data data = new Data();

    public static boolean loaded = false;


    /** Micro class for serializing, used separately from ChatLog for simplification */
    private static class Data {
        public static final String EMPTY_DATA = "{\"history\":[],\"messages\":[]}"; // prevents a few errors if the channel doesn't initialize
        public static final int DEFAULT_SIZE = 100;

        public List<Text> messages;
        public List<String> history;

        private Data() {
            messages = Lists.newArrayListWithExpectedSize(DEFAULT_SIZE);
            history = Lists.newArrayListWithExpectedSize(DEFAULT_SIZE);
        }
    }


    /**
     * Deserializes the chat log from {@link #CHATLOG_PATH} and resolves message data from it.
     *
     * @implNote
     * <ol>
     *   <li> Checks if the file at {@link #CHATLOG_PATH} exists.
     *   <li> If it doesn't exist, {@code rawData} just uses {@link Data#EMPTY_DATA}.
     *   <li> If it does exist, it will convert the ChatLog file to UTF-8 if it isn't already and save it to {@code rawData}.
     *   <li> If {@code rawData} contains invalid data, resets {@link #data} to a default, empty {@link Data} object.
     *   <li> Then it uses {@link #json} to convert {@code rawData} into a usable {@link Data} object.
     *   <li> Runs {@link #enforceSizes()} to ensure that the {@link Data} object doesn't overflow with messages.
     *   <li> If it successfully resolved, then returns and logs a message.
     */
    public static void deserialize() {
        String rawData = Data.EMPTY_DATA;

        if( Files.exists(file) ) {

            try {
                rawData = Files.readString(file);

            } catch (MalformedInputException notUTF8) { // thrown if the file is not encoded with UTF-8
                ChatPatches.LOGGER.warn("[ChatLog.deserialize] ChatLog file encoding was '{}', not UTF-8. Complex text characters may have been replaced with question marks.", Charset.defaultCharset().name());

                try {
                    // force-writes the string as UTF-8
                    Files.writeString(file, new String(Files.readAllBytes(file)), StandardOpenOption.TRUNCATE_EXISTING);
                    rawData = Files.readString(file);

                } catch (IOException ioexc) {
                    ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't rewrite the ChatLog at '{}', resetting:", CHATLOG_PATH, ioexc);

                    // final attempt to reset the file
                    try {
                        rawData = Data.EMPTY_DATA; // just in case of corruption from previous failures
                        Files.writeString(file, Data.EMPTY_DATA, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException ioerr) {
                        ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't reset the ChatLog at '{}':", CHATLOG_PATH, ioerr);
                    }
                }

            } catch (IOException e) {
                ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't access the ChatLog at '{}':", CHATLOG_PATH, e);
                // rawData is EMPTY DATA
            }
        } else {
            data = new Data();
            loaded = true;
            return;
        }


        // if the file has invalid data (doesn't start with a '{'), reset it
        if( rawData.length() < 2 || !rawData.startsWith("{") ) {
            data = new Data();
            loaded = true;

            return;
        }

        try {
            data = json.fromJson(rawData, Data.class);
            enforceSizes();
        } catch (com.google.gson.JsonSyntaxException e) {
            ChatPatches.LOGGER.error("[ChatLog.deserialize] Tried to read the ChatLog and found an error, loading an empty one: ", e);

            data = new Data();
            loaded = true;
            return;
        }

        loaded = true;

        ChatPatches.LOGGER.info("[ChatLog.deserialize] Read the chat log containing {} messages and {} sent messages from '{}'",
			data.messages.size(), data.history.size(),
            CHATLOG_PATH
		);
    }

    public static void serialize(boolean crashing) {
        if(crashing && savedAfterCrash)
            return;

        try {
            enforceSizes();

            final String str = json.toJson(data, Data.class);
            Files.writeString(file, str, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            ChatPatches.LOGGER.info("[ChatLog.serialize] Saved the chat log containing {} messages and {} sent messages to '{}'", data.messages.size(), data.history.size(), CHATLOG_PATH);

        } catch (IOException e) {
            ChatPatches.LOGGER.error("[ChatLog.serialize] An I/O error occurred while trying to save the chat log:", e);

        } finally {
            if(crashing)
                savedAfterCrash = true;
        }
    }


    /** Removes all overflowing data from {@code ChatLog.data} with an index greater than {@link Config#maxMsgs}. */
    private static void enforceSizes() {
        if(data.messages.size() > ChatPatches.config.maxMsgs)
            data.messages = data.messages.subList(0, ChatPatches.config.maxMsgs + 1);

        if(data.history.size() > ChatPatches.config.maxMsgs)
            data.history = data.history.subList(0, ChatPatches.config.maxMsgs + 1);
    }

    public static void restore(MinecraftClient client) {
        Util.Flags.LOADING_CHATLOG.set();

        if(data.history.size() > 0)
            data.history.forEach(client.inGameHud.getChatHud()::addToMessageHistory);
        if(data.messages.size() > 0)
            data.messages.forEach(msg -> client.inGameHud.getChatHud().addMessage(
                msg, null, new MessageIndicator(0x382fb5, null, null, "Restored")
            ));

        Util.Flags.LOADING_CHATLOG.remove();
        ChatPatches.LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages from '{}' into Minecraft!", data.messages.size(), data.history.size(),
            CHATLOG_PATH);
    }


    public static void addMessage(Text msg) {
        if(data.messages.size() < ChatPatches.config.maxMsgs)
            data.messages.add(msg);
    }
    public static void addHistory(String msg) {
        if(data.history.size() < ChatPatches.config.maxMsgs)
            data.history.add(msg);
    }
    public static void clearMessages() { data.messages.clear(); }
    public static void clearHistory() { data.history.clear(); }
}
