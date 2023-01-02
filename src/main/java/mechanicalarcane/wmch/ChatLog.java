package mechanicalarcane.wmch;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import mechanicalarcane.wmch.config.Config;
import mechanicalarcane.wmch.util.Util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static java.io.File.separator;
import static mechanicalarcane.wmch.WMCH.LOGGER;
import static mechanicalarcane.wmch.WMCH.config;

/**
 * Represents the chat log file in the
 * run directory located at {@link ChatLog#CHATLOG_PATH}.
 */
public class ChatLog {
    public static final String CHATLOG_PATH = WMCH.FABRICLOADER.getGameDir().toString() + separator + "logs" + separator + "chatlog.json";
    private static final File file = new File(CHATLOG_PATH);
    private static final Gson json = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(Text.class, (JsonSerializer<Text>) (src, typeOfSrc, context) -> Text.Serializer.toJsonTree(src))
        .registerTypeAdapter(Text.class, (JsonDeserializer<Text>) (json, typeOfSrc, context) -> Text.Serializer.fromJson(json))
        .registerTypeAdapter(Text.class, (InstanceCreator<Text>) type -> Text.empty())
    .create();

    private static boolean initialized = false;
    private static boolean savedAfterCrash = false;
    private static ChatLog.Data data = new Data( Data.DEFAULT_SIZE );
    private static FileChannel channel;
    private static String rawData = "{\"history\":[],\"messages\":[]}"; // prevents a few errors if the channel doesn't initialize

    public static boolean loaded = false;


    /** Micro class for serializing, used separately from ChatLog for simplification */
    private static class Data {
        public static int DEFAULT_SIZE = 100;

        public List<Text> messages;
        public List<String> history;

        private Data(int size) {
            messages = Lists.newArrayListWithExpectedSize(size);
            history = Lists.newArrayListWithExpectedSize(size);
        }
    }


    /** Initializes the ChatLog file and opens file connections. */
    public static void initialize() {
        if(!initialized) {

            try( FileInputStream inStream = new FileInputStream(file) ) {

                channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                rawData = new String(inStream.readAllBytes());

                initialized = true;
            } catch(IOException e) {
                LOGGER.error("[ChatLog()] Couldn't create file connections:", e);
            }
        }
    }


    /**
     * Deserializes the chat log and resolves message data from it.
     * First detects if it is reading the first version of the
     * ChatLog, and then updates it to match with the current version.
     * Then proceeds as normal and deserializes the log into an
     * instance of {@code ChatLog.Data} at {@code ChatLog.data}.
     */
    public static void deserialize() {
        long fileSize = -1;

        if( !rawData.startsWith("{") && rawData.length() > 1 ) {
            LOGGER.info("[ChatLog.deserialize] Old ChatLog file type detected, updating...");
            try {
                write("{\"history\":[],\"messages\":");
                channel.position( channel.size() );
                write("}");

                fileSize = channel.size();
            } catch (IOException e) {
                LOGGER.error("[ChatLog.deserialize] An I/O error occurred while trying to update the chat log:", e);
            }
        } else if(rawData.length() < 2) {
            data = new Data( Data.DEFAULT_SIZE );
            loaded = true;

            return;
        }

        try {
            data = json.fromJson(rawData, Data.class);
            enforceSizes();
        } catch (com.google.gson.JsonSyntaxException e) {
            LOGGER.error("[ChatLog.deserialize] Tried to read the ChatLog and found an error, loading an empty one: ", e);

            data = new Data( Data.DEFAULT_SIZE );
            loaded = true;
            return;
        }

        loaded = true;

        LOGGER.info("[ChatLog.deserialize] Read the chat log {} containing {} messages and {} sent messages from '{}'",
			fileSize != -1 ? "(using "+fileSize+" bytes of data)" : "",
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

            // removes any overflowing file data so no corrupted JSON is stored
            channel.truncate(str.getBytes().length);
            write(str);


            LOGGER.info("[ChatLog.serialize] Saved the chat log containing {} messages and {} sent messages to '{}'", data.messages.size(), data.history.size(), CHATLOG_PATH);

        } catch (IOException e) {

            if(crashing) {
                LOGGER.warn("[ChatLog.serialize] An I/O error occurred while trying to save the chat log after a crash:", e);
                LOGGER.debug("[ChatLog.serialize] Salvaged chat log data:\n{}", rawData);
            } else
                LOGGER.error("[ChatLog.serialize] An I/O error occurred while trying to save the chat log:", e);

        } finally {
            if(crashing)
                savedAfterCrash = true;
        }
    }


    /**
     * Shorthand for writing Strings to {@code ChatLog.channel},
     * writes the string and then sets the channel's position to 0.
     */
    private static void write(String str) throws IOException {
        channel.write( ByteBuffer.wrap(str.getBytes()) );
        channel.position(0);
    }

    /** Removes all overflowing data from {@code ChatLog.data} with an index greater than {@link Config#maxMsgs}. */
    private static void enforceSizes() {
        if(data.messages.size() > config.maxMsgs)
            data.messages = data.messages.subList(0, config.maxMsgs + 1);

        if(data.history.size() > config.maxMsgs)
            data.history = data.history.subList(0, config.maxMsgs + 1);
    }

    public static void restore(MinecraftClient client) {
        Util.Flags.LOADING_CHATLOG.set();

        if(data.history.size() > 0)
            data.history.forEach(client.inGameHud.getChatHud()::addToMessageHistory);
        if(data.messages.size() > 0)
            data.messages.forEach(msg -> client.inGameHud.getChatHud().addMessage(
                msg, MessageSignatureData.EMPTY, new MessageIndicator(0x382fb5, null, null, "Restored")
            ));

        Util.Flags.LOADING_CHATLOG.remove();
        LOGGER.info("[ChatLog.restore] Restored {} messages and {} history messages from '{}' into Minecraft!", data.messages.size(), data.history.size(),
            CHATLOG_PATH);
    }


    public static void addMessage(Text msg) {
        if(data.messages.size() < config.maxMsgs)
            data.messages.add(msg);
    }
    public static void addHistory(String msg) {
        if(data.history.size() < config.maxMsgs)
            data.history.add(msg);
    }
    public static void clearMessages() { data.messages.clear(); }
    public static void clearHistory() { data.history.clear(); }
}
