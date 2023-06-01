Programmer: Farha Zindah, COSC 439/522, W '23

Description: 
Java chat app that allows clients to communicate with each other 
by sending messages to a server. At the end of each client session, 
the server returns the client the number of messages they sent and the time spent in connection.

Compile/run:
1. To run the app, you first compile and run the server 
Ex: javac fzi_TCPServerMT.java
The server takes optional command-line arguments (the port number, g, and n for the diffie-hellman algorithm). 
If no argument is given, the default port number is 22950. 
Add 'nohup' to ensure the server doesn't hang up, and '&' to run it in the background.

> java fzi_TCPServer -p 22950 -n 1823 -g 1019
> java fzi_TCPServer

ex: 
_________________________________________________________________
>java fzi_TCPServer
Opening port...

*** farha has joined the chat room (hosted by DESKTOP-5SDR0QA) ***
farha: hi!

2. Next, compile and run the client program using three optional command line args: username (-u), port number (-p), and host address (-h). If the username isn't given, you will be prompted for one. The default host and port are localhost and 22950.
Ex: javac fzi_TCPClientMT.java

> java fzi_TCPClient –u tom –p 22222 –h emunix.emich.edu 
> java fzi_TCPClient –h 224.21.198.12  
> java fzi_TCPClient –p 22222 –u tom 
> java fzi_TCPClient 

ex:
__________________________________________________________________
>java fzi_TCPClient -u farha
Enter message:
hi!

************************************
*** Final report from the server ***

farha sent 1 messages to the server
Session duration: 00:04:21:769

!!!!! Closing connection... !!!!!

3. To end a client session, type DONE. This will not be counted as a message.
4. To end the server, find your process id (ps –j | more) and type kill -9 PID.

Conclusion: 
- I spent around 15 hours on this project
- I had trouble getting special characters to print correctly to the 
server broadcast to clients
  
- program has some errors when it comes to special characters:
ex: QAZWSXEDCRFVTGBYHNUJMIKOLPqazwsxedcrfvtgbyhnujmikolp`1234567890-=][\';,./?><":|}{+_)(*&^%$#@!~
prints on multiple lines