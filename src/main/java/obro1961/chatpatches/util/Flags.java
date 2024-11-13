package obro1961.chatpatches.util;

/**
 * These are used to fix bugs, especially with messages modifying where otherwise unwanted.
 * But generally, they are used whenever a boolean flag is needed to control a feature
 * and scope limitations prevent an easier, more direct solution.
 */
public enum Flags { //todo replace w a bitset..?
	// also todo delete this class theres no way we still need this
	INIT(0b1),
	LOADING_CHATLOG(0b10),
	BOUNDARY_LINE(0b100);

	Flags(int value) {
		this.value = value;
	}

	public static int flags = INIT.value;
	public final int value;


	/** Set or remove this flag according to {@code set} in {@link #flags}. */
	public void set(boolean set) {
		if(set)
			raise();
		else
			lower();
	}

	/** Set this flag to {@code true} in {@link #flags}. */
	public void raise() {
		flags |= value;
	}

	/** Performs an XOR (toggle) operation of this flag on {@link #flags}. */
	public void toggle() {
		flags ^= value;
	}

	/** Removes this flag's bits in {@link #flags}. */
	public void lower() {
		if(isRaised())
			toggle();
	}

	/** Returns true if this flag has its bit(s) set in {@link #flags}. */
	public boolean isRaised() {
		return (flags & value) == value;
	}
}