import java.nio.ByteBuffer;

class CyclicBuffer
{
  public byte[] data;
  public int position;
  public int size;
  public int capacity;

  CyclicBuffer(int capacity)
  {
    this.data = new byte[capacity];
    this.position = 0;
    this.size = 0;
    this.capacity = capacity;
  }

  void put(ByteBuffer buffer)
  {
    if (size + buffer.limit() <= capacity)
    {
      for (int i = 0, j = position + size; i < buffer.limit(); i++, j++)
        data[j -= (j >= capacity ? capacity : 0)] = buffer.get(i);

      size += buffer.limit();
    }
    else
    {
      System.err.println("Overflow in buffer detected!");

      int limit = capacity < buffer.limit() ? capacity : buffer.limit();
      for (int i = 0, j = position + size; i < limit; i++, j++)
        data[j -= (j >= capacity ? capacity : 0)] = buffer.get(i);

      this.size = capacity;
    }
  }

  String read_line()
  {
    int newline_pos = find_newline();

    if (newline_pos < 0)
    {
      return null;
    }
    else if (position <= newline_pos)
    {
      int length = newline_pos - position;
      String res = new String(data, position, length);

      position = (newline_pos + 1) % capacity;
      size -= length + 1;

      return res;
    }
    else
    {
      int length = capacity - position;
      String res = new String(data, position, length) +
        new String(data, 0, newline_pos);

      position = (newline_pos + 1) % capacity;
      size -= length + newline_pos + 1;

      return res;
    }
  }

  private int find_newline()
  {
    int j = position;
    for (int i = 0; i < size; i++, j++)
    {
      if (data[j -= (j >= capacity ? capacity : 0)] == '\n')
        return j;
    }

    return -1;
  }
}
