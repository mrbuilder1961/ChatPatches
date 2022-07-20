package obro1961.wmch.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.LiteralText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Random functions that are of some random, obsolete use
 */
public class Util {
	public static String delAll(String dirty, String toDel) {
		return dirty.replaceAll(toDel, "");
	}

	public static String asString(OrderedText old) {
		StringBuilder str = new StringBuilder();
		old.accept(new CharacterVisitor() {
			@Override
			public boolean accept(int ix, Style s, int cp) {
				str.append(Character.toString(cp));
				return true;
			}
		});
		return str.toString();
	}

	/**
	 * Turns a String formatted like {@code "&4Dark RED &l&33AAffCUSTOM BOLD BLUE"}
	 * to
	 * a text with all {@code &<?>} replaced with styled colors.
	 * If there are no formatting characters, returns the unstyled string.
	 * Doesn't support hex colors.
	 */
	public static Text getStrTextF(String dirty) {
		LiteralText out = new LiteralText("");
		Pattern finder = Pattern.compile("(?:&[0-9a-fA-Fk-orK-OR])+");
		Matcher results = finder.matcher(dirty);

		if (dirty.matches(".*" + finder.pattern() + ".*")) {
			// if there is text before a formatter then add it alone
			if (dirty.split(finder.pattern())[0].length() > 0) {
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
				for (int j = 0; j < codes.length; ++j)
					style[j] = Formatting.byCode(codes[j]);

				out.append(new LiteralText(texts.get(i++)).formatted(style));
			}

			return out;
		} else
			return Text.of(dirty);
	}
}