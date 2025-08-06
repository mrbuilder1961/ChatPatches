package obro1961.chatpatches;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import obro1961.chatpatches.config.Config;
import obro1961.chatpatches.util.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static obro1961.chatpatches.util.TextUtils.asText;

public class ChatPatches implements ClientModInitializer {
	public static final String MOD_ID = "chatpatches";
	public static final Logger LOGGER = LoggerFactory.getLogger("Chat Patches"); //prepub: custom impl that overrides everything to call #getBracedCaller(.) to simplify log messages??

	public static Config config = Config.initialize();

	public static ResourceLocation id(String path) {
		return ResourceLocation.tryBuild(MOD_ID, path);
	}


	@Override
	public void onInitializeClient() {
		// todo: gametests! somewhere somehow!

		// -- chat log saving events --
		// according to my testing, this event works as needed when the game disconnects and on crashes if the game is functional at that point
		// testing details (server=hypixel): normal disconnects work on both world and server, manual F3+C crash works on world but NOT server
		// honestly I don't care if it fails on crashes, its fixable A) through the save interval or B) by fixing the crash's source
		//stonecutter: * events *
		ClientPlayConnectionEvents.DISCONNECT.register((network, client) -> ChatLog.serialize());
		ScreenEvents.AFTER_INIT.register((client, screen, sW, sH) -> ChatLog.saveIfPaused(screen));
		ClientTickEvents.END_WORLD_TICK.register(world -> ChatLog.tickSaveCounter());

		// -- chat log loader and boundary sender --
		ClientPlayConnectionEvents.JOIN.register((network, packetSender, client) -> {
			// loads once
			ChatLog.load(false);
			// loads every time
			config.sendBoundaryLine(); // doesn't run the first time because of the slight delay before the chat log initializes messages
			ChatLog.hideRecentMessages();
		});

		LOGGER.info("[ChatPatches()] Finished setup!");
	}


	/**
	 * @return The class and method of the caller, acquired from a {@link
	 * StackWalker}, formatted like {@code [$class.$method]}. Additionally, denotes
	 * lambda callers after the method name with {@code (->n)}, where {@code n} is
	 * the index of the lambda as written in the calling class, to make debugging
	 * easier. If the calling class is anonymous, hidden, or unnamed, it will be
	 * reflected in the output as its state surrounded by angle brackets (ex. {@code
	 * <hidden>}). If the method is a static initializer, it will be reflected as
	 * {@code <static_init>}; not to be confused with
	 * <a href="https://stackoverflow.com/questions/2420389/static-initialization-blocks">
	 * static initializer blocks</a>, which are automatically reflected as {@code
	 * <clinit>}.
	 *
	 * @implNote The lambda index is only added if the method name follows the pattern
	 * {@code lambda$method$n} or {@code lambda$static$n}, where {@code n} is the index
	 * of the lambda in the class. <b>This does not apply to mixin and/or nested
	 * lambdas</b> due to their sheer complexity (ex.
	 * {@code abcd6789$mod_id$lambda$method$n$m}) and rarity, so they are treated as
	 * regular methods.
	 *
	 * @param skipExtraFrame If true, skips the first two frames of the stack trace
	 *                       instead of just one. Intended for methods like {@link
	 *                       #logReportMsg(Throwable)} and {@link #logDuration(long, long)}
	 *                       that call this method themselves, so that the caller is
	 *                       reflected accurately.
	 */
	public static String getBracedCaller(boolean skipExtraFrame) {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		StackWalker.StackFrame caller = walker.walk(frames -> frames.skip(skipExtraFrame ? 2 : 1).findFirst().orElseThrow());
		String clazz = caller.getDeclaringClass().getSimpleName();
		String method = caller.getMethodName();
		String lambda = method.startsWith("lambda$") ? String.format("(->%s)", method.substring(method.lastIndexOf("$") + 1)) : "";

		if(clazz.isEmpty()) {
			Class<?> callingClass = caller.getDeclaringClass();
			if(callingClass.isAnonymousClass())
				clazz = "<anonymous>";
			else if(callingClass.isHidden())
				clazz = "<hidden>";
			else
				clazz = "<unnamed>"; // preview feature so it goes in the else
		}

		if(method.startsWith("lambda$static$")) // goes first so it doesn't get masked by the next check
			method = "<static_init>";
		else if(!lambda.isEmpty())
			method = method.substring(7, method.lastIndexOf("$")); // removes the 'lambda$' (l=7) and the '$n' at the end to get the method name

		return String.format("[%s.%s%s]", clazz, method, lambda);
	}

	/**
	 * Logs an error-level message telling the user to report the given error.
	 * Caller details provided by {@link #getBracedCaller(boolean)}.
	 *
	 * <p>Outputs the following message:
	 * <pre>
	 * [$caller] /!\ Please report this error on GitHub or Discord with the full log file attached! /!\
	 * $error
	 * </pre>
	 */
	public static void logReportMsg(@NotNull Throwable error) {
		// logging the message regularly makes the logger treat the error like an Object and not a Throwable, so it doesn't print the stack trace -_-
		String message = getBracedCaller(true) + " /!\\ Please report this error on GitHub or Discord with the full log file attached! /!\\";
		LOGGER.error(message, error);
	}

	/**
	 * Logs how long the caller took to execute, according to {@code start} and the
	 * {@linkplain System#currentTimeMillis() current time}. If the duration is less
	 * than the given {@code threshold}, an info message is logged. Otherwise, logs a
	 * warning message noting the excessive duration and suggesting the user report
	 * it. In both cases the duration is converted to a {@code double} and logged in
	 * seconds.
	 *
	 * @param start The time, in milliseconds, when the caller started execution.
	 * Should be a previously recorded value from {@link System#currentTimeMillis()}.
	 * @param threshold The maximum duration, in milliseconds, that the caller should
	 * take to execute.
	 */
	public static void logDuration(long start, long threshold) {
		// store and convert to seconds
		double duration = (double) (System.currentTimeMillis() - start) / 1000;
		double max = (double) threshold / 1000;

		String caller = getBracedCaller(true);
		if(duration >= max) {
			LOGGER.warn("{} Took {} seconds, but should've taken less than {}s. Consider reporting this to the GitHub or Discord", caller, duration, max);
		} else {
			LOGGER.info("{} Took {} seconds", caller, duration);
		}
	}

	/**
	 * Submits the given task to an {@linkplain Util#ioPool() I/O worker
	 * thread} for execution, and returns a {@link CompletableFuture} for managing
	 * the results. If the task's results are not needed, perhaps in cases where
	 * exceptions are managed by the task itself or for saving actions, the returned
	 * {@link CompletableFuture} may be safely ignored.
	 *
	 * @apiNote Methods such as {@link CompletableFuture#orTimeout(long, TimeUnit)},
	 * by nature, <b>will block</b>, and as such should <b>probably not be used</b>.
	 */
	public static /*CompletableFuture<Void>*/ void executeIoTask(Runnable task) {
		final var IO_POOL = Util.ioPool(); // var for stonecutter!
		/*return*/ CompletableFuture.runAsync(task, IO_POOL).exceptionallyAsync(e -> {
			logReportMsg(e);
			return null;
		}, IO_POOL);
	}

	private static void pushToast(boolean error, Object header, Object description) {
		final int MAX_LEN = 60; // minimizes errors going off-screen
		MutableComponent head = TextUtils.truncate(asText(header), MAX_LEN);
		MutableComponent desc = TextUtils.truncate(asText(description), MAX_LEN);

		if(!head.equals(asText(header))) {
			head.append(CommonComponents.ELLIPSIS.copy().withStyle(ChatFormatting.GRAY));
		}
		if(!desc.equals(asText(description))) {
			desc.append(CommonComponents.ELLIPSIS.copy().withStyle(ChatFormatting.GRAY));
		}
		/*if(ChatFormatting.stripFormatting(desc.getString()).length() > MAX_LEN) {
			desc = TextUtils.truncate(desc, MAX_LEN).append(CommonComponents.ELLIPSIS.copy().withStyle(ChatFormatting.GRAY));
		}*/

		SystemToast.add(
			Minecraft.getInstance()./*?if >=1.21.2 {*/getToastManager/*?} else {*//*getToasts*//*?}*/(),
			error
				? SystemToast./*?if >=1.20.3 {*/SystemToastId/*?} else {*//*SystemToastIds*//*?}*/.PACK_LOAD_FAILURE
				: SystemToast./*?if >=1.20.3 {*/SystemToastId/*?} else {*//*SystemToastIds*//*?}*/.PERIODIC_NOTIFICATION,
			head,
			desc
		);
	}

	public static void pushErrorToast(Object header, Object description) {
		pushToast(true, header, description);
	}

	public static void pushInfoToast(Object header, Object description) {
		pushToast(false, header, description);
	}

	//prepub: move this stuff to a util class?
	/** @see #regBack(DynamicOps) */
	public static DynamicOps<JsonElement> regJsonOps() {
		return regBack(JsonOps.INSTANCE);
	}

	/**
	 * Returns a registry-backed copy of {@code ops}, provided by the {@linkplain
	 * Minecraft#level client's world}, to not crash when serializing. Fixes
	 * <a href="https://github.com/mrbuilder1961/ChatPatches/issues/180">#180</a>.
	 * Thanks to
	 * <a href="https://discord.com/channels/507304429255393322/721100785936760876/1278519812628156528">arkosammy12</a>
	 * for the help!
	 */
	public static <T> /*? if >=1.20.5 {*/RegistryOps/*?} else {*//*DynamicOps*//*?}*/<T> regBack(DynamicOps<T> ops) {
		//? if >=1.20.5 {
		if(Minecraft.getInstance().level instanceof ClientLevel world) {
			return world.registryAccess().createSerializationContext(ops);
		} else {
			logReportMsg(new NullPointerException("Expected existing client world"));
		}
		//? }
		return /*? if >=1.20.5 {*/(RegistryOps<T>)/*?}*/ ops;
	}
}