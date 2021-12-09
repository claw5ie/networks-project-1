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
    if (size + buffer.limit() > capacity)
    {
      System.err.println("Overflow in buffer detected!");
    }
    else if (position + size >= capacity)
    {
      int last_byte = position + size - capacity;

      for (int i = 0; i < buffer.limit(); i++)
        data[last_byte++] = buffer.get(i);

      size += buffer.limit();
    }
    else
    {
      int last_byte = position + size;

      if (buffer.limit() > capacity - last_byte)
      {
        int bytes_to_read = capacity - last_byte;

        int i = 0;
        for (; i < bytes_to_read; i++)
          data[last_byte++] = buffer.get(i);

        for (int j = 0; i < buffer.limit(); j++, i++)
          data[j] = buffer.get(i);

        size += buffer.limit();
      }
      else
      {
        for (int i = 0; i < buffer.limit(); i++)
          data[last_byte++] = buffer.get(i);

        size += buffer.limit();
      }
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
    int newline_pos = position;
    for (int i = 0; i < size; i++, newline_pos++)
    {
      if (newline_pos >= capacity)
        newline_pos -= capacity;

      if (data[newline_pos] == '\n')
        return newline_pos;
    }

    return -1;
  }
}
