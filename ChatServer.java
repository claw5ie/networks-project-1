import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

  // Decoder for incoming text -- assume UTF-8
  static private final CharsetDecoder decoder =
    Charset.forName("UTF8").newDecoder();

  static Hashtable<SocketChannel, UserInfo> users =
    new Hashtable<SocketChannel, UserInfo>();

  static public void main(String args[]) throws Exception {
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel socket_channel = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      socket_channel.configureBlocking(false);

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket server = socket_channel.socket();
      int port = Integer.parseInt(args[0]);
      
      server.bind(new InetSocketAddress(port));
      
      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      socket_channel.register(selector, SelectionKey.OP_ACCEPT);
      System.out.println("Listening on port " + port);

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        // If we don't have any activity, loop around and wait again
        if (selector.select() == 0)
          continue;

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if (key.isAcceptable()) {
            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket socket = server.accept();
            System.out.println("Got connection from " + socket);

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel channel = socket.getChannel();
            channel.configureBlocking(false);

            // Register it with the selector, for reading
            channel.register(selector, SelectionKey.OP_READ);

            if (!users.contains(channel))
              users.put(channel, new UserInfo());

          } else if (key.isReadable()) {
            SocketChannel sc = null;

            try {
              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              String message = processInput(sc);

              if (message != null) {
                UserInfo user = users.get(sc);
                
                if (message.charAt(0) == '/') {
                  process_command(message.split(" ", 0), user);
                  System.out.println("Server: " + message);
                } else {
                  System.out.println(
                    (user == null ? "Anon: " : user.name) + ": " + message
                    );
                }
              }
              else {
                // If the connection is dead, remove it from the selector
                // and close it
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println("Closing connection to "+s);
                  s.close();
                  users.remove(sc);
                } catch(IOException ie) {
                  System.err.println("Error closing socket "+s+": "+ie);
                }
              }
            } catch(IOException ie) {
              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch(IOException ie2) { System.out.println(ie2); }

              System.out.println("Closed "+sc);
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch(IOException ie) {
      System.err.println(ie);
    }
  }

  static boolean name_exist() {
    return false;
  }

  static boolean sala_exist() {
    return true;
  }

  static void process_command(String[] words, UserInfo user) {
    String state = "state";

    if (words[0].equals("/nick")) {
      if(words.length == 2 && !name_exist()) {
        System.out.println("name set to: " + words[1]);
        user.name = words[1];
      } else {
        System.out.print("ERROR");
      }
    } else if(words[0].equals("/join")) {
      if(words.length != 2 || state == "init") {
        System.out.print("ERROR");
      }
      else if(sala_exist()) {
        if(state =="outside") {
          //juntar a sala_exist
          System.out.print("OK"); //para quem usa o comando
          System.out.print("JOINED" + user.name); //para quem ja esta na sala
        } else {
          //sair da sala atual
          System.out.print("OK"); //para quem usa o comando
          System.out.print("LEFT" + user.name); //para quem esta na sala antiga
          System.out.print("JOINED" + user.name); //para quem ja esta na sala nova
        }
      }
      else {
        //criar sala
        System.out.print("OK");
      }
    }
    else if(words[0].equals("/leave")) {
      if(state == "inside") {
        //sair da sala
        System.out.print("OK");// para mim
        System.out.print("LEFT" + user.name);
      }
      else {
        System.out.print("ERROR");
      }
    }
    else if(words[0].equals("/bye")) {
      System.out.print("BYE");//mim
      if(state == "inside") {
        System.out.print("LEFT" + user.name);
      }
    }
  }

  // Just read the message from the socket and send it to stdout
  static private String processInput(
    SocketChannel socket_channel
    ) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    socket_channel.read(buffer);
    buffer.flip();

    return buffer.limit() > 0 ? decoder.decode(buffer).toString() : null;
  }
}
