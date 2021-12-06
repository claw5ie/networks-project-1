import java.nio.channels.SocketChannel;

class UserInfo implements Comparable<UserInfo>
{
  public String name;
  public String sala;
  public int state;
  public SocketChannel channel;

  public UserInfo(SocketChannel channel)
  {
    this.name = null;
    this.state = 1; // init
    this.sala = null;
    this.channel = channel;
  }

  public int compareTo(UserInfo other)
  {
    return name.compareTo(other.name);
  }
}
