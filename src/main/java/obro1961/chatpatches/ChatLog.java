package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import joptsimple.internal.Strings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import obro1961.chatpatches.accessor.ChatHudAccess;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.security.ClickEvent$ActionMixin;
import obro1961.chatpatches.util.TextUtils;
import org.apache.commons.lang3.StringEscapeUtils;
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

import static obro1961.chatpatches.ChatPatches.*;

/**
 * Represents the chat log file in the run directory located at {@link #PATH}.
 * Contains methods for serializing, deserializing, accessing, modifying, and
 * backing up the messages and history stored within.
 */
public class ChatLog {
    /**
     * Serializes as a {@link Pair} to avoid needing a dedicated class. {@link
	 * #messages} are first and {@link #history} is second, and the native list
	 * is mapped to a {@linkplain ChatLog#newSyncedObjectList(List) synchronized
	 * mutable object list}. The horrible abomination that is its reimplementation
	 * allows the codec to automatically disable the {@linkplain #safeCodec safety
	 * serialization check} while in use to allow all messages to be serialized.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
     */
    public static final Codec<Pair<ObjectList<Component>, ObjectList<String>>> CODEC = Util.make(() -> {
		var CODEC = Codec.pair(
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

		return new Codec<>() {
			@Override
			public <T> DataResult<T> encode(Pair<ObjectList<Component>, ObjectList<String>> input, DynamicOps<T> ops, T prefix) {
				safeCodec.set(false);
				var result = CODEC.encode(input, ops, prefix);
				safeCodec.set(true);
				return result;
			}

			@Override
			public <T> DataResult<Pair<Pair<ObjectList<Component>, ObjectList<String>>, T>> decode(DynamicOps<T> ops, T input) {
				safeCodec.set(false);
				var result = CODEC.decode(ops, input);
				safeCodec.set(true);
				return result;
			}

			@Override
			public String toString() {
				return "WrappedChatLogCodec[safe=" + safeCodec.get() + ", codec=" + CODEC + "]";
			}
		};
	});
    public static final Path PATH = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("chatlog.json");
    public static final GuiMessageTag RESTORED_INDICATOR = new GuiMessageTag(0x382FB5, null, Component.translatable("text.chatpatches.restored"), "Restored"); // prepub use an AW and put the icon to use

	/**
	 * Thread-local because
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1387226885867704401">
	 * TheWhyEvenHow</a> suggested this, and they also made this implementation
	 * successful, so I trust them.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
	 */
	private static final ThreadLocal<Boolean> safeCodec = ThreadLocal.withInitial(() -> true);
    private static final int DEFAULT_SIZE = 100;
	private static final int IO_THRESHOLD_SUGGESTION = 1000;
	private static final String EMPTY_JSON = "{\"messages\":[],\"history\":[]}";
	private static final ObjectList<?> EMPTY_LIST = newSyncedObjectList(null); // used for determining if the chat log has been deserialized yet

	private static Minecraft mc() { return Minecraft.getInstance(); }

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

    @SuppressWarnings("unchecked")
	private static ObjectList<Component> messages = (ObjectList<Component>) EMPTY_LIST;
    @SuppressWarnings("unchecked")
    private static ObjectList<String> history = (ObjectList<String>) EMPTY_LIST;

    /**
     * @return If {@code source} is null, returns a new synchronized object
     * array list; otherwise returns a synchronized view of the given list.
     */
    static <T> ObjectList<T> newSyncedObjectList(@Nullable List<T> source) {
        return ObjectLists.synchronize( source == null ? new ObjectArrayList<>(DEFAULT_SIZE) : new ObjectArrayList<>(source) );
    }


    public static boolean isRestoring() { return restoring; }
    public static ThreadLocal<Boolean> isCodecSafe() { return safeCodec; }

    public static void addMessage(Component message) {
        if(restoring)
            return;

        enforceLimits();
        messages.add(message);
    }
    public static void addHistory(String sentMessage) {
        if(restoring)
            return;

        enforceLimits();
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
     * the same order, but it would break existing chat logs and is
	 * therefore not worth the hassle.
     */
    private static void enforceLimits() {
        if(messageCount() > config.chatMaxMessages)
            messages.removeElements(0, messageCount() - config.chatMaxMessages);

        if(historyCount() > config.chatMaxMessages)
            history.removeElements(0, historyCount() - config.chatMaxMessages);
    }

    private static void updateMessagesLogged() {
        lastMessageCount = messageCount();
        lastHistoryCount = historyCount();
    }

	/**
     * Deserializes the chat log from {@link #PATH}.
     *
     * @apiNote Should be executed on an {@linkplain
     * Util#ioPool() I/O worker thread} to avoid freezing
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
        LOGGER.info("[ChatLog.deserialize] Reading...");

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
                    pushErrorToast("Chat log encoding error", "Expected UTF-8 or '%s'".formatted(def.name()));
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
                JsonObject json = GsonHelper.parse(rawJson);
                var deserializedPair =
                    CODEC.parse(ChatPatches.regJsonOps(), json)
                        .resultOrPartial(e -> {
                            logReportMsg(new JsonParseException(e));
                            pushErrorToast("Chat log parse error", e);
                            backup();
                        })
                        .orElseGet(() -> Pair.of(newSyncedObjectList(null), newSyncedObjectList(null)));

                messages = deserializedPair.getFirst(/*stonecutter: NOT java 21 getFirst*/);
                history = deserializedPair.getSecond();
            }

            enforceLimits();
            updateMessagesLogged();

			LOGGER.info("[ChatLog.deserialize] Parsed {} messages and {} sent messages!", lastMessageCount, lastHistoryCount);
        } catch(RuntimeException e) {
            LOGGER.error("[ChatLog.deserialize] An unexpected error occurred while trying to parse '{}', backing it up and generating a new one:", PATH, e);
            pushErrorToast("Chat log deserialization error", e.getLocalizedMessage());
            backup();

            messages = newSyncedObjectList(null);
            history = newSyncedObjectList(null);
        }
		ChatPatches.logDuration(start, IO_THRESHOLD_SUGGESTION);
    }

	@SuppressWarnings("deprecation") // if an alternative is found, i'll gladly use it
	private static String escapeAndSurround(String str) {
		return Strings.surround(StringEscapeUtils.escapeJava(str), '"', '"');
	}

    /**
     * Saves the chat log to {@link #PATH}. Only saves if {@link Config#chatlog} is
     * true, if {@link #messages} and {@link #history} are not empty, and if there
     * are <i>new</i> messages to save. <b>Executed on an {@linkplain
     * Util#ioPool() I/O worker thread} to avoid freezing the render
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

		ChatPatches.executeIoTask(() -> {
			long start = System.currentTimeMillis();
			LOGGER.info("[ChatLog.serialize] Saving...");

			try {
				DataResult<JsonElement> result = CODEC.encodeStart(ChatPatches.regJsonOps(), Pair.of(messages, history));
				JsonElement json = result.result().orElse(null);
				String data = GsonHelper.toStableString(json);
				Path path = PATH;

				// FIXME: dump -> chatlog.json doesn't restore the messages bc they never get added for some reason? it doesn't seem to be a codec issue, but a chathud one
				if(json == null) {
					// noinspection Convert2MethodRef: makes stonecutter life easier
					var err = result.error().map(e -> e.message()).orElse(ChatFormatting.RED + "Unknown cause");
					path = PATH.resolveSibling("chatlog_dump_" + Util.getFilenameFormattedDateTime() + ".json");

					LOGGER.warn("[ChatLog.serialize] Failed to serialize chat log; dumping to '{}' instead!", path);
					logReportMsg(new JsonParseException(err));
					pushErrorToast("Chat log codec error", err);

					data = EMPTY_JSON
						.replace/*All*/("[],", messages.stream()
							// codec is unusable here
							.map(Component::getString)
							.map(ChatLog::escapeAndSurround)
							.toList() + ","
						)
						.replace/*All*/("[]}", history.stream()
							.map(ChatLog::escapeAndSurround)
							.toList() + "}"
						);
				}

				// always in UTF-8
				Files.writeString(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

				updateMessagesLogged();

				LOGGER.info("[ChatLog.serialize] Saved {} messages and {} sent messages to '{}'!", lastMessageCount, lastHistoryCount, path);
			} catch(IOException | RuntimeException e) {
				LOGGER.error("[ChatLog.serialize] An unexpected error occurred while trying to save:", e);
				pushErrorToast("Chat log serialization error", e.getLocalizedMessage());
			}
			ChatPatches.logDuration(start, IO_THRESHOLD_SUGGESTION);
		});
	}


    /**
     * Creates a backup of the current chat log file located at {@link #PATH} and saves
     * it as {@code chatlog_${now}.json} in the same directory as the original file.
     * If an error occurs, a warning will be logged. Doesn't modify the current chat
     * log. <b>Executed on an {@linkplain Util#ioPool() I/O worker thread}
     * to avoid freezing the render thread.</b>
     */
    public static void backup() {
		ChatPatches.executeIoTask(() -> {
			try {
				Path backupPath = PATH.resolveSibling("chatlog_" + Util.getFilenameFormattedDateTime() + ".json");
				Files.copy(PATH, backupPath);
				LOGGER.info("[ChatLog.backup] Successfully backed up the current chat log to '{}':", backupPath);
			} catch(IOException e) {
				LOGGER.warn("[ChatLog.backup] Couldn't backup '{}':", PATH, e);
				pushErrorToast("Chat log backup error", e.getLocalizedMessage());
			}
		});
	}

    public static void restore() {
        if(messageCount() > 0 && historyCount() > 0) {
			ChatComponent chat = mc().gui.getChat();

			restoring = true;
			history.forEach(chat::addRecentChat);
			messages.forEach(msg -> chat.addMessage(msg, null, RESTORED_INDICATOR));
			restoring = false;

			config.sendBoundaryLine(); // ensures the check that the chat isn't empty passes, which often doesn't due to multithreading
			hideRecentMessages();
		}

		LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages!", messageCount(), historyCount());
	}

	/**
	 * Hides the most recent messages in chat, so they don't render instantly when
	 * restored or when a boundary line is added. This is done by setting {@link
	 * GuiMessage.Line#addedTime} to a calculated value. Avoids iterating through
	 * the entire list by exiting from the loop after hitting the first message
	 * that is already old enough to not render.
	 */
	public static void hideRecentMessages() {
		if(messageCount() > 0 && historyCount() > 0) {
			int ticks = mc().gui.getGuiTicks();
			var visibles = ((ChatHudAccess) mc().gui.getChat()).chatpatches$getVisibleMessages();

			// sets all messages (restored and boundary line) to an addedTime of -200 to prevent instant rendering! (#42)
			// now adds the message's addedTime to account for any extra offsets from the deserialization desync from the main game thread
			for(int i = 0; i < visibles.size(); i++) {
				var ln = visibles.get(i);
				if(ticks - ln.addedTime() < 200) {
					visibles.set(i, new GuiMessage.Line(-(200 + ln.addedTime()), ln.content(), ln.tag(), ln.endOfEntry()));
				} else {
					break; // only the most recent messages need to be checked, the rest are guaranteed to be old enough
					// note: this assumes the list is continuous, which should always be true, but who knows
				}
			}
		}
	}

    /**
     * Attempts to load the chat log from {@link #PATH} and restore it into the game.
	 * Only does so if the chat log is {@linkplain Config#chatlog enabled in the config}
	 * and hasn't been deserialized yet (when {@link #messages} and {@link #history}
	 * are both still equal to {@link #EMPTY_LIST}). <b>Executed on an {@linkplain
     * Util#ioPool() I/O worker thread} to avoid freezing the render
	 * thread.</b>
	 *
	 * @param force {@code true} to force loading the chat log even if it's been
	 * loaded in the current session, {@code false} otherwise.
     */
    public static void load(boolean force) {
        if(config.chatlog && ((messages == EMPTY_LIST && history == EMPTY_LIST) || force)) {
			ChatPatches.executeIoTask(() -> {
				deserialize();
				restore(); // doesn't need to be executed on the I/O thread but requires sequential execution
			});
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
        if(config.chatlogSaveInterval == 0 && (screen instanceof PauseScreen || !mc().isWindowActive())) {
			serialize();
		}
    }
}