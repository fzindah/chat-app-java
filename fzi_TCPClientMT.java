/* Programmer: Farha Zindah, COSC 439/522, W '23
** File name: "fzi_TCPClientMT.java"
** Client program for a chat app that allows clients to communicate with each other 
** by sending encrypted messages to a server, and sending DONE to get final report.
** 
** When you run this program, you may give the username, host name or
** the service port number as command line arguments. 
** For example,
** java fzi_TCPClient -u farha -h localhost -p 22222
*/


import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class fzi_TCPClientMT {
	private static InetAddress host; // default localhost
	private static int port = 22950; // default port number
	private static String username;
	private static int g, n, y;
	private static int serverFunction;
	private static int secretKey;
	private static byte pad;

	public static void main(String[] args) {
		
		try {
			// check command line arguments
			checkCommandArgs(args);
		} 
		catch (UnknownHostException e) {
			System.out.println("Error: Invalid host ID!");
			System.exit(1);
		}
		catch (Exception e) {
			System.out.println("Enter an input for each option chosen: -p [port] -h [host] -u [username]");
		}
		
		// run the program
		run(port);
	}

	/**
	 * Run the Client program
	 * @param port number
	 */
	private static void run(int port) {
		Socket link = null;
		try {
			// Establish a connection to the server
			link = new Socket(host, port);

			// Set up input and output streams for the connection
			BufferedReader in = new BufferedReader(new InputStreamReader(link.getInputStream()));
			PrintWriter out = new PrintWriter(link.getOutputStream(), true);

			// engage in handshake
			handshake(in, out);
			
		    // create a sender thread to read and send typed messages to the server
		    Sender sender = new Sender(out, username, pad);
		    
		    // start the sender thread
		    sender.start();  
		    
		    // receive content of chat file
		    receiveResponse(in);

		} 
		catch (IOException e) {
			System.out.println("Host server is not running");
			System.exit(1);
		}
		catch (NullPointerException e) {System.exit(1);}
		catch (Exception e) {}

		finally {
			try {
				System.out.println("\n!!!!! Closing connection... !!!!!");
				link.close();
			}
			catch (Exception e) {
				System.exit(1);
			}
		}

	}

	/**
	 * Method checks whether command line arguments are valid 
	 * and sets variables accordingly.
	 * @param args
	 * @throws UnknownHostException, Exception 
	 */
	public static void checkCommandArgs(String[] args) throws UnknownHostException, Exception {
		String error;
		
		// check and resolve command line arguments
		for (int i = 0; i < args.length; i += 2) {
			
			switch (args[i].toLowerCase()) {
			case "-p":
				error = (args.length <= i + 1) ? "Error: enter input for port\n" : "";
				System.out.print(error);
				port = Integer.parseInt(args[i + 1]);
				break;
			case "-u":
				error = (args.length <= i + 1) ? "Error: enter username\n" : "";
				System.out.print(error);
				username = args[i + 1];
				break;
			case "-h":
				error = (args.length <= i + 1) ? "Error: enter host\n" : "";
				System.out.print(error);
				host = InetAddress.getByName(args[i + 1]);
				break;
			default: 
				throw new Exception();
			}
		}

		// if host wasn't input, set to default
		if (host == null)
			host = InetAddress.getLocalHost();
	}
	
	public static void receiveResponse(BufferedReader in) throws IOException {
	    // the main thread reads messages sent by the server, and displays them on the screen
        String response;

        // Get data from the server and display it on the screen
        while (!(response = decrypt(in.readLine())).equals("DONE")) {
        	System.out.println(response);          
        }

	}
	
	/**
	 * Compute modulus of large exponents
	 * Reference:
	 * https://www.geeksforgeeks.org/modular-exponentiation-power-in-modular-arithmetic/
	 * @param g, y, n
	 * @return
	 */
    public static int computeExpMod(int g, int y, int n)
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
	 * Establish and set shared key with server
	 * @param in, out
	 * @throws NumberFormatException, IOException
	 */
	private static  void handshake(BufferedReader in, PrintWriter out) 
			throws NumberFormatException, IOException {
		g = Integer.parseInt(in.readLine());
		n = Integer.parseInt(in.readLine());
		serverFunction = Integer.parseInt(in.readLine());
		
		// send g^y % n
		out.println(computedFunction());
		setKey();
		System.out.println("n: " + n + "\s\sg: " + g + "\s\skey: " + secretKey
				+ "\s\spad: " + String.format("%8s", Integer.toBinaryString(pad)).replace(' ', '0'));	
	}
	
    /**
     * Use XOr of key to encrypt data
     * Cipher: https://www.geeksforgeeks.org/xor-cipher/
     * @throws UnsupportedEncodingException 
     */
    private static String decrypt(String data) throws UnsupportedEncodingException {
        // encrypted string
    	byte[] d = data.getBytes("UTF8");
    	
        for(int i=0; i < d.length; i++)
            d[i] = (byte) ((byte) (d[i]^pad) & 0xFF);
        
        return new String(d, StandardCharsets.UTF_8);
    }
	
	
    /* set secret key and pad */
	private static void setKey() throws UnsupportedEncodingException {
		secretKey = computeExpMod(serverFunction, y, n);
		// get binary rightmost numbers
		byte[] a = ("" + secretKey).getBytes("UTF-8");
		pad = a[0];
	}
	
	
	/* compute g^y % n function */
	private static int computedFunction() {
		y = (int) Math.floor(Math.random() * (201 - 100 + 1) + 100);  
		return computeExpMod(g,y,n);
	}
}

/**
 * The sender class reads messages typed at the keyboard, 
 * and sends them to the server
 */
class Sender extends Thread {

	private PrintWriter out;
	private String username;
	private byte pad;
	
	// constructor
	public Sender(PrintWriter out, String username, byte pad) {
		this.out = out;
		this.username = username;
		this.pad = pad;
	}

	// overwrite the method 'run' 
	public void run() {
		// Set up stream for keyboard entry
		BufferedReader userEntry = new BufferedReader(new InputStreamReader(System.in));
		String message;
		
		
		try {
			// Get data from the user and send it to the server
			setUsername(userEntry);
			out.println(encrypt(username));
			
			// Pause to load previous chat before prompting for new message
			Thread.currentThread();
			Thread.sleep(100);
			
			do {
				System.out.print("Enter message: ");
				message = userEntry.readLine();
				out.println(encrypt(message));
			} while (!message.trim().equals("DONE"));
		} 
		catch (IOException | InterruptedException e) {
			System.out.println("Exception");
		}
	}
	
	/**
	 * Method checks if the user has entered a valid user name
	 * @param userEntry
	 * @throws IOException
	 */
	public void setUsername(BufferedReader userEntry) throws IOException {
		while (username == null || username.equals("")) {
			System.out.print("Enter username: ");
			username = userEntry.readLine();
		}
	}
	
    /**
     * Use XOr of key to encrypt data
     * Cipher: https://www.geeksforgeeks.org/xor-cipher/
     * @throws UnsupportedEncodingException 
     */
    private String encrypt(String data) throws UnsupportedEncodingException {
    	byte[] d = data.getBytes("UTF8");
    	
        for(int i=0; i < d.length; i++)
            d[i] = (byte) ((byte) (d[i]^pad) & 0xFF);
        
        return new String(d, StandardCharsets.UTF_8);
    }
}