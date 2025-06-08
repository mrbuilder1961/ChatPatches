package obro1961.chatpatches.chatlog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.toast.SystemToast;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static obro1961.chatpatches.ChatPatches.LOGGER;
import static obro1961.chatpatches.ChatPatches.config;

/**
 * Represents the chat log file in the run directory located at {@link #PATH}.
 * Contains methods for serializing, deserializing, accessing, modifying, and
 * backing up the messages and history stored within.
 */
public class ChatLog {
    /**
     * Serializes as a {@link Pair} to avoid needing a dedicated
     * class. {@link #messages} are first and {@link #history}
     * is second, and the native list is mapped to a {@linkplain
     * ChatLog#newSyncedObjectList(List) synchronized mutable
     * object list}.
     */
    public static final Codec<Pair<ObjectList<Text>, ObjectList<String>>> CODEC = Codec.pair(
        TextUtils.textCodec()
            .listOf()
            .xmap(ChatLog::newSyncedObjectList, Function.identity()) // makes the lists synchronized and mutable
            .fieldOf("messages") // with a default value, errors are silently ignored
            .codec(),
        Codec.STRING
            .listOf()
            .xmap(ChatLog::newSyncedObjectList, Function.identity()) // makes the lists synchronized and mutable
            .fieldOf("history") // with a default value, errors are silently ignored
            .codec()
    );
    public static final Path PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatlog.json");
    public static final MessageIndicator RESTORED_INDICATOR = new MessageIndicator(0x382FB5, null, Text.translatable("text.chatpatches.restored"), "Restored"); // prepub use an AW and put the icon to use

    private static final int DEFAULT_SIZE = 100;
    /** In seconds */
    private static final int IO_TIMEOUT = 15;
    private static final String EMPTY_JSON = "{\"messages\":[],\"history\":[]}";
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean init = true;
    /**
     * Used to suspend the addition of messages
     * and access to the chat log while restoring.
     * Prevents log spam of restored messages and
     * other related issues like {@link
     * ConcurrentModificationException}s.
     */
    private static boolean restoring = false;
    private static int lastHistoryCount = -1, lastMessageCount = -1;
    private static int ticksUntilSave = config.chatlogSaveInterval * SharedConstants.TICKS_PER_MINUTE;

    private static ObjectList<Text> messages = newSyncedObjectList(null);
    private static ObjectList<String> history = newSyncedObjectList(null);

    /**
     * @return If {@code source} is null, returns a new synchronized object
     * array list; otherwise returns a synchronized view of the given list.
     */
    static <T> ObjectList<T> newSyncedObjectList(@Nullable List<T> source) {
        return ObjectLists.synchronize( source == null ? new ObjectArrayList<>(DEFAULT_SIZE) : new ObjectArrayList<>(source) );
    }


    public static boolean isRestoring() { return restoring; }

    public static void addMessage(Text message) {
        if(restoring)
            return;

        ensureCapacity();
        messages.add(message);
    }
    public static void addHistory(String sentMessage) {
        if(restoring)
            return;

        ensureCapacity();
        history.add(sentMessage);
    }

    public static void clearMessages() { messages.clear(); }
    public static void clearHistory() { history.clear(); }

    public static int messageCount() { return messages.size(); }
    public static int historyCount() { return history.size(); }

    /**
     * Ensures both {@link #messages} and {@link #history} are
     * not larger than {@link Config#chatMaxMessages}. If they
     * are, removes the oldest messages (from {@code 0} inclusive
     * to <code>list.size() - {@link Config#chatMaxMessages}</code>
     * exclusive).
     *
     * @implNote The chat log system has the oldest messages at 0,
     * but vanilla has the newest at 0. I would switch them to be in
     * the same order, but it would break existing chat logs.
     */
    private static void ensureCapacity() {
        if(messageCount() > config.chatMaxMessages)
            messages.removeElements(0, messageCount() - config.chatMaxMessages);

        if(historyCount() > config.chatMaxMessages)
            history.removeElements(0, historyCount() - config.chatMaxMessages);
    }

    private static void updateMessageCounts() {
        lastMessageCount = messageCount();
        lastHistoryCount = historyCount();
    }


    //prepub: WOAH FORGOT ABOUT THIS! i think i can keep this but needs to go on pause while i do more important stuff.
    // it should probably be integrated somehow with ChatPatches#logReportMsg
    private static void pushErrorToast(String header, String desc, String... vars) {
        final int MAX_LEN = 60; // minimizes errors going off-screen
        String d = desc.replace("{}", "%s").formatted((Object[]) vars);

        mc.getToastManager().add(new SystemToast(
            SystemToast.Type.PACK_LOAD_FAILURE,
            Text.of(header.length() > MAX_LEN ? header.substring(0, MAX_LEN - 3) + "..." : header),
            Text.of(d.length() > MAX_LEN ? d.substring(0, MAX_LEN - 3) + "..." : d)
        ));
    }

    /**
     * Deserializes the chat log from {@link #PATH}.
     *
     * @apiNote Should be executed on an {@linkplain
     * Util#getIoWorkerExecutor() I/O worker thread} to avoid freezing
     * the render thread. Must be done by the caller to ensure {@link
     * #restore()} can run sequentially, if necessary.
     *
     * @implNote
     * <ol>
     *   <li>Initializes {@code rawJson} to {@link #EMPTY_JSON}</li>
     *   <li>Checks if {@link #PATH} exists</li>
     *   <li>If it does, parses the file at {@link #PATH} and loads it
     *   into {@code rawJson}</li>
     *   <li>If that throws an error, tries to parse one more time
     *   using the {@linkplain Charset#defaultCharset() default charset},
     *   otherwise resets {@code rawJson}</li>
     *   <li>If {@code rawJson} equals {@link #EMPTY_JSON}, loads empty
     *   copies of {@link #messages} and {@link #history} to avoid
     *   unnecessary parsing</li>
     *   <li>Otherwise, uses {@link #CODEC} to parse {@code rawJson},
     *   {@linkplain ChatPatches#logReportMsg(Throwable) logging an error}
     *   and using empty lists if something went wrong</li>
     *   <li>Removes any overflowing messages</li>
     *   <li>If any errors were thrown, logs the issue and backs up the
     *   broken file, loading empty lists</li>
     *   <li>Logs a message noting how many entries were loaded and how
     *   long it took</li>
     * </ol>
     */
    public static void deserialize() {
        String rawJson = EMPTY_JSON;

        long start = System.currentTimeMillis();
        LOGGER.info("[ChatLog.deserialize] Loading...");

        if(Files.exists(PATH)) {
			try {
                rawJson = Files.readString(PATH); // chat log is always encoded with UTF-8
			} catch(MalformedInputException notUTF8) {
                Charset def = Charset.defaultCharset();
                LOGGER.warn("[ChatLog.deserialize] File encoding was '{}', not UTF-8. Complex text characters may have been corrupted!", def.name());

                try {
                    rawJson = Files.readString(PATH, def); // maybe it's using the default encoding
				} catch(IOException e) {
                    LOGGER.error("[ChatLog.deserialize] Couldn't parse '{}' in UTF-8 or '{}', generating a new one:", PATH, def.name(), e);
                    rawJson = EMPTY_JSON;
                    pushErrorToast("Chat log encoding error", "Expected UTF-8 or '{}'", def.name());
                    backup();
                }
            } catch(IOException e) {
                LOGGER.error("[ChatLog.deserialize] Something went wrong accessing '{}':", PATH, e);
                rawJson = EMPTY_JSON;
                pushErrorToast("Chat log I/O error", e.getLocalizedMessage());
                backup();
            }
        }


        try {
            if(rawJson.equals(EMPTY_JSON)) {
                messages = newSyncedObjectList(null);
                history = newSyncedObjectList(null);
            } else {
                JsonObject json = JsonHelper.deserialize(rawJson);
                var deserializedPair =
                    CODEC.parse(ChatPatches.jsonOps(), json)
                        .resultOrPartial(e -> {
                            ChatPatches.logReportMsg(new JsonParseException(e));
                            pushErrorToast("Chat log parse error", e);
                            backup();
                        })
                        .orElseGet(() -> Pair.of(newSyncedObjectList(null), newSyncedObjectList(null)));

                messages = deserializedPair.getFirst();
                history = deserializedPair.getSecond();
            }

            ensureCapacity();
            updateMessageCounts();
        } catch(RuntimeException e) {
            LOGGER.error("[ChatLog.deserialize] An unexpected error occurred while trying to parse '{}', backing it up and generating a new one:", PATH, e);
            pushErrorToast("Chat log deserialization error", e.getLocalizedMessage());
            backup();

            messages = newSyncedObjectList(null);
            history = newSyncedObjectList(null);
        } finally {
            init = false; // messages and history are populated regardless of errors
        }

        LOGGER.info("[ChatLog.deserialize] Parsed {} messages and {} sent messages in {} seconds",
            lastMessageCount, lastHistoryCount, (System.currentTimeMillis() - start) / 1000.0
        );
    }

    /**
     * Saves the chat log to {@link #PATH}. Only saves if {@link Config#chatlog} is
     * true, if {@link #messages} and {@link #history} are not empty, and if there
     * are <i>new</i> messages to save. <b>Executed on an {@linkplain
     * Util#getIoWorkerExecutor() I/O worker thread} to avoid freezing the render
     * thread.</b>
     *
     * @apiNote As of 1.20.5, also requires the player to be in-game during the saving
     * process, so the registry-synced TextCodec can be used.
     *
     * @see <a href="https://github.com/mrbuilder1961/ChatPatches/issues/180">#180</a>
     */
    public static void serialize() {
        if(!config.chatlog)
            return;
        if((messages.size() == lastMessageCount && history.size() == lastHistoryCount) || (messages.isEmpty() && history.isEmpty()))
            return; // don't write empty or old data

		try {
			Util.getIoWorkerExecutor().submit(() -> {
				long start = System.currentTimeMillis();
				LOGGER.info("[ChatLog.serialize] Saving...");

				try {
					JsonElement json = CODEC.encodeStart(ChatPatches.jsonOps(), Pair.of(messages, history))
						.resultOrPartial(e -> ChatPatches.logReportMsg(new JsonParseException(e)))
						.orElseThrow();

					// always in UTF-8
					Files.writeString(PATH, JsonHelper.toSortedString(json), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

					updateMessageCounts();
				} catch(IOException | RuntimeException e) {
					LOGGER.error("[ChatLog.serialize] An unexpected error occurred while trying to save:", e);
					pushErrorToast("Chat log serialization error", e.getLocalizedMessage());
					LOGGER.warn("[ChatLog.serialize] Dumping data: {}",
						EMPTY_JSON // assumes the Text codec is unusable, so instead uses #getString()
							.replace("[]", messages.stream().map(Text::getString).toList().toString())
							.replace("[]", history.toString())
					);
				}

				LOGGER.info("[ChatLog.serialize] Saved {} messages and {} sent messages to '{}' in {} seconds",
					lastMessageCount, lastHistoryCount, PATH, (System.currentTimeMillis() - start) / 1000.0
				);
			}).get(IO_TIMEOUT, TimeUnit.SECONDS);
		} catch(InterruptedException | ExecutionException | TimeoutException e) {
			ChatPatches.logReportMsg(e);
		}
	}


    /**
     * Creates a backup of the current chat log file located at {@link #PATH} and saves
     * it as {@code chatlog_${now}.json} in the same directory as the original file.
     * If an error occurs, a warning will be logged. Doesn't modify the current chat
     * log. <b>Executed on an {@linkplain Util#getIoWorkerExecutor() I/O worker thread}
     * to avoid freezing the render thread.</b>
     */
    public static void backup() {
		try {
			Util.getIoWorkerExecutor().submit(() -> {
				try {
					Path backupPath = PATH.resolveSibling("chatlog_" + Util.getFormattedCurrentTime() + ".json");
					Files.copy(PATH, backupPath);
					LOGGER.info("[ChatLog.backup] Successfully backed up the current chat log to '{}':", backupPath);
				} catch(IOException e) {
					LOGGER.warn("[ChatLog.backup] Couldn't backup '{}':", PATH, e);
					pushErrorToast("Chat log backup error", e.getLocalizedMessage());
				}
			}).get(IO_TIMEOUT, TimeUnit.SECONDS);
		} catch(InterruptedException | TimeoutException | ExecutionException e) {
			ChatPatches.logReportMsg(e);
		}
	}

    public static void restore() {
        if(messageCount() > 0 && historyCount() > 0) {
            restoring = true;
            history.forEach(mc.inGameHud.getChatHud()::addToMessageHistory);
            messages.forEach(msg -> mc.inGameHud.getChatHud().addMessage(msg, null, RESTORED_INDICATOR));
            restoring = false;
        }

        LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages!", messageCount(), historyCount());
    }

    /**
     * Attempts to load the chat log from {@link #PATH}
     * and restore it into the game. Only does so if
     * the chat log is enabled in the config and hasn't
     * been deserialized yet. <b>Executed on an {@linkplain
     * Util#getIoWorkerExecutor() I/O worker thread} to
     * avoid freezing the render thread.</b>
     */
    public static void load() {
        if(config.chatlog && init) {
			try {
                // submits the deserialization task and waits at most #IO_TIMEOUT seconds for it to complete
				Util.getIoWorkerExecutor().submit(ChatLog::deserialize).get(IO_TIMEOUT, TimeUnit.SECONDS); //sigh... prepub config option?
                ChatLog.restore(); // restoring requires deserialization, but is also not an I/O task
			} catch(TimeoutException | InterruptedException | ExecutionException e) {
                ChatPatches.logReportMsg(e);
                // i'm not re-implementing more error shit here, in this case >15s load time *is* the catastrophic issue
            }
        }
    }

    public static void forceLoad() {
        if(config.chatlog) {
            LOGGER.info("[ChatLog.forceLoad] Force loading the chat log...");

            init = true; // allows #load to execute
            load();
            init = false;
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
}