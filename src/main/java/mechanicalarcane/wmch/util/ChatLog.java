package mechanicalarcane.wmch.util;

import static mechanicalarcane.wmch.WMCH.LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.config.Option;
import net.minecraft.SharedConstants;
import net.minecraft.text.Text;

/**
 * Represents the chat log file in the
 * run directory located at {@code ./logs/chatlog.json}.
 */
public class ChatLog {
    private static final File file = new File( WMCH.FABRICLOADER.getGameDir().toFile().getAbsolutePath() + "/logs/chatlog.json" );
    private static final Gson json = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(Text.class, new JsonSerializer<Text>() {
            @Override public JsonElement serialize(Text src, Type typeOfSrc, JsonSerializationContext context) {
                return Text.Serializer.toJsonTree(src);
            }
        })
        .registerTypeAdapter(Text.class, new JsonDeserializer<Text>() {
            @Override public Text deserialize(JsonElement json, Type typeOfSrc, JsonDeserializationContext context) {
                return Text.Serializer.fromJson(json);
            }
        })
        .registerTypeAdapter(Text.class, new InstanceCreator<Text>() {
            @Override public Text createInstance(Type type) {
                return Text.empty();
            }
        })
    .create();

    private static boolean initialized = false;
    private static boolean savedAfterCrash = false;
    private static ChatLog.Data data = new Data(100);
    private static FileChannel channel;
    private static FileInputStream inStream;
    private static String rawData;

    public static boolean loaded = false;

    /** Internal class for serializing, used seperately from ChatLog for simplification */
    private static class Data {
        public List<Text> messages;
        public List<String> history;

        private Data(int size) {
            messages = Lists.newArrayListWithExpectedSize(size);
            history = Lists.newArrayListWithExpectedSize(size);
        }
    }


    /** Initializes the chatlog file and opens all file-related connections. */
    public static void initialize() {
        if(!initialized) {
            try {
                channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                inStream = new FileInputStream(file);
                rawData = new String(inStream.readAllBytes());

                initialized = true;
            } catch(IOException e) {
                LOGGER.error("[initialize] Couldn't create chatlog file connections:", e);
            }
        }
    }


    /**
     * Deserializes the chat log and resolves message data from it.
     * First detects if it is reading the first version of the
     * chatlog, and then updates it to match with the current version.
     * Then proceeds as normal and deserializes the log into an
     * instance of {@code ChatLog.Data} at {@code ChatLog.data}.
     */
    public static void deserialize() {
        long fileSize = -1;
        if( !rawData.startsWith("{") && rawData.length() > 1 ) {
            LOGGER.info("Old chatlog file type detected, updating...");
            try {
                write("{\"history\":[],\"messages\":");
                channel.position(channel.size());
                write("}");

                fileSize = channel.size();
            } catch (IOException e) {
                LOGGER.error("[deserialize] An I/O error occurred while trying to update the chat log:", e);
            }
        } else if(rawData.length() < 2) {
            data = new Data(100);
            loaded = true;

            return;
        }

        try {
            data = json.fromJson(rawData, Data.class);
            enforceSizes(Option.MAX_MESSAGES.get());
        } catch (com.google.gson.JsonSyntaxException e) {
            LOGGER.error("[deserialize] Tried to read the ChatLog and found an error, loading an empty one: ", e);

            data = new Data(100);
            loaded = true;
            return;
        }

        loaded = true;

        LOGGER.info("Read chat log{}, containing {} messages and {} sent messages from ./logs/chatlog.json",
			fileSize != -1 ? "using " + fileSize + " bytes of data" : "", data.messages.size(), data.history.size()
		);
    }

    public static void serialize(boolean crashing) {
        if(crashing && savedAfterCrash)
            return;

        try {
            enforceSizes(Option.MAX_MESSAGES.get());
            final String stringified = json.toJson(data, Data.class);

            // removes any overflowing file data so no corrupted JSON is stored
            channel.truncate(stringified.getBytes().length);
            write(stringified);

            LOGGER.info("Saved chat log containing {} messages and {} sent messages to ./logs/chatlog.json", data.messages.size(), data.history.size());
        } catch (IOException e) {
            LOGGER.error("[serialize] An I/O error occurred while trying to write the chat log:", e);
        } finally {
            if(crashing)
                savedAfterCrash = true;
        }
    }


    /** Shorthand for writing Strings to {@code ChatLog.channel} */
    private static void write(String str) throws IOException {
        channel.write( ByteBuffer.wrap(str.getBytes()) );
        channel.position(0);
    }

    /** Removes all overflowing data from {@code ChatLog.data} with an index greater than {@code max}. */
    private static void enforceSizes(int max) {
        data.messages.removeIf(msg -> data.messages.indexOf(msg) > Math.abs(max));
        data.history.removeIf(sent -> data.history.indexOf(sent) > Math.abs(max));
    }


    /**
     * Adds a chat message to the chat log, returns true if it was added.
     * @param msg Message to add
     * @param max The number specifying the limit of cached messages allowed
     */
    public static boolean addMessage(Text msg, int max) {
        return (data.messages.size() < Math.abs(max)) ? data.messages.add(msg) : false;
    }
    /**
     * Adds a sent message to the chat log, returns true if it was added.
     * @param msg History to add
     * @param max The number specifying the limit of cached history allowed
     */
    public static boolean addHistory(String msg, int max) {
        return (data.history.size() < Math.abs(max)) ? data.history.add( SharedConstants.stripInvalidChars(msg) ) : false;
    }
    public static void clearMessages() { data.messages.clear(); }
    public static void clearHistory() { data.history.clear(); }
    public static List<Text> getMessages() { return data.messages; }
    public static List<String> getHistory() { return data.history; }
}
