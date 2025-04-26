package obro1961.chatpatches.chatlog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.Util;
import obro1961.chatpatches.ChatPatches;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.TextUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Function;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;

/**
 * Represents the chat log file in the run directory located
 * at {@link #PATH}. Contains methods for serializing,
 * deserializing, accessing, modifying, and backing up the data.
 */
public class ChatLog {
    public static final Path PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatlog.json");
    public static final MessageIndicator RESTORED_INDICATOR = new MessageIndicator(0x382FB5, null, Text.translatable("text.chatpatches.restored"), "Restored"); // prepub use an AW and put the icon to use

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean deserialized = false;
    /**
     * Used to suspend the addition of messages
     * and access to the chat log while restoring.
     * Prevents log spam of restored messages and
     * other related issues like {@link
     * ConcurrentModificationException}s.
     */
    private static boolean restoring = false;
    private static ChatLog.Data data = new Data();
    private static int lastHistoryCount = -1, lastMessageCount = -1;
    private static int ticksUntilSave = config.chatlogSaveInterval * SharedConstants.TICKS_PER_MINUTE;


    private static class Data { //delete: this; just move the codec to the main class and it will work fine if the main class has the list fields
        static final Codec<Data> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            TextUtils.textCodec()
                .listOf()
                .xmap(Data::newSyncedObjectList, Function.identity()) // makes the lists synchronized and mutable
                .optionalFieldOf("messages", newSyncedObjectList(null))
                .forGetter(data -> data.messages),
            Codec.STRING
                .listOf()
                .xmap(Data::newSyncedObjectList, Function.identity()) // makes the lists synchronized and mutable
                .optionalFieldOf("history", newSyncedObjectList(null))
                .forGetter(data -> data.history)
        ).apply(inst, (messages, history) -> Util.make(new Data(), data -> {
            data.messages = messages;
            data.history = history;
        })));
        static final int DEFAULT_SIZE = 100;
        static final String EMPTY_DATA = "{\"messages\":[],\"history\":[]}";

        ObjectList<Text> messages;
        ObjectList<String> history;

        Data() {
            messages = newSyncedObjectList(null);
            history = newSyncedObjectList(null);
        }

        /**
         * @return If {@code source} is null, returns a new synchronized object
         * array list, otherwise returns a synchronized view of the given list.
         */
        static <T> ObjectList<T> newSyncedObjectList(@Nullable List<T> source) {
            return ObjectLists.synchronize( source == null ? new ObjectArrayList<>(DEFAULT_SIZE) : new ObjectArrayList<>(source) );
        }

        /**
         * @implNote Assumes the Text codec is unusable, so instead
         *           maps each message using {@link Text#getString()}
         */
        @Override
        public String toString() {
            return EMPTY_DATA
                .replace("[]", history.toString())
                .replace("[]", messages.stream().map(Text::getString).toList().toString());
        }
    }


    /**
     * Deserializes the chat log from {@link #PATH} and resolves an instance
     * of {@link Data} from it.
     *
     * @implNote
     * <ol>
     *   <li>Initializes {@code rawData} to {@link Data#EMPTY_DATA}</li>
     *   <li>Checks if {@link #PATH} exists</li>
     *   <li>If it does, parses the file at {@link #PATH} and loads it
     *   into {@code rawData}</li>
     *   <li>If that throws an error, tries to parse one more time
     *   using the {@linkplain Charset#defaultCharset() default charset},
     *   otherwise resets {@code rawData}</li>
     *   <li>If {@code rawData} equals {@link Data#EMPTY_DATA}, saves a
     *   fresh instance of {@link Data} to avoid unnecessary parsing</li>
     *   <li>Otherwise, uses {@link Data#CODEC} to parse {@code rawData},
     *   {@linkplain ChatPatches#logReportMsg(Throwable) logging an error}
     *   and creating a new copy if something went wrong</li>
     *   <li>Removes any overflowing messages</li>
     *   <li>If any errors were thrown, logs the issue and backs up the
     *   broken file, saving a new instance of {@link Data}</li>
     *   <li>Logs a message noting how many entries were loaded and the time
     *   it took to load</li>
     * </ol>
     */
    public static void deserialize() {
        long start = System.currentTimeMillis();
        String rawData = Data.EMPTY_DATA;

        LOGGER.info("[ChatLog.deserialize] Loading...");
        if(Files.exists(PATH)) {
			try {
                rawData = Files.readString(PATH); // chat log is always encoded with UTF-8
			} catch(MalformedInputException notUTF8) {
                Charset def = Charset.defaultCharset();
                LOGGER.warn("[ChatLog.deserialize] File encoding was '{}', not UTF-8. Complex text characters may have been corrupted!", def.name());

                try {
                    rawData = Files.readString(PATH, def); // maybe it's using the default encoding
				} catch(IOException e) {
                    LOGGER.error("[ChatLog.deserialize] Couldn't parse '{}' in UTF-8 or '{}', generating a new one:", PATH, def.name(), e);
                    rawData = Data.EMPTY_DATA;
                }
            } catch(IOException e) {
                LOGGER.error("[ChatLog.deserialize] Something went wrong accessing '{}':", PATH, e);
                rawData = Data.EMPTY_DATA;
            }
        }


        try {
            if(rawData.equals(Data.EMPTY_DATA)) {
                data = new Data(); // spare some time
            } else {
                JsonObject json = JsonHelper.deserialize(rawData);
                data =
                    Data.CODEC.parse(ChatPatches.jsonOps(), json)
                        .resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
                        .orElseGet(Data::new);
            }

            // the sublist indices make sure to only keep the newest data and remove the oldest
            // NOTE: the chat log system has the oldest messages at 0, but vanilla has the newest at 0
            if(messageCount() > config.chatMaxMessages)
                data.messages = data.messages.subList( messageCount() - config.chatMaxMessages, messageCount() );
            if(historyCount() > config.chatMaxMessages)
                data.history = data.history.subList( historyCount() - config.chatMaxMessages, historyCount() );
        } catch(RuntimeException e) {
            LOGGER.error("[ChatLog.deserialize] An unexpected error occurred while trying to parse '{}', backing it up and generating a new one:", PATH, e);
            backup();

            data = new Data();
        } finally {
            deserialized = true; // if successful, data is populated. if an error occurred, data is empty
        }

        LOGGER.info("[ChatLog.deserialize] Parsed {} messages and {} sent messages in {} seconds",
            messageCount(), historyCount(), (System.currentTimeMillis() - start) / 1000.0
        );
    }

    /**
     * Saves the chat log to {@link #PATH}. Only saves if {@link Config#chatlog} is
     * true, if {@link #data} is not empty, and if there is <i>new</i> data to save.
     *
     * @apiNote As of 1.20.5, also requires the player to be in-game during the saving
     *          process, so the registry-synced TextCodec can be used
     *
     * @see <a href="https://github.com/mrbuilder1961/ChatPatches/issues/180">#180</a>
     */
    public static void serialize() {
        if(!config.chatlog) //todo simplify these
            return;
        if((messageCount() == lastMessageCount && historyCount() == lastHistoryCount) || (data.messages.isEmpty() && data.history.isEmpty()))
            return; // don't write empty or old data

        long start = System.currentTimeMillis();

        LOGGER.info("[ChatLog.serialize] Saving...");
        try {
            JsonElement json = Data.CODEC.encodeStart(ChatPatches.jsonOps(), data)
                .resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
                .orElseThrow();

            // always in UTF-8
            Files.writeString(PATH, JsonHelper.toSortedString(json), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            lastMessageCount = messageCount();
            lastHistoryCount = historyCount();
        } catch(IOException | RuntimeException e) {
            LOGGER.error("[ChatLog.serialize] An unexpected error occurred while trying to save:", e);
            LOGGER.warn("[ChatLog.serialize] Dumping data: {}", data.toString());
        }

        LOGGER.info("[ChatLog.serialize] Saved {} messages and {} sent messages to '{}' in {} seconds",
            messageCount(), historyCount(), PATH, (System.currentTimeMillis() - start) / 1000.0
        );
    }


    /**
     * Creates a backup of the current chat log file
     * located at {@link #PATH} and saves it as
     * {@code chatlog_${now}.json} in the
     * same directory as the original file. If an
     * error occurs, a warning will be logged.
     * Doesn't modify the current chat log.
     */
    public static void backup() {
        try {
            Files.copy(PATH, PATH.resolveSibling( "chatlog_" + Util.getFormattedCurrentTime() + ".json" ));
        } catch(IOException e) {
            LOGGER.warn("[ChatLog.backup] Couldn't backup '{}':", PATH, e);
        }
    }

    /**
     * Restores the chat log from {@link #data} into
     * the {@linkplain ChatHud chat hud}.
     */
    public static void restore() {
        restoring = true;
        {
            data.history.forEach(mc.inGameHud.getChatHud()::addToMessageHistory);
            data.messages.forEach(msg -> mc.inGameHud.getChatHud().addMessage(msg, null, RESTORED_INDICATOR));
        }
        restoring = false;

        LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages from '{}'!", messageCount(), historyCount(), PATH);
    }

    /**
     * Attempts to load the chat log from {@link #PATH}
     * and restore it into the game. Only does so if
     * the chat log is enabled in the config and hasn't
     * been deserialized yet.
     */
    public static void load() {
        if(config.chatlog && !deserialized) {
            deserialize();
            restore();
        }
    }

    /**
     * Ticks {@link #ticksUntilSave} down by 1.
     *
     * @implNote
     * <ol>
     *     <li>Saves the chat log if {@link Config#chatlogSaveInterval}
     *     is greater than 0 AND if {@link #ticksUntilSave} is 0.</li>
     *     <li>Decrements {@link #ticksUntilSave} by 1.</li>
     *     <li>If {@link #ticksUntilSave} is less than 0, resets to
     *     {@link Config#chatlogSaveInterval} *
     *     {@value SharedConstants#TICKS_PER_MINUTE}</li>
     * </ol>
     */
    public static void tickSaveCounter() {
        if(config.chatlogSaveInterval > 0 && ticksUntilSave == 0)
            serialize();

        ticksUntilSave--;

        if(ticksUntilSave < 0)
            ticksUntilSave = config.chatlogSaveInterval * SharedConstants.TICKS_PER_MINUTE;
    }

    /**
     * Saves the chat log if the save interval is
     * disabled and the game is paused.
     */
    public static void saveIfPaused(Screen screen) {
        if(config.chatlogSaveInterval == 0 && (!mc.isWindowFocused() || screen instanceof GameMenuScreen))
            serialize();
    }



    public static boolean isRestoring() {
        return restoring;
    }

    public static void addMessage(Text msg) {
        if(!restoring) {
            if(messageCount() > config.chatMaxMessages)
                data.messages.removeFirst();

            data.messages.add(msg);
        }
    }
    public static void addHistory(String msg) {
        if(!restoring) {
            if(historyCount() > config.chatMaxMessages)
                data.history.removeFirst();

            data.history.add(msg);
        }
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