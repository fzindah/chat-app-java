/* Programmer: Farha Zindah, COSC 439/522, W '23
** File name: "fzi_TCPServerMT.java"
** Server program for a chat app that allows clients to communicate with each other 
** by sending encrypted messages to a server, decrypting incoming messages, 
* and sending DONE to get final report.
** 
** When you run this program, you may give g, n, or the service port
** number as a command line argument. 
*For example, java TCPServer -p 22950 -n 1019 -g 1823
*/

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class fzi_TCPServerMT {
	private static ServerSocket servSock;
	private static int port = 22950;
	private static int g = 1019;
	private static int n = 1823;
	
	// Synchronized list of client threads
	private static List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());

	/*
	 * Call run method to start Server
	 */
	public static void main(String[] args) {
		
		System.out.println("Opening port...\n");
		
		try {
			
			checkCommandArgs(args);
			// Create a server object
			servSock = new ServerSocket(port);
		}
		catch (IOException e) {
			System.out.println("Unable to attach to port!");
			System.exit(1);
		}
		catch (NumberFormatException e) {
			System.out.println("Please enter a valid port number");
			System.exit(1);
		} catch (Exception e) {}
		// run program
		do {
			run();
		} while (true);

	}

	/**
	 * Run the server program
	 */
	public static void run() {
		// create the chat file before the first client joins
		final File chat = new File("fz_chat.txt");
		Socket link = null;
		
		try {
			// Put the server into a waiting state
			link = servSock.accept();
			
			// Create a thread to handle this connection
			ClientHandler handler = new ClientHandler(link, chat, clients, g, n);

			// add thread to list
			clients.add(handler);
			
			// start serving this connection
			handler.start();
		}

		catch (IOException e) {}
		catch (NullPointerException e) {
			System.out.println("Socket has not been successfully created");
			System.exit(1);
		}
	}
	
	public static void checkCommandArgs(String[] args) throws Exception {
		
		String error;
		
		// check and resolve command line arguments
		for (int i = 0; i < args.length; i += 2) {
			
			switch (args[i].toLowerCase()) {
			case "-p":
				error = (args.length <= i + 1) ? "Error: enter input for port\n" : "";
				System.out.print(error);
				port = Integer.parseInt(args[i + 1]);
				break;
			case "-g":
				error = (args.length <= i + 1) ? "Error: enter value for g\n" : "";
				System.out.print(error);
				g = Integer.parseInt(args[i + 1]);
				break;
			case "-n":
				error = (args.length <= i + 1) ? "Error: enter value for n\n" : "";
				System.out.print(error);
				n = Integer.parseInt(args[i + 1]);
				break;
			default: 
				throw new Exception();
			}
		}
	}
}


/**
 * Class to handle Client threads
 */
class ClientHandler extends Thread {
	// resources
	private Socket client;
	private List<ClientHandler> clientList;
	private BufferedReader in;
	private PrintWriter out;
	private File chat;
	private Instant sessionStart;
	private int g, n, x, secretKey;
	private byte pad;
	
	public ClientHandler(Socket s, File chat, List<ClientHandler> clients, int g, int n) {
		//set up the socket
		client = s;
		clientList = clients;
		try {
			//Set up input and output streams for socket
			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			out = new PrintWriter(client.getOutputStream(), true);
			this.chat = chat;
			sessionStart = Instant.now(); // get current time
			this.g = g;
			this.n = n;
		} 
		catch (IOException e) {
			e.getMessage();
		}
	}

	/*
	 * Method is called automatically when a client thread starts.
	 */
	public void run() {

		try {
			// initiate handshake with client
			initHandshake();
			// set key using client function
			setKey(in.readLine());
			
			// pad pad with 
			System.out.println("n: " + n + "  g: " + g + "  key: " + secretKey
					+ "  pad: " + String.format("%8s", Integer.toBinaryString(pad)).replace(' ', '0'));
			
			String username = decrypt(in.readLine());

			// if it's the first client, make sure the file is empty
			if (clientList.size() == 1)
				emptyChat();
			else 
				writeChatToClient(out, chat); // print earlier messages
			
			// broadcast messages to server, clients, chat file
			int numMessages = broadcastUserInput(in, username);
			
			//Send a report back
			out.println(encrypt("\n************************************\n"
					+ "*** Final report from the server ***\n"
					+ "\n" + username + " sent " + numMessages + " messages to the server", pad));
			
			// print out time elapsed
			out.println(encrypt(sessionDuration(sessionStart), pad));
			
			// remove from client list
			removeClient(this, username);
		} 
		catch (IOException e) {
			e.getMessage();
		} 
		finally {
			try {
				// close client connection
				out.println(encrypt("!!!!! Closing connection... !!!!!", pad));
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Unable to disconnect!");
				System.exit(1);
			}
		}
	}
	
	/** 
	 * method to read client input and write to the chat file
	 * @param in
	 * @param chat
	 * @throws IOException
	 */
	private int broadcastUserInput(BufferedReader in, String username) throws IOException {
		
		// User data
		int numMessages = 0;
		boolean welcomeSent = false;
		String message = "*** " + username + " has joined the chat room"
				+ " (hosted by " + InetAddress.getLocalHost().getHostName() + ") ***";
		
		// get input until client enters "DONE"
		while (!message.equals("DONE")) {
			
			if (welcomeSent)
				message = username + ": " + message;
			else {
				welcomeSent = true;
			}
			
			// broadcast message
			try {
				broadcast(message);
			} 
			catch (Exception e) {
				System.out.print("Error" + e.getMessage());
			}
			message = decrypt(in.readLine());
			// count messages sent
			numMessages++;
		}
		
		broadcast("*** " + username + " has left the chat room ***\n");
		
		return numMessages - 1;
	}
	
	/**
	 * Method to write contents of the text file to the client
	 * @param out
	 * @param chat
	 * @throws IOException
	 */
	synchronized void writeChatToClient(PrintWriter out, File chat) throws IOException {
		
		// Read each line from the file and write to client
		synchronized (chat) {
			// Create file reader
			BufferedReader chatReader = new BufferedReader(new FileReader(chat));
			String line;
			
			out.println(encrypt("\n*** Chat log ***\n****************", pad));
			               
			while((line = chatReader.readLine()) != null) {
				out.println(encrypt(line, pad));
			}
		chatReader.close();
		}
	}
    
    /**
     * Method broadcasts message to clients, server, and chat file
     * @param message
     * @throws IOException
     */
    private void broadcast(String message) throws IOException {
    	
    	// Print to server
    	System.out.println(message);
    	
    	// Synchronized block to access chat file
    	synchronized (chat) {
    		FileWriter writer = new FileWriter(chat, true);
	        // write message to chat file
			writer.write(message + "\n");
			writer.flush(); 
			writer.close();
    	}
    	
        // Synchronized block to access client list
        synchronized (clientList) {
            // Using iterators to iterate over elements
            Iterator<ClientHandler> clients = clientList.iterator();
 
            // Broadcast message to each client
            while (clients.hasNext()) {
            	ClientHandler client = clients.next();

                if(!client.equals(this)){
                	client.getWriter().println(encrypt("\b".repeat(15) + message + 
                			"       ", client.getPad()));
                }
            }
        }
    }
    
    /**
     * Remove client and delete chat if necessary
     * @param client
     * @param username
     */
    private void removeClient(ClientHandler client, String username) {
    	// synchronized access to client list
    	synchronized (clientList) {
    		clientList.remove(this);
    		if (clientList.isEmpty()) {
    			chat.delete();
    		}
    	}
    }
    
    /**
     * Empty chat file 
     * (if previous users didn't terminate using DONE)
     */
    private void emptyChat() {
    	FileWriter writer = null;
    	synchronized (chat) {
	        // write message to chat file
			try {
				writer = new FileWriter(chat, false);
				writer.write("");
				writer.flush(); 
				writer.close();
			} catch (IOException e) {}
    	}
    }
    
	/**
	 * Method returns session duration in HH::MM:SS::ms
	 * @param sStart
	 * @return
	 */
	private String sessionDuration(Instant sStart) {
		// get duration between start and end instance
		long sDuration = Duration.between(sStart, Instant.now()).toMillis();

		// convert to appropriate units
		long HH = TimeUnit.MILLISECONDS.toHours(sDuration);
		long MM = TimeUnit.MILLISECONDS.toMinutes(sDuration) % 60;
		long SS = TimeUnit.MILLISECONDS.toSeconds(sDuration) % 60;
		
		// return formatted string
		String session = String.format("Session duration: %02d:%02d:%02d:%03d", HH, MM, SS, sDuration % 1000);

		return session;
	}
	
	/** Compute function based on g^x % n
	 */
	private int computedFunction() {
		x = (int) Math.floor(Math.random() * (201 - 100 + 1) + 100); 
		int fVal = computeExpMod(g,x,n);
		return fVal;
	}
	
	/**
	 * Set key and pad values
	 * @throws UnsupportedEncodingException 
	 */
	private void setKey(String f) throws UnsupportedEncodingException {
		secretKey = computeExpMod(Integer.parseInt(f), x, n);
		// get binary rightmost numbers
		byte[] a = ("" + secretKey).getBytes("UTF-8");
		pad = a[0];
	}
	
	/**
	 * Compute and return modulus for large exponents
	 * Reference:
	 * https://www.geeksforgeeks.org/modular-exponentiation-power-in-modular-arithmetic/
	 */
    static int computeExpMod(int g, int y, int n)
    {
      int res = 1; // Init result
   
      g = g % n; // Check if g >= n
   
      while (y > 0)
      {
        // If y is odd, multiply g with result
        if ((y & 1) != 0)
          res = (res * g) % n;
   
        y = y >> 1; // y = y/2
        g = (g * g) % n;
      }
      return res;
    }

	/**
	 * Return writer
	 * @return
	 */
    private PrintWriter getWriter(){
        return out;
    }
    
    private byte getPad() {
    	return pad;
    }
    /**
     * Use XOr of key to encrypt data
     * Cipher: https://www.geeksforgeeks.org/xor-cipher/
     * @throws UnsupportedEncodingException 
     */
    private String encrypt(String data, byte pad) throws UnsupportedEncodingException {
        // encrypted string
    	byte[] d = data.getBytes("UTF8");
    	
        for(int i=0; i < d.length; i++)
            d[i] = (byte) ((byte) (d[i]^pad) & 0xFF);
        
        return new String(d, StandardCharsets.UTF_8);
    }
    
    /**
     * Use XOr of key to encrypt data
     * Cipher: https://www.geeksforgeeks.org/xor-cipher/
     * @throws UnsupportedEncodingException 
     */
    private String decrypt(String data) throws UnsupportedEncodingException {
        // encrypted string
        return encrypt(data, pad);
    }
    
    private void initHandshake() {
		out.println(g);
		out.println(n);
		out.println(computedFunction());
    }
}