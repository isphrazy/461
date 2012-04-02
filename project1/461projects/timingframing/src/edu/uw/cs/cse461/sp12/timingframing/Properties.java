package edu.uw.cs.cse461.sp12.timingframing;

/**
 * Default configuration values, shared across projects.
 * @author zahorjan
 *
 */
public class Properties {
	/**
	 * Default host name or IP address of Server.
	 */
	public static final String SERVER_HOST = "cse461.cs.washington.edu";

	/**
	 * Server port used when inter-symbol time is negotiated (i.e., set by client)
	 */
	public static final int SERVER_PORT_NEGOTIATE = 46100;  // inter-symbol time is negotiated by a handshake
	/**
	 * The default inter-symbol time when rate is negotiated.
	 */
	public static final int SERVER_INTER_SYMBOL_TIME = 10; // msec.

	/**
	 *  The ports associated with specific, pre-set, inter-symbol times are ports 46101, 46102, etc.
	 *  This array gives the inter-symbol times associated with those ports (as well as determining
	 *  how many of them will exist).  Consecutive values in the array correspond to ports 46101, 46102, etc.
	 */
	public static int[] SERVER_PORT_INTERSYMBOL_TIME_VEC = {1, 2, 4, 8, 16, 32, 64, 128, 256};

	/**
	 * Default number of trials to run (used by ConsoleClient).
	 */
	public static final int CLIENT_NTRIALS = 40;  
	
}
