import java.nio.channels.SelectionKey;

public class UserInfo
{
  public String name;
  public String sala;
  public int state;
  public SelectionKey key;

  public UserInfo(SelectionKey key)
  {
    this.name = null;
    this.state = 1; // init
    this.sala = null;
    this.key = key;
  }
}
