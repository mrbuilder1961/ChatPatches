package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import joptsimple.internal.Strings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.util.Util;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.mixin.security.ClickEvent$ActionMixin;
import obro1961.chatpatches.util.TextUtil;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
     * Serializes as a {@link Pair} to avoid needing a dedicated class.
	 * {@link #messages} are first and {@link #history} is second, and the native
	 * list is mapped to a {@linkplain ChatLog#newSyncedObjectList(List) synchronized
	 * mutable object list}. Uses the {@linkplain TextUtil#UNSAFE_CODEC unsafe codec}
	 * to ensure all messages can be serialized.
	 *
	 * @see ClickEvent$ActionMixin#allowConditionalSerialization(boolean)
     */
    public static final Codec<Pair<ObjectList<Component>, ObjectList<String>>> CODEC = Codec.pair(
		TextUtil.UNSAFE_CODEC
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
    public static final GuiMessageTag RESTORED_INDICATOR = new GuiMessageTag(0x382FB5, /*not realistically doable rn*/ null, Component.translatable("text.chatpatches.restored"), "Restored");

    private static final int DEFAULT_SIZE = 100;
	private static final int IO_THRESHOLD_SUGGESTION = 1000;
	private static final String EMPTY_JSON = "{\"messages\":[],\"history\":[]}";
	/**
	 * Convenience object that's also used for determining if the chat log has
	 * been deserialized yet.
	 */
	private static final ObjectList<?> EMPTY_LIST = newSyncedObjectList(null);

	private static Minecraft mc() { return Minecraft.getInstance(); }

    /**
     * Used to suspend the addition of messages and access to the chat log
	 * while restoring. Prevents log spam of restored messages and other
	 * related issues like {?}.
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

    public static void addMessage(Component message) {
        if(restoring) {
			return;
		}

        enforceLimits();
        messages.add(message);
    }
    public static void addHistory(String sentMessage) {
        if(restoring) {
			return;
		}

        enforceLimits();
        history.add(sentMessage);
    }

	public static void removeMessage(Component message) {
		boolean changed = true;
		try {
			changed = messages.remove(message);
		} catch(IndexOutOfBoundsException e) {
			logReportMsg(e);
		}

		if(!changed) {
			LOGGER.warn("Couldn't find message '{}' to remove", message.getString());
		}
	}
	/*public static void removeHistory(String sentHistory) {
		boolean changed = true;
		try {
			changed = history.remove(sentHistory);
		} catch(IndexOutOfBoundsException e) {
			logReportMsg(e);
		}

		if(!changed) {
			LOGGER.warn("Couldn't find sent message '{}' to remove", sentHistory);
		}
	}*/ // commented out bc currently unused but here for continuity

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
        if(messageCount() > config.chatMaxMessages) {
			messages.removeElements(0, messageCount() - config.chatMaxMessages);
		}

        if(historyCount() > config.chatMaxMessages) {
			history.removeElements(0, historyCount() - config.chatMaxMessages);
		}
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
        LOGGER.info("Reading...");

        if(Files.exists(PATH)) {
			try {
                rawJson = Files.readString(PATH); // chat log is always encoded with UTF-8
			} catch(MalformedInputException notUTF8) {
                Charset def = Charset.defaultCharset();
                LOGGER.warn("File encoding was '{}', not UTF-8. Complex text characters may have been corrupted!", def.name());

                try {
                    rawJson = Files.readString(PATH, def); // maybe it's using the default encoding
				} catch(IOException e) {
                    LOGGER.error("Couldn't parse '{}' in UTF-8 or '{}', generating a new one:", PATH, def.name(), e);
                    rawJson = EMPTY_JSON;
                    pushErrorToast("Chat log encoding error", "Expected UTF-8 or '%s'".formatted(def.name()));
                    backup();
                }
            } catch(IOException e) {
                LOGGER.error("Something went wrong accessing '{}':", PATH, e);
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

				//~ !j21_get_first
                messages = deserializedPair.getFirst();
                history = deserializedPair.getSecond();
				//~ j21_get_first
            }

            enforceLimits();
            updateMessagesLogged();

			LOGGER.info("Parsed {} messages and {} sent messages!", lastMessageCount, lastHistoryCount);
        } catch(RuntimeException e) {
            LOGGER.error("An unexpected error occurred while trying to parse '{}', backing it up and generating a new one:", PATH, e);
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
        if(!config.chatlog) {
			return;
		}
        if((messages.size() == lastMessageCount && history.size() == lastHistoryCount) || (messages.isEmpty() && history.isEmpty())) {
			return; // don't write empty or old data
		}

		executeIoTask(() -> {
			long start = System.currentTimeMillis();
			LOGGER.info("Saving...");

			try {
				DataResult<JsonElement> result = CODEC.encodeStart(ChatPatches.regJsonOps(), Pair.of(messages, history));
				JsonElement json = result.result().orElse(null);
				String data = GsonHelper.toStableString(json);
				Path path = PATH;

				if(json == null) {
					// noinspection Convert2MethodRef: makes stonecutter life easier
					var err = result.error().map(e -> e.message()).orElse(ChatFormatting.RED + "Unknown cause");
					path = PATH.resolveSibling("chatlog_dump_" + Util.getFilenameFormattedDateTime() + ".json");

					LOGGER.warn("Failed to serialize chat log");
					logReportMsg(new JsonParseException(err));
					pushErrorToast("Chat log codec error", err);

					data = EMPTY_JSON
						.replace/*All*/("[],", messages.stream()
							// codec is unusable here
							.map(Component::getString) // some message with "quotes"
							.map(ChatLog::escapeAndSurround) // "some message with \"quotes\""
							.map(str -> "{\"extra\":[\"\"," + str + ",\"\"],\"text\":\"\"}") // equivalent to ChatUtil.buildMessage(null, null, str, null)
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

				LOGGER.info("Saved {} messages and {} sent messages to '{}'!", lastMessageCount, lastHistoryCount, path);
			} catch(IOException | RuntimeException e) {
				LOGGER.error("An unexpected error occurred while trying to save:", e);
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
		executeIoTask(() -> {
			try {
				Path backupPath = PATH.resolveSibling("chatlog_" + Util.getFilenameFormattedDateTime() + ".json");
				Files.copy(PATH, backupPath);
				LOGGER.info("Successfully backed up the current chat log to '{}'!", backupPath);
			} catch(IOException e) {
				LOGGER.warn("Couldn't backup '{}':", PATH, e);
				pushErrorToast("Chat log backup error", e.getLocalizedMessage());
			}
		});
	}

	/**
	 * @see #deserialize()
	 * @see #load(boolean)
	 */
    public static void restore() {
        if(messageCount() > 0 || historyCount() > 0) {
			/*? if >=1.21.9 {*/RenderSystem.assertOnRenderThread();/*?}*/
			ChatComponent chat = mc().gui.getChat();

			// todo i think we just need to mixin to the delayed message queue thing, and here we cache the current setting, set it to ~5s delay, and mark some flag field true to be used in the mixin(s)!

			restoring = true;
			history.forEach(chat::addRecentChat);
			messages.forEach(msg -> chat.addMessage(msg, null, /*? if >=26.1 {*/GuiMessageSource.SYSTEM_CLIENT,/*?}*/ RESTORED_INDICATOR));
			restoring = false;

			config.sendBoundaryLine(); // ensures the check that the chat isn't empty passes, which often doesn't due to multithreading
			hideRecentMessages();
		}

		LOGGER.info("Restored {} messages and {} history messages!", messageCount(), historyCount());
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
			var visibles = mc().gui.getChat().trimmedMessages;

			// sets all messages (restored and boundary line) to an addedTime of -200 to prevent instant rendering! (#42)
			// now adds the message's addedTime to account for any extra offsets from the deserialization desync from the main game thread
			for(int i = 0; i < visibles.size(); i++) {
				var l = visibles.get(i);
				if(ticks - l.addedTime() < 200) {
					int newTime = -(200 + l.addedTime());
					/*? if >=26.1 {*/var lp = l.parent();/*?}*/
					visibles.set(i, new GuiMessage.Line(
						/*? if >=26.1 {*/new GuiMessage(newTime, lp.content(), lp.signature(), lp.source(), lp.tag())/*?} else {*//*newTime*//*?}*/,
						l.content(),
						/*? if <=1.21.11 {*//*l.tag(),*//*?}*/
						l.endOfEntry()
					));
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
	 *
	 * @implNote Since 1.21.9, {@link #restore()} must be executed on the main render
	 * thread, presumably due to accessing the chat HUD. This is currently achieved through
	 * {@link Minecraft}'s implementation of {@link java.util.concurrent.Executor}, which
	 * assumes the render thread will always be used.
     */
    public static void load(boolean force) {
        if(config.chatlog && ((messages == EMPTY_LIST && history == EMPTY_LIST) || force)) {
			executeIoTask(ChatLog::deserialize).thenAcceptAsync(x -> restore(), mc());
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
        if(config.chatlogSaveInterval > 0 && ticksUntilSave == 0) {
			serialize();
		}

        ticksUntilSave--;

        if(ticksUntilSave < 0) {
			ticksUntilSave = config.chatlogSaveInterval * SharedConstants.TICKS_PER_MINUTE;
		}
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