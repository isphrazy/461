package edu.uw.cs.cse461.sp12.timingframing;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONStringer;

import edu.uw.cs.cse461.sp12.util.TCPMessageHandler;

/**
 * A Client connects to a Server.  Just which port it connects to implies
 * an inter-symbol time.  (The project extends this by negotiating the inter-symbol
 * time, and so can use only a single server port).  The Client then reads symbols,
 *  both synchronously and asynchronously.
 * <p>
 * Clients produce no output.  An addListener() method is provided that allows
 * other code to receive a callback when a symbol is read, either synchronously or asynchronously.
 * <p>
 * Clients create no new threads.  The connect() method enters a loop that doesn't exit until
 * either until EOF is reached on the data stream, or the synchronous and asynchronous receive
 * methods disagree about the stream contents.  Don't invoke connect() with a thread that you can't afford
 * to not hear from again for a long while.
 * 
 * @author zahorjan
 *
 */
public class Client {
	private Socket mClientSocket;
	private Timer mTimer;
	private ArrayList<ClientListener> mListeners;

	private char mLastSyncChar;  // last character read synchronously
	private char mLastAsyncChar; // last character read asynchronously
	private int mSyncLength;
	private int mAsyncLength;
	private int mMatchingChars;
        private boolean mStopped;
	
	private Lock mLock;
	
	// Event types
	public static final int TYPE_ASYNC = 0;
	public static final int TYPE_SYNC = 1;
		
	/**
	 * Interface for clients that want to receive notifications for every character received.
	 * <code>onChar</code> is invoked separated for each character received synchronously and asynchronously.
	 * <p>
	 * The <code>type</code> argument
	 * indicates which kind of notification is being delivered (<code>TYPE_SYNC</code> or
	 * <code>TYPE_ASYNC</code>). The <code>c</code> argument
	 * is the current character.
	 * @author zahorjan
	 *
	 */
	// listener interface
	public interface ClientListener {
		public void onChar(int type, char c);
	}

	/**
	 * Constructor takes the inter-read time for the asynchronous reader (in milliseconds), and number of 
	 * symbols between synchronization bit patterns.
	 * @param symbolGap
	 * @throws IOException
	 */
	public Client() {
		mListeners = new ArrayList<ClientListener>();
		mClientSocket = null;
		mLock = new ReentrantLock();
		reset();
	}
	
	/**
	 * Reset the Client to its initial state, ready to connect.
	 */
	public void reset() {
		disconnect();
		mSyncLength = 0;
		mAsyncLength = 0;
		mMatchingChars = 0;
                mStopped = false;
	}
	
	/**
	 * Listener will receive callback for each synchronously and each asynchronously 
	 * read character.
	 * @param listener
	 */
	public synchronized void addListener(ClientListener listener ) {
		mListeners.add(listener);
	}

	/**
	 * Invokes the onChar() method of any listeners that have been registered,
	 * then checks for disagreement between the sync and async streams, and terminates
	 * execution if there is one.
	 * @param type
	 */
	private boolean onChar(int type, char c) {

		mLock.lock();
		try {
			if ( type == TYPE_SYNC ) mSyncLength++;
			else if ( type == TYPE_ASYNC ) {
				mLastAsyncChar = c;
				mAsyncLength++;
			}
			else throw new RuntimeException("Unknown type in ConsoleClient.onChar: " + type);

			// first inform any listeners 
			for ( ClientListener listener : mListeners ) {
				listener.onChar(type, c);
			}
			// now check for disagreement
			if ( mAsyncLength == mSyncLength ) {
				if ( mLastAsyncChar == mLastSyncChar ) {
					mMatchingChars++;
					return true;
				}
			}
			else if ( mAsyncLength >= mSyncLength - 1 && mAsyncLength <= mSyncLength + 1 ) return true;

			// sync and async streams disagree 
			return false;
		} finally {
			mLock.unlock();
		}
	}
	
	/**
	 * Getter for number of characters read successfully (asynchronously) so far.
	 * @param options
	 */
	public int getNumMatchingChars() {
		return mMatchingChars;
	}
	
	/**
	 * Returns total number of characters read asynchronously so far.
	 * @return
	 */
	public int getAsyncLength() {
		return this.mAsyncLength;
	}
	/**
	 * Returns total number of characters read synchronously so far.
	 * @return
	 */
	public int getSyncLength() {
		return this.mSyncLength;
	}
	
	/**
	 * Returns <code>true</code> if the Client is current connected to a server, <code>false</code> otherwise.
	 * @return
	 */
	public boolean isConnected() {
		return mClientSocket != null;
	}
	
	/**
	 * Maps a port number to the port's implied inter-symbol time.
	 * @param port The port number.
	 * @param intersymbolTime The inter-symbol time to use if the port implies negotiated inter-symbol times.
	 * @return The inter-symbol time, in msec.
	 */
	public int portToIntersymbolTime(int port, int intersymbolTime) {
		int portOffset = port - Properties.SERVER_PORT_NEGOTIATE;
		if ( portOffset < 0 || portOffset > Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC.length )
			throw new IllegalArgumentException("Invalid port number specified: " + port);
		if ( portOffset == 0 ) return intersymbolTime;
		return Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC[portOffset-1];
	}
	
	/**
	 * Connect to the server socket.  Start asynchronous reads, using Timer events.
	 * Then go into a synchronous read loop.
	 * 
	 * @param serverHost  The host name or IP of the Server.
	 * @param serverPort  The Server port you want to connect to.
	 * @param negotiaterate  <code>true</code> if you want to negotiate the send rate with the Server.  <code>false</code> for Project 1.
	 * @param interSymbolTime If negotiating, the desired time between sends of consecutive symbols.  Otherwise, ignored.
	 * @throws IOException
	 */
	public void connect(String serverHost, int serverPort, boolean negotiaterate, int interSymbolTime) throws IOException {
		if ( isConnected() ) throw new RuntimeException("Client.connect: called when already connected");
		try {
			/*****************************************************************
			 * CSE461 Project 1
			 * 	  Starting from working code, I've replaced the right hand sides
			 *    of the following two statements with 'null.'  You should 
			 *    turn them back into working code.
			 *    
			 *    The first statement creates a socket and tries to connect to the
			 *    remote server.
			 *    The second statement extracts the InputStream from the socket,
			 *    which can then be used to read data sent by the server.
			 *    For both, you'll want to read the documentation on the Socket
			 *    class.  It's best to read the Android documentation (because
			 *    not all of the Java API may be available in Android 2.3.5):
			 *    http://developer.android.com/reference/packages.html
			 *    It's also the source for Android documentation, which we'll need
			 *    later in the course.
			 ******************************************************************/
			mClientSocket = null;
			InputStream is = null;
			

			if ( negotiaterate ) {
				TCPMessageHandler msgHandler = new TCPMessageHandler(mClientSocket);

				// Tell the server what inter symbol time this client wants.
				/*****************************************************************
				 * Project 2 will fill in code here
				 ******************************************************************/
			}

			mTimer = new Timer();

			// synchronous read loop
			boolean asyncStarted = false;
			int b = 0;
			while ( (b = is.read()) != -1 && !mStopped ) {
				mLastSyncChar = (char)b;
				// we can't start the async listener until the TCP handshake has completed
				if ( !asyncStarted ) {
					mTimer.scheduleAtFixedRate( new TimerTask() { public void run() { onChar(TYPE_ASYNC, mLastSyncChar); }	}, 
											    interSymbolTime/2,
											    interSymbolTime
											  );
					asyncStarted = true;
				}
				if ( !this.onChar(TYPE_SYNC, mLastSyncChar) ) break;
			}
		} finally {
			disconnect();
		}
	}

        /**
         * External interface to tell this Client to stop reading.
         */
         public void stop() {
             mStopped = true;
         }

	/**
	 * Close the connection to the server.
	 */
	public void disconnect() {
		if ( mTimer != null ) mTimer.cancel();
		mTimer = null;
		try {
			if ( mClientSocket != null ) mClientSocket.close();
		} catch (Exception e ) {};
		mClientSocket = null;
	}
}
