/**
 * Server portion for the timingframing assignment of CSE 461. 
 */
package edu.uw.cs.cse461.sp12.timingframing;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;

import edu.uw.cs.cse461.sp12.util.TCPMessageHandler;

/**
 * A server accepts connections from Clients, and then paces output to them.
 * The Server terminates only when the Client breaks the connection.
 * @author zahorjan
 *
 */
public class Server {
	/**
	 * The data sent by the Server is taken from a file.
	 */
	private static final String dataFilename = "timingframingData.txt";
	/**
	 * The data sent by the Server is cached in memory.
	 */
	private String dataString;

	/**
	 * We might want to be able to identify an individual sender (client connection), so we
	 * give them unique, sequential IDs.  <code>mSenderID</code> is the next ID to hand out.
	 */
	private int mSenderID;
	
	/**
	 *  A single Timer object is used to pace all senders
	 */
	private Timer timer;

	/**
	 * Keep track of the ServerInstances we create
	 */
	private int mNumServerInstances;
	/**
	 * Keep track of the ServerInstances we create
	 */
	private Runnable mServerInstance[];

	/**
	 * Constructs, but does not start, a Server.
	 * <p>
	 * The baseline server supports a fixed set of inter-symbol times, given by array
	 * <code>Properties.SERVER_INTERSYMBOL_TIME_VEC</code>.  Each inter-symbol time is associated
	 * with a server port to which clients can connect.
	 * A thread is associated with each server port, because the thread blocks waiting for connections.
	 * <p>
	 * To avoid the risk of running out of open file handles, we read the data file into
	 * a String when the server is created.  When clients connect, it is served from that String.
	 * 
	 * @throws IOException
	 */
	public Server() throws IOException {

		//-------------------------------------
		// Read entire data file into memory.
		//-------------------------------------

		File file = new File(dataFilename);
		int fLength = (int)file.length();
		if ( fLength == 0 ) throw new RuntimeException("Server.init: file " + dataFilename + " has length 0!");
		byte[] buf = new byte[fLength];
		FileInputStream fis = new FileInputStream(file);
		int nRead = fis.read(buf, 0, fLength);
		fis.close();
		if ( nRead != fLength) throw new RuntimeException("Server.init: Wanted to read " + fLength + " bytes from data file, but read " + nRead);
		dataString = new String(buf);
		System.out.println("Server initialized data: " + dataString.length() + " bytes");
		// safeguard, because bad things will happen if the data is length 0
		if ( dataString.length() == 0 ) throw new RuntimeException("Server.init: dataString has length 0!");
		
		//-------------------------------------
		// Initialize instance variables
		//-------------------------------------
		mSenderID = 0;
		timer = new Timer();

		//-------------------------------------
		// Create a socket and thread for each server port
		//-------------------------------------
		
		mNumServerInstances = Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC.length + 1; // the + 1 is for the negotiating socket, which is index 0
		mServerInstance = new Runnable[mNumServerInstances];
		mServerInstance[0] = new ServerInstance( Properties.SERVER_PORT_NEGOTIATE, true, 0 );
		for ( int p =1; p < mNumServerInstances ; p++ ) {
			mServerInstance[p] = new ServerInstance( Properties.SERVER_PORT_NEGOTIATE + p,
													 false,
													 Properties.SERVER_PORT_INTERSYMBOL_TIME_VEC[p-1]
					                               );
		}
	}
	
	/**
	 * Once constructed, a Server must be <code>start</code>ed to begin accepting client connections.
	 * @throws IOException
	 */
	public void start() throws IOException {
		for ( int p =0; p < mNumServerInstances; p++ ) {
			 new Thread(mServerInstance[p]).start();
		}
	}
	
	/**
	 * The mainline is used to start the server.  It ignores any arguments. It never terminates.
	 * @param args
	 */
	public static void main(String args[]) {
		try {
			Server server = new Server();
			server.start();
		} catch ( Exception e) {
			System.out.println("Caught exception in main.");
			e.printStackTrace();
		}
	}

	//-----------------------------------------------------------------------------------------------------
	// ServerInstance class
	/**
	 * A ServerInstance manages a single server socket (waiting for client connections).
	 * It can be either a predetermined rate or a negotiated rate socket.  (Project 1 supports
	 * only the former.)
	 * @author zahorjan
	 *
	 */
	public class ServerInstance implements Runnable {
		private int mPort;
		private boolean mNegotiateRate;
		private int mIntersymbolTime;
		
		protected ServerSocket mServerSocket;

		/**
		 * Constructor.
		 * @param port  Port number to be managed by this <code>ServerInstance</code>.
		 * @param negotiateRate <code>true</code> to indicate the client should negotiate a rate; <code>false</code> for fixed rate.
		 * @param intersymboltime Unless negotating the rate, the time between successive byte sends (in msec.).
		 * @throws IOException
		 */
		public ServerInstance(int port, boolean negotiateRate, int intersymboltime) throws IOException {
			mPort = port;
			mNegotiateRate = negotiateRate;
			mIntersymbolTime = intersymboltime;

			mServerSocket = new ServerSocket(mPort);
		}

		/**
		 * Puts thread into a loop accepting incoming connections.  Each connection
		 * will be sent a paced stream of data characters, after a brief handshake.
		 * Each connection is handled by a new thread, created by this method.
		 */
		public void run() {
			// Because a new thread executes this method for each ServerInstance object,
			// throwing exceptions is ineffective -- there's no enclosing try...catch 
			// waiting to field them.  So, catch them here.
			try {
				while (true) {
					Socket newSocket = mServerSocket.accept();
					ClientConnectionManager newSender = new Server.ClientConnectionManager(mSenderID++, newSocket,
																		mNegotiateRate, mIntersymbolTime, dataString);
					// A client has connected.  Create a thread to handle the connection to it.
					new Thread(newSender).start();
				}
			} catch (Exception e) {
				System.out.println("ServerInstance.run: caught exception");
				e.printStackTrace();
			}

		}
	}

	// ServerInstance class
	//-----------------------------------------------------------------------------------------------------


	//------------------------------------------------------------------------------
	// ClientConnectionManager class
	/**
	 * There is a <code>ClientConnectionManager</code> object per client connection, and a thread per 
	 * <code>ClientConnectionManager</code> (created by the <code>ServerInstance</code> object).
	 * It paces sending the symbols at that rate, as best it can.
	 * <p>
	 * The <code>ClientConnectionManager</code> terminates only when the client breaks the connection.
	 * @author zahorjan
	 *
	 */
	private class ClientConnectionManager implements Runnable {
		private Socket mSocket;
		private String mDataString;
		private int mID;
		private boolean mNegotiateRate;
		private int mIntersymbolTime;

		private Lock lock;
		private Condition sendtaskDone;

		/**
		 * Construct the object.
		 * @param id A UID for this instance.
		 * @param s  The socket (already) connected to the client.
		 * @param negotiateRate If <code>true</code>, client wants to set the send rate.  <code>false</code> for Project 1.
		 * @param intersymboltime If negotiating, the client's desired inter-symbol time.
		 * @param data The data to send (over and over, forever).
		 * @throws IOException
		 */
		public ClientConnectionManager(int id, Socket s, boolean negotiateRate, int intersymboltime, String data) throws IOException {
			mID = id;
			mSocket = s;
			mNegotiateRate = negotiateRate;
			mIntersymbolTime = intersymboltime;
			mDataString = data;
			
			mSocket.setTcpNoDelay(true);  
			lock = new ReentrantLock();
			sendtaskDone = lock.newCondition();
		}

		/**
		 * The user of this class should have created a Thread.  When thread <code>start()</code> is invoked, the thread runs here.
		 */
		public void run() {
			System.out.println("Sender[" + mID + "] starting");
			lock.lock();
			try {
				// If the client connected on the rate negotiation port, the first thing it should do is
				// send its desired inter-symbol time, as a JSONObject.
				if ( mNegotiateRate ) {
					TCPMessageHandler msgHandler = new TCPMessageHandler(mSocket);
					JSONObject jsObject = msgHandler.readMessageAsJSONObject();
					// if we don't receive a JSON object as the first communication, close the socket
					mIntersymbolTime = jsObject.getInt("intersymboltime");
				}
				System.out.println("Server.Sender.run[" + mID + "]: intersymboltime = " +
									mIntersymbolTime + (mNegotiateRate ? " (negotiated)" : ""));

				SendTask sendTask = new SendTask();
				timer.scheduleAtFixedRate( sendTask, 0, mIntersymbolTime);
				while ( !sendTask.isDone() ) sendtaskDone.await();
			} catch (Exception e) {
				System.out.println("Sender[" + mID + "]: caught exception: " + e.getMessage() );
				System.out.println("Terminating offending connection");
				e.printStackTrace();
			} finally {
				lock.unlock();
				try {
					mSocket.close();
				} catch (Exception e) {}
				System.out.println("Sender[" + mID + "] done");
			}
		}
		//ClientConnectionManager class
		//------------------------------------------------------------------------------
		

		//------------------------------------------------------------------------------
		// SendTask class
		/**
		 * Helper class used by <code>ClientConnection</code> for Timer-based pacing of outgoing characters.
		 * @author zahorjan
		 *
		 */
		private class SendTask extends TimerTask {
			private int mDataIndex;
			private OutputStream mOS;
			private boolean mIsDone;

			/**
			 * Create a task that can be run periodically by a Java <code>Timer</code> object.
			 * @param resyncInterval
			 * @throws IOException
			 */
			public SendTask() throws IOException {
				mDataIndex = 0;
				mOS = mSocket.getOutputStream();
				mIsDone = false;
			}

			/**
			 * Invoked each time the <code>Timer</code> fires this task.  Sends a single byte.
			 */
			public void run() {
				try {
					byte thisDataByte = (byte)mDataString.charAt(mDataIndex);
					mDataIndex++;
					if ( mDataIndex >= mDataString.length() ) mDataIndex = 0;
					mOS.write(thisDataByte);
					mOS.flush();
				} catch (Exception e) {
					// write fails if socket closes?
					cancel(); // stop this task
					lock.lock();
					mIsDone = true;
					sendtaskDone.signal();
					lock.unlock();
				}
			}
			
			/**
			 * Returns current status.
			 * @return <code>true</code> means client has disconnected; <code>false</code> it has not.
			 */
			public boolean isDone() {
				return mIsDone;
			}
		}
		// SendTask class
		//------------------------------------------------------------------------------

	}
}
