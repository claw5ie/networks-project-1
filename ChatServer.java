import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.Character.*;

public class ChatServer
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate(16384);

  // Decoder for incoming text -- assume UTF-8
  static private final CharsetDecoder decoder =
    Charset.forName("UTF8").newDecoder();

  static private Hashtable<SocketChannel, UserInfo> users = new Hashtable<>();
  static private TreeMap<String, UserInfo> clients = new TreeMap<>();
  static private TreeMap<String, TreeSet<UserInfo>> salas = new TreeMap<>();

  static public void main(String args[]) throws Exception
  {
    try
    {
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

      while (true)
      {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        // If we don't have any activity, loop around and wait again
        if (selector.select() == 0)
          continue;

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        // Get a key representing one of bits of I/O activity
        for (SelectionKey key : keys)
        {
          // What kind of activity is it?
          if (key.isAcceptable())
          {
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

          }
          else if (key.isReadable())
          {
            SocketChannel sc = null;

            try
            {
              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              String message = processInput(sc);

              if (message != null)
              {
                UserInfo user = users.get(sc);

                if (message.charAt(0) == '/')
                {
                  System.out.println("Server: " + message);
                  process_command(message.split(" ", 0), user);
                }
                else
                {
                  System.out.println(
                    (user == null ? "Anon: " : user.name) + ": " + message
                    );
                }
              }
              else
              {
                // If the connection is dead, remove it from the selector
                // and close it
                key.cancel();

                Socket s = null;
                try
                {
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

              try
              {
                sc.close();
              } catch(IOException ie2) {
                System.out.println(ie2);
              }

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

  static void send_message(UserInfo user, String message) throws IOException
  {
    buffer.clear();
    buffer.put((message + '\n').getBytes());
    buffer.flip();
    user.channel.write(buffer);
  }

  static void process_command(String[] words, UserInfo user) throws IOException
  {
    if (words[0].equals("/nick"))
    {
      // nick

      if (words.length == 2)
      {
        String name = words[1];
        if (!clients.containsKey(name))
        {
          send_message(user, "OK");
          if (user.state == 1)
          {
            user.state = 2; // outside
            clients.put(name, user);
          }
          else if (user.state == 3)
          {
            System.out.println("NEWNICK " + user.name + " " + name);
          }
          user.name = name;
        }
        else
        {
          send_message(user, "ERROR");
        }
      }
      else
      {
        send_message(user, "ERROR");
      }
    }
    else if (words[0].equals("/join"))
    {
      // join

      if (words.length != 2 || user.state == 1)
      {
        send_message(user, "ERROR");
      }
      else if (salas.containsKey(words[1]))
      {
        System.out.println("OK"); // para quem usa o comando
        if (user.state == 2)
        {
          System.out.println("JOINED" + user.name); // para quem ja esta na sala
          user.state = 3; // inside
        }
        else
        {
          System.out.println("LEFT" + user.name); // para quem esta na sala antiga
          // retirar user da sala na lista e se a sala ficou vazia apaga-la
          TreeSet<UserInfo> sala = salas.get(user.sala);
          if (sala.size() == 1)
          {
            salas.remove(user.sala);
          }
          else
          {
            sala.remove(user.name);
          }
          // para quem ja esta na sala nova
          System.out.println("JOINED" + user.name);
        }
        user.sala = words[1];
        // adicionar o user nos parametros da sala
        salas.get(user.sala).add(user);
      }
      else
      {
        // se a sala nao existir
        // adicionar words[1] a lista de salas com o user
        TreeSet<UserInfo> new_sala = new TreeSet<>();
        new_sala.add(user);
        salas.put(words[1], new_sala);
        user.sala = words[1];
        System.out.println("OK");
      }
    }
    else if (words[0].equals("/leave"))
    {
      // leave

      if (user.state == 3 && words.length == 1)
      {
        // RETIRAR USER DA SALA E APAGA-LA SE PRECISO
        TreeSet<UserInfo> sala = salas.get(user.sala);
        if (sala.size() == 1)
        {
          salas.remove(user.sala);
        }
        else
        {
          sala.remove(user.name);
        }
        user.sala = null;
        System.out.println("OK"); // para mim
        System.out.println("LEFT" + user.name); // para outros
      }
      else
      {
        send_message(user, "ERROR");
      }
    }
    else if (words[0].equals("/bye"))
    {
      // leave

      System.out.println("BYE"); // mim
      if (user.state == 3)
      {
        TreeSet<UserInfo> sala = salas.get(user.sala);
        if (sala.size() == 1)
        {
          salas.remove(user.sala);
        }
        else
        {
          sala.remove(user.name);
        }
        System.out.println("LEFT" + user.name);
      }

      // apagar user da lista
      users.remove(user.channel);
      if (user.name != null)
        clients.remove(user.name);

      Socket socket = null;
      try
      {
        socket = user.channel.socket();
        System.out.println("Closing connection to " + socket);
        user.channel.close();
        socket.close();
      } catch(IOException ie) {
        System.err.println("Error closing socket " + socket + ": " + ie);
      }
    }
  }

  // Just read the message from the socket and send it to stdout
  static private String processInput(
    SocketChannel socket_channel
    ) throws IOException
  {
    // Read the message to the buffer
    buffer.clear();
    socket_channel.read(buffer);
    buffer.flip();

    if (buffer.limit() > 0)
    {
      String message = decoder.decode(buffer).toString();

      int last_newline = message.length();
      while (last_newline-- > 0)
      {
        if (!Character.isWhitespace(message.charAt(last_newline)))
        {
          last_newline++;
          break;
        }
      }

      return last_newline > 0 ? message.substring(0, last_newline) : "";
    }

    return null;
  }
}
