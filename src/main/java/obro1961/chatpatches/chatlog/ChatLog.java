package obro1961.chatpatches.chatlog;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import net.minecraft.util.Uuids;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Flags;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;
import static obro1961.chatpatches.util.TextUtils.textCodec;

/**
 * Represents the chat log file in the run directory located at {@link ChatLog#PATH}.
 * Contains methods for serializing, deserializing, accessing, modifying, and
 * backing up the data.
 */
public class ChatLog {
    public static final Path PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatlog.json");
    public static final MessageIndicator RESTORED_TEXT = new MessageIndicator(0x382fb5, null, null, I18n.translate("text.chatpatches.restored"));

    public static boolean loaded = false;
    public static int ticksUntilSave = config.chatlogSaveInterval * 60 * 20; // convert minutes to ticks

    private static ChatLog.Data data = new Data();
    private static boolean savedAfterCrash = false;
    private static int lastHistoryCount = -1, lastMessageCount = -1;


    /** Simplified serializing class */
    private static class Data {
        public static final String EMPTY_DATA = "{\"history\":[],\"messages\":[]}"; // prevents a few errors if the channel doesn't initialize
        public static final int DEFAULT_SIZE = 100;
        /**
         * Codec for serializing and deserializing chat log data.
         * Has entries for the {@link #messages} and {@link #history},
         * and calls {@link Codec#xmap(Function, Function)} to make the
         * lists mutable.
         */
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(textCodec()).xmap(ArrayList::new, Function.identity()).fieldOf("messages").forGetter(data -> data.messages),
            Codec.list(Codec.STRING).xmap(ArrayList::new, Function.identity()).fieldOf("history").forGetter(data -> data.history)
        ).apply(inst, (messages, history) -> Util.make(new Data(), data -> {
            data.messages = messages;
            data.history = history;
        })));

        public ArrayList<Text> messages;
        public ArrayList<String> history;

        private Data() {
            messages = new ArrayList<>(DEFAULT_SIZE);
            history = new ArrayList<>(DEFAULT_SIZE);
        }

        private Data(boolean done) {
            this();
            loaded = done;
        }
    }


    /**
     * Deserializes the chat log from {@link #PATH} and resolves message data from it.
     *
     * @implNote
     * <ol>
     *   <li>Checks if the file at {@link #PATH} exists.</li>
     *   <li>If it doesn't exist, sets {@link #data} to an empty object and returns.</li>
     *   <li>If it does exist, converts the ChatLog file to UTF-8 if necessary and loads it into {@code rawData}.</li>
     *   <li>If {@code rawData} contains invalid data, resets {@link #data}.</li>
     *   <li>Transforms any legacy UUID int arrays into a stringified format</li>
     *   <li>Then uses {@link Data#CODEC} to parse {@code rawData} into a usable {@link Data} object.</li>
     *   <li>Removes any overflowing messages.</li>
     *   <li>If any errors are thrown, logs the issue and backs up the broken file just in case.</li>
     *   <li>Otherwise, logs a message noting how many entries were loaded.</li>
     * </ol>
     */
    public static void deserialize() {
        String rawData = Data.EMPTY_DATA;

        if(Files.exists(PATH)) {
            try {
                rawData = Files.readString(PATH);
            } catch(MalformedInputException notUTF8) { // thrown if the file is not encoded with UTF-8
                LOGGER.warn("[ChatLog.deserialize] Chat log file encoding was '{}', not UTF-8. Complex text characters may have been replaced with question marks.", Charset.defaultCharset().name());

                try {
                    // force-writes the string as UTF-8
                    Files.writeString(PATH, new String(Files.readAllBytes(PATH)), StandardOpenOption.TRUNCATE_EXISTING);
                    rawData = Files.readString(PATH);

                } catch(IOException ioexc) {
                    LOGGER.error("[ChatLog.deserialize] Couldn't rewrite the chat log at '{}', resetting:", PATH, ioexc);

                    // final attempt to reset the file
                    try {
                        rawData = Data.EMPTY_DATA; // just in case of corruption from previous failures
                        Files.writeString(PATH, Data.EMPTY_DATA, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch(IOException ioerr) {
                        LOGGER.error("[ChatLog.deserialize] Couldn't reset the chat log at '{}':", PATH, ioerr);
                    }
                }

            } catch(IOException e) {
                LOGGER.error("[ChatLog.deserialize] Couldn't access the chat log at '{}':", PATH, e);
                rawData = Data.EMPTY_DATA; // just in case of corruption from failures
            }
        } else {
            data = new Data(true);
            return;
        }


        // ignore invalid files
        if( rawData.length() < 2 || !rawData.startsWith("{") ) {
            data = new Data(true);
            return;
        }

        try {
            // transformUUIDArrays: (temporary?) method to fix old chat logs
            JsonObject jsonData = JsonHelper.deserialize( transformUUIDArrays(rawData) );

            data = Data.CODEC.parse(JsonOps.INSTANCE, jsonData).resultOrPartial(e -> {
                throw new JsonSyntaxException(e);
            }).orElseThrow();

            // the sublist indices make sure to only keep the newest data and remove the oldest
            // NOTE: the chat log system has the oldest messages at 0, but vanilla has the newest at 0
            if(messageCount() > config.chatMaxMessages)
                data.messages = (ArrayList<Text>)data.messages.subList( messageCount() - config.chatMaxMessages, messageCount() );
            if(historyCount() > config.chatMaxMessages)
                data.history = (ArrayList<String>)data.history.subList( historyCount() - config.chatMaxMessages, historyCount() );

            loaded = true;
        } catch(Exception e) {
            LOGGER.error("[ChatLog.deserialize] Tried to read the chat log and found an error, backing it up and loading an empty one:", e);

            backup();

            data = new Data(true);
            return;
        }

        LOGGER.info("[ChatLog.deserialize] Read the chat log containing {} messages and {} sent messages from '{}'",
			messageCount(), historyCount(),
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
        if(!config.chatlog || (crashing && savedAfterCrash))
            return;
        if(data.messages.isEmpty() && data.history.isEmpty())
            return; // don't overwrite the file with an empty one if there's nothing to save
        if(messageCount() == lastMessageCount && historyCount() == lastHistoryCount)
            return; // don't save if there's no new data AND if the path is the default one (not a backup)

        try {
            String str = JsonHelper.toSortedString(
                Data.CODEC.encodeStart(JsonOps.INSTANCE, data).resultOrPartial(e -> {
                    throw new JsonSyntaxException(e);
                }).orElseThrow()
            );

            Files.writeString(PATH, str, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            lastHistoryCount = historyCount();
            lastMessageCount = messageCount();
            LOGGER.info("[ChatLog.serialize] Saved the chat log containing {} messages and {} sent messages to '{}'", messageCount(), historyCount(), PATH);
        } catch(IOException e) {
            LOGGER.error("[ChatLog.serialize] An I/O error occurred while trying to save the chat log:", e);
            LOGGER.debug("[ChatLog.serialize] Dumped data:\n{\"history\":{},\"messages\":{}}", data.history, data.messages);
        } finally {
            if(crashing)
                savedAfterCrash = true;
        }
    }

    /**
     * Creates a backup of the current chat log file
     * located at {@link #PATH} and saves it as
	 * {@code chatlog_${current_time}.json} in the
     * same directory as the original file. If an
	 * error occurs, a warning will be logged.
     * Doesn't modify the current chat log.
     */
    public static void backup() {
		try {
            Files.copy(PATH, PATH.resolveSibling( "chatlog_" + Util.getFormattedCurrentTime() + ".json" ));
		} catch(IOException e) {
			LOGGER.warn("[ChatLog.backup] Couldn't backup the chat log at '{}':", PATH, e);
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

        LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages from '{}' into Minecraft!", messageCount(), historyCount(), PATH);
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

    /**
     * DFU-type method to update old chat logs and
     * allow them to be deserialized in this post-Codec
     * world. This method may be deleted at any time.
     *
     * @implNote Currently transforms old UUID arrays into
     * stringified ones.
     */
    private static String transformUUIDArrays(String oldRawData) {
        // all the "\\s*" substrings allow matching prettified chat logs
        // without whitespace matches: `"id":[(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)]`
        String uuidArrayRegex = "\"id\"\\s*:\\s*\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]";

        String fixedData = oldRawData;
        while( fixedData.matches(".*"+uuidArrayRegex+".*") ) {
            // find the first instance and map the stringified array to a real int array
            int[] bits = Arrays.stream(fixedData.replaceFirst(".*"+uuidArrayRegex+".*", "$1,$2,$3,$4").split(","))
                .mapToInt(Integer::parseInt)
                .toArray();
            // actually replace the stringified array with the dashed uuid
            fixedData = fixedData.replaceFirst( uuidArrayRegex, "\"id\":\"" + Uuids.toUuid(bits) + "\"" );
        }

        return fixedData;
    }

    public static void addMessage(Text msg) {
        if(messageCount() > config.chatMaxMessages)
            data.messages.removeFirst();

        data.messages.add(msg);
    }
    public static void addHistory(String msg) {
        if(historyCount() > config.chatMaxMessages)
            data.history.removeFirst();

        data.history.add(msg);
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