import java.nio.channels.SocketChannel;

class UserInfo implements Comparable<UserInfo>
{
  public String name;
  public String chat_room;
  public SocketChannel channel;

  public UserInfo(SocketChannel channel)
  {
    this.name = null;
    this.chat_room = null;
    this.channel = channel;
  }

  public int compareTo(UserInfo other)
  {
    return name.compareTo(other.name);
  }
}
