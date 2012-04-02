package edu.uw.cs.cse461.sp12.timingframing;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

/**
 * A ConsoleClient provides a console (command line/text) UI for a Client.
 * It also orchestrates running multiple experiments against a Server,
 * and printing the results.
 * 
 * @author zahorjan
 *
 */
public class ConsoleClient implements Client.ClientListener {
	private Client mClient;
	
	/**
	 * Constructor that creates its own Client to listen to.
	 * @param mLastSyncChar
	 */
	public ConsoleClient() {
		mClient = new Client();
		init(mClient);
	}

	/**
	 * Constructor to attach to an existing Client.
	 * @param c
	 */
	public ConsoleClient(Client c) {
		init(c);
	}
	
	/**
	 * Attach this ConsoleClient to a Client.
	 * @param client
	 */
	private void init(Client client) {
		mClient = client;
		mClient.addListener(this);
		System.out.println("\nNew listener:");
	}
	
	/**
	 * Set to clean state, ready to connect again.
	 * @throws IOException
	 */
	public void reset() throws IOException {
		if ( mClient != null ) mClient.reset();
	}

	/**
	 * Getter for underlying Client object.
	 * @return
	 */
	public Client getClient() {
		return mClient;
	}
	
	/**
	 * Connect to server and start receiving characters.
	 * <br>
	 * Note: The thread calling this method doesn't return until the underlying Client object
	 * is done reading data.
	 * @param host The Server's host.
	 * @param port The Server's port.
	 * @param intersymbolTime  The desired time between consecutive symbols sent by the Server.  Ignored in Project 1.
	 */
	public void connect(String host, int port, int intersymbolTime) throws IOException {
		try {
			// if a predetermined rate port is specified, it's intersymbol time value overrides the command line arg
			int portOffset = port - Properties.SERVER_PORT_NEGOTIATE;
			if ( portOffset < 0 || portOffset > Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC.length )
				throw new IllegalArgumentException("Invalid port number specified");
			boolean negotiate = false;
			if ( portOffset == 0 ) negotiate = true;
			else intersymbolTime = Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC[portOffset-1];
			
			// the connect call loops until an error has been detected
			mClient.connect(host, port, negotiate, intersymbolTime);
		} finally {
			mClient.disconnect();
		}
	}
	
	/**
	 * Client.ClientListener interface method, called each time the underlying Client object
	 * reads a symbol (synchronously or asynchronously). 
	 * <p>
	 * The base implementation doesn't do anything.  If you want to see a trace
	 * of characters read, though, you can print them here.
	 */
	public void onChar(int type, char c) {
	}
	
	/**
	 * Print helpful information on how to invoke a ConsoleClient.
	 * @param options
	 */
	public static void help(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(ConsoleClient.class.getName(), options );
	}
	
	/**
	 * Start a set of experiments and print their results.
	 * @param args
	 */
	public static void main(String args[]) {
		// default values
		String serverHost = Properties.SERVER_HOST;
		// default server port is the fastest pre-determined rate.  (That fails quickly, so isn't so annoying to run.)
		int serverPort = Properties.SERVER_PORT_NEGOTIATE + 1;
		int interSymbolTime = Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC[0];   // default, in msec.
		int nTrials = Properties.CLIENT_NTRIALS;
		
		ConsoleClient cclient = new ConsoleClient();
		
		// This code deals with command line options
		Options options = new Options();
		options.addOption("s", "serverhost", true, "Host name or IP address of server (Default: " + serverHost + ")");
		options.addOption("p", "serverport", true, "Server port number (Default: " + serverPort + ")");
		options.addOption("i", "intersymboltime", true, "Time between successive symbols in msec. (Default: " + interSymbolTime + ")");
		options.addOption("t", "trials", true, "Number of trials to run (Default: " + nTrials + ")");
		options.addOption("h", "help", false, "Print this message");
		
		CommandLineParser parser = new PosixParser();
		try {
			CommandLine line = parser.parse(options, args);
			if ( line.hasOption("help") ) {
				help(options);
				return;
			}
			if ( line.hasOption("serverhost") ) serverHost = line.getOptionValue("serverhost");
			if ( line.hasOption("serverport") ) {
				serverPort = Integer.parseInt(line.getOptionValue("serverport"));
				interSymbolTime = cclient.getClient().portToIntersymbolTime(serverPort, interSymbolTime);
			}
			if ( line.hasOption("intersymboltime") ) {
				interSymbolTime = Integer.parseInt(line.getOptionValue("intersymboltime"));
				// if an interSymbolTime is specified, the port is forced to the negotating port
				serverPort = Properties.SERVER_PORT_NEGOTIATE;
			}
			if ( line.hasOption("trials") ) nTrials = Integer.parseInt(line.getOptionValue("trials"));
		} catch ( ParseException e ) {
			System.out.println("Command line parsing exception: " + e.getMessage());
			help(options);
			System.exit(-1);
		}
		
		System.out.println("\n------------------------------------------------------------------------");
		System.out.println("Server location: " + serverHost + ":" + serverPort);
		try {
			System.out.println("Client location: " + InetAddress.getLocalHost().getHostName() );
		} catch (UnknownHostException e) {
			System.out.println("Client location: <error determining host name>");
		}
		System.out.println("" + interSymbolTime + " msec. inter-symbol time");
		System.out.println("" +  nTrials + " trials");

		// Run the experiments
		int totalSyncChars = 0;
		int minChars = Integer.MAX_VALUE;
		int maxChars = 0;
		for ( int trial=0; trial<nTrials; trial++ ) {
			try {
				cclient.reset();
				cclient.connect(serverHost, serverPort, interSymbolTime);
				int syncSample = cclient.getClient().getNumMatchingChars();
				if ( syncSample < minChars ) minChars = syncSample;
				if ( syncSample > maxChars ) maxChars = syncSample;
				totalSyncChars += syncSample;
				System.out.printf("%5d chars read before error\n", syncSample);
			} catch (Exception e) {
				System.out.println("ConsoleClient.main: caught exception");
				e.printStackTrace();
			}
		}
		
		System.out.println("\n\tAverage good data length: " + totalSyncChars / (float)nTrials );
		System.out.println("\tMinimum good data length: " + minChars );
		System.out.println("\tMaximum good data length: " + maxChars );
		System.out.println("\n------------------------------------------------------------------------");
	}

}
