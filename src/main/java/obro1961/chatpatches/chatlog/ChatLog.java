package obro1961.chatpatches.chatlog;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Flags;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static obro1961.chatpatches.ChatPatches.config;

/**
 * Represents the chat log file in the run directory located at {@link ChatLog#PATH}.
 */
public class ChatLog {
    public static final Path PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatlog.json");
    public static final MessageIndicator RESTORED_TEXT = new MessageIndicator(0x382fb5, null, null, I18n.translate("text.chatpatches.restored"));

    private static final Gson GSON = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(Text.class, (JsonSerializer<Text>) (src, type, context) -> Text.Serialization.toJsonTree(src))
        .registerTypeAdapter(Text.class, (JsonDeserializer<Text>) (json, type, context) -> Text.Serialization.fromJsonTree(json))
        .registerTypeAdapter(Text.class, (InstanceCreator<Text>) type -> Text.empty())
    .create();

    private static boolean savedAfterCrash = false;
    private static ChatLog.Data data = new Data();
    private static int lastHistoryCount = -1, lastMessageCount = -1;

    public static boolean loaded = false;
    public static int ticksUntilSave = config.chatlogSaveInterval * 60 * 20; // convert minutes to ticks


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
     * Deserializes the chat log from {@link #PATH} and resolves message data from it.
     *
     * @implNote
     * <ol>
     *   <li> Checks if the file at {@link #PATH} exists.
     *   <li> If it doesn't exist, {@code rawData} just uses {@link Data#EMPTY_DATA}.
     *   <li> If it does exist, it will convert the ChatLog file to UTF-8 if it isn't already and save it to {@code rawData}.
     *   <li> If {@code rawData} contains invalid data, resets {@link #data} to a default, empty {@link Data} object.
     *   <li> Then it uses {@link #GSON} to convert {@code rawData} into a usable {@link Data} object.
     *   <li> Removes any overflowing messages.
     *   <li> If it successfully resolved, then returns and logs a message.
     */
    public static void deserialize() {
        String rawData = Data.EMPTY_DATA;

        if(Files.exists(PATH)) {
            try {
                rawData = Files.readString(PATH);

            } catch(MalformedInputException notUTF8) { // thrown if the file is not encoded with UTF-8
                ChatPatches.LOGGER.warn("[ChatLog.deserialize] ChatLog file encoding was '{}', not UTF-8. Complex text characters may have been replaced with question marks.", Charset.defaultCharset().name());

                try {
                    // force-writes the string as UTF-8
                    Files.writeString(PATH, new String(Files.readAllBytes(PATH)), StandardOpenOption.TRUNCATE_EXISTING);
                    rawData = Files.readString(PATH);

                } catch(IOException ioexc) {
                    ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't rewrite the ChatLog at '{}', resetting:", PATH, ioexc);

                    // final attempt to reset the file
                    try {
                        rawData = Data.EMPTY_DATA; // just in case of corruption from previous failures
                        Files.writeString(PATH, Data.EMPTY_DATA, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch(IOException ioerr) {
                        ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't reset the ChatLog at '{}':", PATH, ioerr);
                    }
                }

            } catch(IOException e) {
                ChatPatches.LOGGER.error("[ChatLog.deserialize] Couldn't access the ChatLog at '{}':", PATH, e);
                rawData = Data.EMPTY_DATA; // just in case of corruption from failures
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
            data = GSON.fromJson(rawData, Data.class);
            removeOverflowData();
        } catch(com.google.gson.JsonSyntaxException e) {
            ChatPatches.LOGGER.error("[ChatLog.deserialize] Tried to read the ChatLog and found an error, loading an empty one: ", e);

            data = new Data();
            loaded = true;
            return;
        }

        loaded = true;

        ChatPatches.LOGGER.info("[ChatLog.deserialize] Read the chat log containing {} messages and {} sent messages from '{}'",
			data.messages.size(), data.history.size(),
            PATH
		);
    }

    /**
     * Saves the chat log to {@link #PATH}. Only saves if {@link Config#chatlog} is true,
     * it isn't crashing again, and if there is *new* data to save.
     *
     * @param crashing If the game is crashing. If true, it will only save if {@link #savedAfterCrash}
     * is false AND if {@link Config#chatlogSaveInterval} is 0.
     */
    public static void serialize(boolean crashing) {
        if(!config.chatlog)
            return;
        if(crashing && savedAfterCrash)
            return;
        if(data.messages.isEmpty() && data.history.isEmpty())
            return; // don't overwrite the file with an empty one if there's nothing to save

        if(data.messages.size() == lastMessageCount && data.history.size() == lastHistoryCount)
            return; // don't save if there's no new data AND if the path is the default one (not a backup)

        removeOverflowData(); // don't save more than the max amount of messages

        try {
            final String str = GSON.toJson(data, Data.class);
            Files.writeString(PATH, str, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            lastHistoryCount = data.history.size();
            lastMessageCount = data.messages.size();

            ChatPatches.LOGGER.info("[ChatLog.serialize] Saved the chat log containing {} messages and {} sent messages to '{}'", data.messages.size(), data.history.size(), PATH);
        } catch(IOException e) {
            ChatPatches.LOGGER.error("[ChatLog.serialize] An I/O error occurred while trying to save the chat log:", e);

        } finally {
            if(crashing)
                savedAfterCrash = true;
        }
    }

    /**
     * Creates a backup of the current chat log file
     * located at {@link #PATH} and saves it
     * as "chatlog_" + current time + ".json" in the
     * same directory as the original file.
     * If an error occurs, a warning will be logged.
     * Doesn't modify the current chat log.
     */
    public static void backup() {
		try {
            Files.copy(PATH, PATH.resolveSibling( "chatlog_" + ChatPatches.TIME_FORMATTER.get() + ".json" ));
		} catch(IOException e) {
			ChatPatches.LOGGER.warn("[ChatLog.backup] Couldn't backup the chat log at '{}':", PATH, e);
		}
	}

    /** Restores the chat log from {@link #data} into Minecraft. */
    public static void restore(MinecraftClient client) {
        Flags.LOADING_CHATLOG.raise();

        if(!data.history.isEmpty())
            data.history.forEach(client.inGameHud.getChatHud()::addToMessageHistory);

        if(!data.messages.isEmpty())
            data.messages.forEach(msg -> client.inGameHud.getChatHud().addMessage(msg, null, RESTORED_TEXT));

        Flags.LOADING_CHATLOG.lower();

        ChatPatches.LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages from '{}' into Minecraft!", data.messages.size(), data.history.size(), PATH);
    }

    /**
     * Ticks {@link #ticksUntilSave} down by 1.
     *
     * @implNote
     * <ol>
     *     <li>Saves the chat log if {@link Config#chatlogSaveInterval} is greater than 0
     *     AND if {@link #ticksUntilSave} is 0.</li>
     *     <li>Decrements {@link #ticksUntilSave} by 1.</li>
     *     <li>If {@link #ticksUntilSave} is less than 0, it will reset it to {@link Config#chatlogSaveInterval} * 20
     *     (converts seconds to ticks).</li>
     * </ol>
     */
    public static void tickSaveCounter() {
        if(config.chatlogSaveInterval > 0 && ticksUntilSave == 0)
            serialize(false);

        ticksUntilSave--;

        if(ticksUntilSave < 0)
            ticksUntilSave = config.chatlogSaveInterval * 60 * 20;
    }

    public static void addMessage(Text msg) {
        if(data.messages.size() > config.chatMaxMessages)
            data.messages.remove(0);

        data.messages.add(msg);
    }
    public static void addHistory(String msg) {
        if(data.history.size() > config.chatMaxMessages)
            data.history.remove(0);

        data.history.add(msg);
    }

    public static void removeOverflowData() {
        // the sublist indices make sure to only keep the newest data and remove the oldest
        if(data.messages.size() > config.chatMaxMessages)
            data.messages = data.messages.subList( data.messages.size() - config.chatMaxMessages, data.messages.size() );

        if(data.history.size() > config.chatMaxMessages)
            data.history = data.history.subList( data.history.size() - config.chatMaxMessages, data.history.size() );
    }

    public static void clearMessages() {
        data.messages.clear();
    }
    public static void clearHistory() {
        data.history.clear();
    }

    public static int messageCount() {
        return data.messages.size();
    }
    public static int historyCount() {
        return data.history.size();
    }
}