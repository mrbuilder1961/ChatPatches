package obro1961.chatpatches;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.DisplayNameGenerator;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @see #generateDisplayNameForMethod(List, Class, Method)
 */
@TestOnly
public class RemoveTestPrefixGenerator implements DisplayNameGenerator {
	private static final Standard STD_INSTANCE = new Standard();

	@NotNull
	@Override
	public String generateDisplayNameForClass(@NotNull Class<?> testClass) {
		return STD_INSTANCE.generateDisplayNameForClass(testClass);
	}

	@NotNull
	@Override
	public String generateDisplayNameForNestedClass(@NotNull List<Class<?>> enclosingInstanceTypes, @NotNull Class<?> nestedClass) {
		return STD_INSTANCE.generateDisplayNameForNestedClass(enclosingInstanceTypes, nestedClass);
	}

	/**
	 * @return The same string as if called by {@link DisplayNameGenerator.Standard},
	 * except when the method follows the pattern {@code testMethod()} - in which case, the
	 * leading string will be removed to return {@code method()} instead. This allows test
	 * names to not appear redundant and allows actual methods to statically import their
	 * respective methods properly.
	 */
	@NotNull
	@Override
	public String generateDisplayNameForMethod(@NotNull List<Class<?>> enclosingInstanceTypes, @NotNull Class<?> testClass, @NotNull Method testMethod) {
		String name = STD_INSTANCE.generateDisplayNameForMethod(enclosingInstanceTypes, testClass, testMethod);

		if(name.startsWith("test") && name.length() > 4) {
			name = name.substring(4);

			char first = name.charAt(0);
			if(Character.isUpperCase(first)) {
				name = Character.toLowerCase(first) + name.substring(1);
			}
		}

		return name;
	}
}