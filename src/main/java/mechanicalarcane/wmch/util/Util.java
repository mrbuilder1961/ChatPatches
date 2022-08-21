package mechanicalarcane.wmch.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;

import mechanicalarcane.wmch.config.Option;
import mechanicalarcane.wmch.mixins.ChatHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.MessageSender;
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
	public static final MessageSender NIL_SENDER = new MessageSender(NIL_UUID, Text.empty(), Text.empty());

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
		//private final int[] slot;

		private Flag(int value) { //,int[] slot
			this.value = value;
			//this.slot = slot;
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


	/** A shorthand method for accessing methods from {@code client}'s {@code net.minecraft.client.gui.hud.ChatHud} object. */
	public static ChatHudAccessor accessChatHud(MinecraftClient client) {
		return ((ChatHudAccessor) client.inGameHud.getChatHud());
	}

	/** If there's space to overwrite, runs {@code list.set(index, object)}. Otherwise does {@code list.add(index, object)}. */
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


	public static String delAll(String dirty, String toDel) {
		return dirty.replaceAll(toDel, "");
	}

	/** Removes all ampersand + formatting code sequences from {@code formatted}. */
	public static String strip(String formatted) {
		return delAll(formatted, "(&[0-9a-fA-Fk-orK-OR])+");
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
	 * to
	 * a text with all {@code &<?>} replaced with styled colors.
	 * If there are no formatting characters, returns the unstyled string.
	 * Doesn't support hex colors.
	 */
	public static MutableText formatString(String dirty) {
		MutableText out = Text.empty();
		Pattern finder = Pattern.compile("(?:&[0-9a-fA-Fk-orK-OR])+");
		Matcher results = finder.matcher(dirty);

		if(dirty.matches(".*" + finder.pattern() + ".*")) {
			// if there is text before a formatter then add it alone
			if(dirty.split(finder.pattern())[0].length() > 0) {
				String prfx = dirty.split(finder.pattern())[0];
				out.append(prfx);
				dirty = dirty.replace(prfx, "");
			}
			;

			List<String> texts = new ArrayList<>(Arrays.asList(dirty.split(finder.pattern())));
			texts.removeIf(s -> s.equals(""));
			int i = 0;

			while (results.find()) {
				Formatting[] style = new Formatting[results.group().length() / 2];
				char[] codes = delAll(results.group(), "&").toCharArray();
				for(int j = 0; j < codes.length; ++j)
					style[j] = Formatting.byCode(codes[j]);

				out.append(Text.literal(texts.get(i++)).formatted(style));
			}

			return out;
		} else
			return Text.literal(dirty);
	}

	public static boolean isBoundaryLine(String text) {
		return text.equals( strip(Option.BOUNDARY_STR.get()) ) || text == strip(Option.BOUNDARY_STR.get());
	}
}