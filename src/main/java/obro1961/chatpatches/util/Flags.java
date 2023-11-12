package obro1961.chatpatches.util;

/** These are used to fix bugs, especially with messages modifying where otherwise unwanted. */
public enum Flags {
	INIT(0b0001),
	LOADING_CHATLOG(0b0010),
	BOUNDARY_LINE(0b0100);
	//SOME_FLAG_HERE(0b1000);

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
		if( isRaised())
			toggle();
	}

	/** Returns true if this flag has its bit(s) set in {@link #flags}. */
	public boolean isRaised() {
		return (flags & value) == value;
	}
}
