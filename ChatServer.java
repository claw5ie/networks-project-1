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

  static Hashtable<SocketChannel, UserInfo> users = new Hashtable<>();
  static TreeMap<String,  UserInfo> clients = new TreeMap<>();
  static TreeMap<String, TreeSet<UserInfo>> salas = new TreeMap<>();



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
        // Get a key representing one of bits of I/O activity
        for (SelectionKey key : keys) {
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
              users.put(channel, new UserInfo(channel));

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
              } else {
                // If the connection is dead, remove it from the selector
                // and close it
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println("Closing connection to " + s);
                  s.close();
                  users.remove(sc);
                } catch(IOException ie) {
                  System.err.println("Error closing socket " + s + ": " + ie);
                }
              }
            } catch(IOException ie) {
              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch(IOException ie2) { System.out.println(ie2); }

              System.out.println("Closed " + sc);
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


  static void process_command(String[] words, UserInfo user) {
    TreeSet<UserInfo> sala;
    //nick
    if (words[0].equals("/nick")) {
      if(words.length == 2) {
        String name = words[1];
        if(!clients.contains(name)){
          System.out.println("OK");
          if(user.state == 1){
            user.state = 2;//outside
            clients.put(name, user);
          }
          else if(user.state == 3){
            System.out.println("NEWNICK " + user.name + " " + name);
          }
          user.name = name;
        }
        else {
          System.out.print("ERROR");
        }
      }
      else {
        System.out.print("ERROR");
      }
    }

//join
    else if(words[0].equals("/join")) {
      if(words.length != 2 || user.state == 1) {
        System.out.print("ERROR");
      }
      else if(salas.contains(words[1])) {
        System.out.print("OK"); //para quem usa o comando
        if(user.state == 2) {
          System.out.print("JOINED" + user.name); //para quem ja esta na sala
          user.state = 3;//inside
        }
        else {
          System.out.print("LEFT" + user.name); //para quem esta na sala antiga
          //retirar user da sala na lista e se a sala ficou vazia apaga-la
          sala = salas.get(user.sala);
          if(sala.size() == 1){
            salas.remove(user.sala);
          }
          else{
            sala.remove(user.name);
          }
          System.out.print("JOINED" + user.name); //para quem ja esta na sala nova
        }
        user.sala = words[1];
        //adicionar o user nos parametros da sala
        salas.get(user.sala).put(user)
      }
      else {//se a sala nao existir
        //adicionar words[1] a lista de salas com o user
        salas.put(words[1], user);
        user.sala = words[1];
        System.out.print("OK");
      }
    }

//leave
    else if(words[0].equals("/leave")) {
      if(user.state == 3 && words.length == 1) {
        //RETIRAR USER DA SALA E APAGA-LA SE PRECISO
        sala = salas.get(user.sala);
        if(sala.size() == 1){
          salas.remove(user.sala);
        }
        else{
          sala.remove(user.name);
        }
        user.sala = null;
        System.out.print("OK");// para mim
        System.out.print("LEFT" + user.name);//para outros
      } else {
        System.out.print("ERROR");
      }
    }

//leave
    else if(words[0].equals("/bye")) {
      System.out.print("BYE");//mim
      if(state == "inside") {
        sala = salas.get(user.sala);
        if(sala.size() == 1){
          salas.remove(user.sala);
        }
        else{
          sala.remove(user.name);
        }
        System.out.print("LEFT" + user.name);
      }
      //apagar user da lista
      clients.remove(user.name);
      key.cancel();

      Socket s = null;
      try {
        s = user.key.channel().socket();
        System.out.println("Closing connection to " + s);
        s.close();
        users.remove(sc);
      } catch(IOException ie) {
        System.err.println("Error closing socket " + s + ": " + ie);
      }
    }
      users.remove(user.key.channel);
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
