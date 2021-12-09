import java.nio.channels.SocketChannel;

class UserInfo implements Comparable<UserInfo>
{
  public String name;
  public String chat_room;
  public SocketChannel channel;
  public CyclicBuffer reader;

  public UserInfo(SocketChannel channel)
  {
    this.name = null;
    this.chat_room = null;
    this.channel = channel;
    this.reader = new CyclicBuffer(1 << 14);
  }

  public int compareTo(UserInfo other)
  {
    return name.compareTo(other.name);
  }
}
