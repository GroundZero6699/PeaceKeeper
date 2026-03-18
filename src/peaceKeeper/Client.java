package peaceKeeper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author christoffer wiik
 * @version 1.0
 * @since 2026-03-12
 * 
 * Represents a client.
 * Connects to server.
 * */
public class Client {
	
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	
	/**
	 * Creates a new client object and opens input and output streams
	 * to a specific host and port. 
	 * @throw exception on error.
	 * */
	public Client(String host, int port) throws Exception {
		socket = new Socket(host, port);
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}
	
	/**
	 * Send a XML message to server.
	 * */
	public void send(String xml) {
		out.println(xml);
	}
	
	/**
	 * Receives XML answer from server.
	 * @throw exception on error.
	 * */
	public String receive() throws Exception {
		return in.readLine();
	}
	
	/**
	 * Closes connection with server.
	 * @throw exception on error.
	 * */
	public void close() throws Exception {
		try { out.close(); } catch(Exception ignored) {}
		try { in.close(); } catch(Exception ignored) {}
		socket.close();
	}
}
