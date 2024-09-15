package obro1961.chatpatches.chatlog;

import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.Flags;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.function.Function;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;

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
            Codec.list(TextCodecs.CODEC).xmap(ArrayList::new, Function.identity()).fieldOf("messages").forGetter(data -> data.messages),
            Codec.list(Codec.STRING).xmap(ArrayList::new, Function.identity()).fieldOf("history").forGetter(data -> data.history)
        ).apply(inst, (messages, history) -> Util.make(new Data(), data -> {
            data.messages = messages;
            data.history = history;
        })));

        public ArrayList<Text> messages;
        public ArrayList<String> history;

        private Data() {
            messages = Lists.newArrayListWithExpectedSize(DEFAULT_SIZE);
            history = Lists.newArrayListWithExpectedSize(DEFAULT_SIZE);
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

        long start = System.currentTimeMillis();
        LOGGER.info("[ChatLog.deserialize] Reading...");

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
            // intentionally creates invalid data to prompt the next if block to
            // generate a blank one and log it, instead of it happening twice
            rawData = "";
        }


        // ignore invalid files
        if( rawData.length() < 2 || !rawData.startsWith("{") ) {
            data = new Data(true);
            LOGGER.info("[ChatLog.deserialize] No chat log found at '{}', generated a blank one for {} initial messages in {} seconds",
                PATH, Data.DEFAULT_SIZE, (System.currentTimeMillis() - start) / 1000.0
            );
            return;
        }

        try {
            JsonObject jsonData = JsonHelper.deserialize(rawData);
            data = Data.CODEC.parse(ChatPatches.jsonOps(), jsonData).resultOrPartial(e -> {
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

        LOGGER.info("[ChatLog.deserialize] Read the chat log containing {} messages and {} sent messages from '{}' in {} seconds",
            messageCount(), historyCount(), PATH, (System.currentTimeMillis() - start) / 1000.0
        );
    }

    /**
     * Saves the chat log to {@link #PATH}. Only saves if {@link Config#chatlog} is true
     * and if there is *new* data to save. <i>As of 1.20.5, also requires the player to
     * be in-game during the saving process, so the registry-synced TextCodec can be
     * used (#180).</i>
     */
    public static void serialize() {
        if(!config.chatlog)
            return;
        if(data.messages.isEmpty() && data.history.isEmpty())
            return; // don't overwrite the file with an empty one if there's nothing to save
        if(messageCount() == lastMessageCount && historyCount() == lastHistoryCount)
            return; // don't save if there's no new data

        long start = System.currentTimeMillis();
        LOGGER.info("[ChatLog.serialize] Saving...");


        // catch the NPE and cancel IF it's thrown
        RegistryOps<JsonElement> registeredOps;
        try {
            registeredOps = ChatPatches.jsonOps();
        } catch(NullPointerException npe) {
            ChatPatches.logReportMsg(npe);
            dumpData();
            return;
        }

        try {
            JsonElement json = Data.CODEC.encodeStart(registeredOps, data)
                .resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
                .orElseThrow();

            Files.writeString(PATH, JsonHelper.toSortedString(json), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            lastHistoryCount = historyCount();
            lastMessageCount = messageCount();
            LOGGER.info("[ChatLog.serialize] Saved the chat log containing {} messages and {} sent messages to '{}' in {} seconds",
                messageCount(), historyCount(), PATH, (System.currentTimeMillis() - start) / 1000.0
            );
        } catch(ConcurrentModificationException cme) { // rudimentary attempt to fix thrown CMEs (#181)
            if(!Flags.ALLOW_CME.isRaised()) {
                LOGGER.warn("[ChatLog.serialize] A ConcurrentModificationException was unexpectedly thrown, trying to serialize one more time:", cme);
                Flags.ALLOW_CME.raise();
                serialize();
                Flags.ALLOW_CME.lower();
            } else {
                LOGGER.error("[ChatLog.serialize] A ConcurrentModificationException was thrown again, chat log saving FAILED:", cme);
            }
        } catch(IOException | RuntimeException e) {
            LOGGER.error("[ChatLog.serialize] An I/O or unexpected runtime error occurred while trying to save the chat log:", e);
            dumpData();
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
            Files.copy(PATH, PATH.resolveSibling( "chatlog_" + ChatPatches.TIME_FORMATTER.get() + ".json" ));
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
            serialize();

        ticksUntilSave--;

        if(ticksUntilSave < 0)
            ticksUntilSave = config.chatlogSaveInterval * 60 * 20;
    }

    /**
     * Dumps chat log data into the debug log. Note:
     * Assumes the Text codec is unusable, so instead
     * maps each message using {@link Text#getString()}.
     */
    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    public static void dumpData() {
        LOGGER.debug("[ChatLog.dumpData] " + Data.EMPTY_DATA.replaceAll("\\[]", "{}"), data.history, data.messages.stream().map(Text::getString).toList());
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