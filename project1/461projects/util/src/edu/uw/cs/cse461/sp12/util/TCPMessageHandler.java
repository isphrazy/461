package edu.uw.cs.cse461.sp12.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sends/recieves a message over an established TCP connection.
 * To be a message means the unit of write/read is demarcated in some way.
 * In this implementation, that's done by prefixing the data with a 4-byte
 * length field.
 * <p>
 * Note used in Project 1.
 *  
 * @author zahorjan
 *
 */
public class TCPMessageHandler {
	protected Socket mSock;
	protected InputStream mIS;
	protected OutputStream mOS;
	
	//--------------------------------------------------------------------------------------
	// helper routines
	//--------------------------------------------------------------------------------------

	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method encodes into that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param i
	 * @return A byte[4] encoding the integer argument.
	 */
	protected static byte[] intToByte(int i) {
		/*****************************************************************
		 * Project 2 will replace this dummy implementation with a real one
		 ******************************************************************/
		byte buf[] = new byte[4];
		return buf;
	}
	
	/**
	 * We need an "on the wire" format for a binary integer.
	 * This method decodes from that format, which is little endian
	 * (low order bits of int are in element [0] of byte array, etc.).
	 * @param buf
	 * @return 
	 */
	protected static int byteToInt(byte buf[]) {
		ByteBuffer b = ByteBuffer.wrap(buf);
		b.order(ByteOrder.LITTLE_ENDIAN);
		int result = b.getInt();
		return result;
	}

	/**
	 * Constructor, associating this TCPMessageHandler with a connected socket.
	 * @param sock
	 * @throws IOException
	 */
	public TCPMessageHandler(Socket sock) throws IOException {
		if ( sock == null) throw new RuntimeException("TCPMessageHandler constructor: socket argument is null");
		if ( !sock.isConnected() ) throw new RuntimeException("TCPMessageHandler constructor: socket argument isn't in connected state");
		mSock = sock;
		mIS = sock.getInputStream();
		mOS = sock.getOutputStream();
	}
	
	//--------------------------------------------------------------------------------------
	// send routines
	//--------------------------------------------------------------------------------------

	/*****************************************************************
	 * Project 2 will implement these methods
	 ******************************************************************/
	public void sendMessage(byte[] buf) throws IOException {
	}
	
	public void sendMessage(String str) throws IOException {
	}
	
	public void sendMesssage(JSONArray jsArray) throws IOException {
	}
	
	public void sendMessage(JSONObject jsObject) throws IOException {
	}
	
	//--------------------------------------------------------------------------------------
	// read routines
	//--------------------------------------------------------------------------------------
	
	public byte[] readMessageAsBytes() throws IOException {
		byte[] lengthBytes = readFromStream(4);  // 
		int length = byteToInt(lengthBytes);
		byte[] buf = readFromStream(length);
		return buf;
	}
	
	public String readMessageAsString() throws IOException {
		byte[] buf = readMessageAsBytes();
		return new String(buf);
	}
	
	public JSONArray readMessageAsJSONArray() throws IOException, JSONException {
		String jsonStr = readMessageAsString();
		return new JSONArray(jsonStr);
	}
	
	public JSONObject readMessageAsJSONObject() throws IOException, JSONException {
		return new JSONObject( readMessageAsString() );
	}
	
	/**
	 * Helper routine that reads a particular number of bytes from connection.
	 * @param nBytes Number of bytes to read.  Must be greater than zero.
	 * @return Throws EOFException if EOF reached; throws an IOException if less than nBytes read or nBytes is zero. Otherwise returns
	 * a <code>byte[nBytes]</code> array of received data.
	 * @throws IOException
	 */
	protected byte[] readFromStream(int nBytes) throws IOException {
		// debug ----------
		if ( nBytes == 0 ) {
			System.out.println("TCPMessageHandler.readFromStream: length 0 read requested"); // place to set breakpoint
			throw new IOException("TCPMessageHandler.readFromStream: read of zero bytes requested");
		}
		// debug ----------
		byte[] buf = new byte[nBytes];
		int totalRead = 0;
		while ( totalRead < nBytes) {
			int nRead = mIS.read(buf, totalRead, nBytes-totalRead);
			if ( nRead < 0 ) {
				if ( totalRead == 0 ) throw new EOFException("EOF reached");
				throw new IOException("TCPMessageHandler.readFromStream: EOF reached after " + totalRead + " bytes, but " + nBytes + " requested");
			}
			// debug ----------
			if ( nRead == 0 ) {
				System.out.println("TCPMessageHandler.readFromStream: read returned 0"); // place to set breakpoint -- under what conditions does this happen?
				throw new IOException("Read zero bytes!");  // don't go into infinite loop!
			}
			// debug ----------
			totalRead += nRead;
		}
		return buf;
	}
		

}
