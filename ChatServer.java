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
  static private TreeMap<String, TreeSet<UserInfo>> chat_rooms = new TreeMap<>();

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

              UserInfo user = users.get(sc);
              if (message != null)
              {
                if (message.charAt(0) == '/')
                {
                  process_command(message.split(" ", 0), user);
                }
                else
                {
                  if (user.chat_room == null)
                  {
                    send_message_to_user(user, "ERROR");
                  }
                  else
                  {
                    send_message_to_everyone_in_room_except(
                      user, "MESSAGE " + user.name + " " + message
                      );
                  }
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

                users.remove(user.channel);

                if (user.name != null)
                  clients.remove(user.name);

                if (user.chat_room != null)
                  remove_user_from_room(user);
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

  static void send_message_to_user(
    UserInfo user, String message
    ) throws IOException
  {
    buffer.clear();
    buffer.put((message + '\n').getBytes());
    buffer.flip();
    user.channel.write(buffer);
  }

  static void send_message_to_everyone_in_room(
    TreeSet<UserInfo> room, String message
    ) throws IOException
  {
    byte[] message_as_bytes = (message + '\n').getBytes();

    for (UserInfo user : room)
    {
      buffer.clear();
      buffer.put(message_as_bytes);
      buffer.flip();
      user.channel.write(buffer);
    }
  }

  // Send to everyone except the user, in room that the user is in.
  static void send_message_to_everyone_in_room_except(
    UserInfo user, String message
    ) throws IOException
  {
    byte[] message_as_bytes = (message + '\n').getBytes();

    for (UserInfo user_in_room : chat_rooms.get(user.chat_room))
    {
      // Instead of comparing strings, it is possible to compare references
      // instead, but you need to guarantee that all string references are taken
      // from "UserInfo" class.
      if (!user_in_room.name.equals(user.name))
      {
        buffer.clear();
        buffer.put(message_as_bytes);
        buffer.flip();
        user_in_room.channel.write(buffer);
      }
    }
  }

  static void remove_user_from_room(UserInfo user) throws IOException
  {
    if (user.chat_room == null)
      return;

    TreeSet<UserInfo> room = chat_rooms.get(user.chat_room);

    if (room.size() == 1)
    {
      chat_rooms.remove(user.chat_room);
    }
    else
    {
      room.remove(user);
      send_message_to_everyone_in_room(room, "LEFT " + user.name);
    }
  }

  static void process_command(String[] words, UserInfo user) throws IOException
  {
    if (words[0].equals("/nick"))
    {
      if (words.length != 2 || clients.containsKey(words[1]))
      {
        send_message_to_user(user, "ERROR");

        return;
      }

      String new_user_name = words[1];
      if (user.name == null)
      {
        clients.put(new_user_name, user);
      }
      else
      {
        clients.remove(user.name);
        clients.put(new_user_name, user);

        if (user.chat_room != null)
        {
          send_message_to_everyone_in_room_except(
            user, "NEWNICK " + user.name + " " + new_user_name
            );
        }
      }

      send_message_to_user(user, "OK");
      user.name = new_user_name;
    }
    else if (words[0].equals("/join"))
    {
      if (words.length != 2 || user.name == null)
      {
        send_message_to_user(user, "ERROR");

        return;
      }

      String room_to_join = words[1];
      if (chat_rooms.containsKey(room_to_join))
      {
        if (user.chat_room == null)
        {
          user.chat_room = room_to_join;
          TreeSet<UserInfo> room = chat_rooms.get(room_to_join);
          send_message_to_everyone_in_room(room, "JOINED " + user.name);
          send_message_to_user(user, "OK");
          room.add(user);
        }
        else if (user.chat_room.equals(room_to_join))
        {
          return; // What to do!?!?!?
        }
        else
        {
          remove_user_from_room(user);
          TreeSet<UserInfo> new_room = chat_rooms.get(room_to_join);
          send_message_to_everyone_in_room(new_room, "JOINED " + user.name);
          send_message_to_user(user, "OK");
          new_room.add(user);
          user.chat_room = room_to_join;
        }
      }
      else
      {
        TreeSet<UserInfo> new_room = new TreeSet<>();
        new_room.add(user);
        chat_rooms.put(room_to_join, new_room);
        user.chat_room = room_to_join;
        send_message_to_user(user, "OK");
      }
    }
    else if (words[0].equals("/leave"))
    {
      if (user.chat_room == null || words.length != 1)
      {
        send_message_to_user(user, "ERROR");

        return;
      }

      remove_user_from_room(user);
      user.chat_room = null;
      send_message_to_user(user, "OK");
    }
    else if (words[0].equals("/bye"))
    {
      if (words.length != 1)
      {
        send_message_to_user(user, "ERROR");

        return;
      }

      send_message_to_user(user, "BYE");
      remove_user_from_room(user);
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
    else if (words[0].equals("/priv"))
    {
      // Can you send messages to yourself???
      // Also, doesn't make sense to partition message here.
      if (words.length != 3 || !clients.containsKey(words[1]))
      {
        send_message_to_user(user, "ERROR");

        return;
      }

      send_message_to_user(
        clients.get(words[1]), "PRIVATE " + user.name + " " + words[2]
        );
    }
    else
    {
      send_message_to_user(user, "ERROR");
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
