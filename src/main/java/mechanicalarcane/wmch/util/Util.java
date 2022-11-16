package mechanicalarcane.wmch.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;

import mechanicalarcane.wmch.WMCH;
import mechanicalarcane.wmch.config.Option;
import mechanicalarcane.wmch.mixin.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.MessageMetadata;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Random functions that are of some random, obsolete use
 */
public class Util {
	public static final UUID NIL_UUID = new UUID(0, 0);
	public static final GameProfile NIL_PROFILE = new GameProfile(NIL_UUID, "");
	public static final MessageMetadata NIL_METADATA = new MessageMetadata(NIL_UUID, Instant.EPOCH, 0);
	public static final String CONFIG_PATH = WMCH.FABRICLOADER.getConfigDir().toString() + "/wmch.json";
	public static final String CHATLOG_PATH = WMCH.FABRICLOADER.getGameDir().toString() + "/logs/chatlog.json";

	public static enum Flag {
		INIT(0b1000),
		//NORMAL(0b0000),
		LOADING_CHATLOG(0b0001),
		RESET_NORMAL(0b0010),
		RESET_FINISHING(0b0100),
		RESET_FINAL(0b0110);

		/**
		 * Flag usage (bitwise):
		 * <table>
		 * 	 <tr><th>Binary Value |</th> <th>Modify message? |</th> <th>Meaning</th></tr>
		 *   <tr><th>1000</th> <th>no</th> <th>nothing done yet</th></tr>
		 *   <tr><th>0000</th> <th>yes</th> <th>normal (done loading chatlog.json)</th></tr>
		 *   <tr><th>0001</th> <th>no</th> <th>loading chatlog.json</th></tr>
		 * 	 <tr><th>0010 | 0100</th> <th>no</th> <th>changing/resetting chat settings</th></tr>
		 * </table>
		 * These are used to fix bugs with messages modifying when they shouldn't be
		 * and to help other methods work better.
		 */
		public static int flags = INIT.value;
		private final int value;

		private Flag(int value) {
			this.value = value;
		}

		public static String binary() { return Integer.toBinaryString(flags); }
		public static boolean hasAll(Flag... flags) {
			boolean hasFlags = true; //* 1010 // 1000, 0100 | 0010

			for(Flag f : flags) {
				if( !f.isSet() )
					return false;

				hasFlags = hasFlags && f.isSet();
			}

			return hasFlags;
		}

		public int value() { return value; } // get const flag value
		public void set() { if( !isSet() ) flags |= value; } // set this flag
		public void toggle() { flags ^= value; } // invert this flag
		public void unSet() { if( isSet() ) toggle(); } // remove this flag
		public boolean isSet() { return (flags & value) == value; } // true if flags has this/these bit(s) set
		//public boolean otherFlagsSet() { return (isSet() ? (flags ^ value) : flags) > 0; } // true if this flag is not the *sole* flag set
	}


	/**
	 * Takes a {@code UUID} and
	 * {@code NetworkHandler} and searches
	 * for a player with the id provided,
	 * otherwise returns {@code NIL_PROFILE}.
	 */
	public static GameProfile getProfile(MinecraftClient client, UUID id) {
		List<PlayerListEntry> players = new ArrayList<>( client.getNetworkHandler().getPlayerList() );

		for(PlayerListEntry player : players) {
			GameProfile prof = player.getProfile();

			if( prof.getId().equals(id) )
				return prof;
		}

		return NIL_PROFILE;
	}

	/** A shorthand method for accessing methods from {@code client}'s {@code net.minecraft.client.gui.hud.ChatHud} object. */
	public static ChatHudAccessor accessChatHud(MinecraftClient client) {
		return ((ChatHudAccessor) client.inGameHud.getChatHud());
	}

	/** If there's space to overwrite, runs {@code list.set(index, object)}. Otherwise runs {@code list.add(index, object)}. */
	public static <T> List<T> setOrAdd(List<T> list, final int index, T object) {
		if(list.size() > index)
			list.set(index, object);
		else
			list.add(index, object);

		return list;
	}

	/**
	 * Takes an {@code Iterable} and a {@code Predicate}, and tests the
	 * {@code Predicate} on each item. {@return Returns a list of the items that
	 * passed the test, or an empty list if none passed.}
	 * @param iterable The Iterable to search in.
	 * @param test Predicate to find the item you're looking for.
	 */
	public static <T> List<T> find(Iterable<T> iterable, Predicate<T> test) {
		List<T> matches = Lists.newArrayList();

		for(T item : iterable)
			if(test.test(item))
				matches.add(item);

		return matches;
	}



	public static String delAll(String str, String... regexes) {
		for(String regex : regexes)
			str = str.replaceAll(regex, "");

		return str;
	}

	/** Removes all ampersand + formatting code sequences from {@code formatted}. */
	public static String strip(String formatted) {
		return delAll(formatted, "(?i)(?<!\\\\)(&[0-9a-fk-or])+");
	}

	/** Returns a list of all substrings in {@code input} that matched {@code regex}. */
	public static List<String> capture(String regex, String input) {
		Matcher matcher = Pattern.compile(regex).matcher(input);
		List<String> captures = Lists.newArrayListWithCapacity(0); //? err from 0

		while(matcher.find())
			captures.add( matcher.group() );

		return captures;
	}

	public static boolean isBoundaryLine(String text) {
		return text.equals( strip(Option.BOUNDARY_STR.get()) ) || text.equals( strip(Option.BOUNDARY_STR.get()) );
	}

	/** Constructs a Text object from an OrderedText */
	public static String reorder(OrderedText ot) {
		StringBuilder builder = new StringBuilder();
		// styles and positions are used for re-applying Styles to sections of text
		/* List<Style> styles = Lists.newArrayList();
		List<Integer> positions = Lists.newArrayList(); */


		ot.accept(new CharacterVisitor() {
			@Override
			public boolean accept(int ix, Style style, int cp) {
				builder.append(Character.toString(cp));

				/* if( !styles.contains(style) ) {
					styles.add( style );
					positions.add( builder.length() );
				} */

				return true;
			}
		});

		/* MutableText styled = Text.literal( builder.substring(0, positions.get(0)) ).setStyle( styles.get(0) );
		for(int i = 1; i < styles.size(); ++i) {
			final int end = ((i + 1) == styles.size()) ? builder.length() : positions.get(i + 1);

			styled.append(
				Text.literal( builder.substring(positions.get(i), end) )
				.setStyle( styles.get(i) )
			);
		} */


		return builder.toString();
	}

	/**
	 * Turns a String formatted like {@code "&4Dark RED &l&33AAffCUSTOM BOLD BLUE"}
	 * into a Text with all {@code &<?>} codes replaced with styled colors.
	 * If there are no formatting characters, returns the unstyled string.
	 * Doesn't support hex colors.
	 *
	 * @param plain String to format
	 */
	public static MutableText formatString(String plain) {
		final String formatCode = "(?i)(?m)(?<!\\\\)(&[0-9a-fk-or])+"; // case-insensitive, multiline, ignore backslashes before
		MutableText output = Text.empty();

		List<String> texts = Lists.newArrayList( Stream.of( plain.split(formatCode) ).toList() );
		// if startswith code => remove empty string
		// doesnt start with code => move before code to output (append(str))
		if(texts.size() > 0) {
			if( // moves the first text to output if it doesn't have a format code OR if its padded (whitespace, not empty)
				( texts.get(0).isBlank() && !texts.get(0).isEmpty() && !plain.matches("^" + formatCode + ".+$") )
				|| ( !plain.matches("^" + formatCode + ".+$") )
			)
				output.append( texts.remove(0) );
			else
				texts.remove(0);
		}

		// Captures formatting codes from input string, removes dupes, and orderes each group into a char list
		List<ArrayList<Formatting>> fCodes = (
			capture(formatCode, plain).stream().map(codes -> {
				List<Formatting> formatters =
					delAll( codes, "(?i)(&[0-9a-fk-or]){1,}(?=&\\1)", "&" )
					.chars().mapToObj(intCode -> Formatting.byCode( (char)intCode ))
					.toList()
				;

				return new ArrayList<>(
					formatters.contains(Formatting.RESET) // gets every code after the last reset (if any reset codes are present)
						? formatters.subList( formatters.lastIndexOf(Formatting.RESET), formatters.size() ) //? includes reset code, deleting it will ruin it
						: formatters
				);
			})
			.toList()
		);
		

		if( texts.size() > 0 && fCodes.size() > 0 ) {
			for(int index = 0; index < texts.size();) {
				Style base = (index > 0) ? output.getSiblings().get(index-1).getStyle() : Style.EMPTY;
				List<Formatting> style = fCodes.get(index);

				if( style.get(0).equals(Formatting.RESET) ) // if first code is reset, reset the base style
					base = Style.EMPTY.withFormatting( style.remove(0) ); // consolidates remove and set calls

				if( style.size() > 1 && style.get(style.size() - 1).equals(Formatting.RESET) )
					style = List.of(Formatting.RESET); // if last code is reset with codes before it, reset the current style


				output.append(
					Text.literal( texts.get(index++) ) .setStyle(
						style.size() > 0 ? base.withFormatting( style.toArray(new Formatting[0]) ) : base
					)
				);
			}

			return output;
		}

		return Text.literal(plain);
	}
}