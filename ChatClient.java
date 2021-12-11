import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient
{
  // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
  JFrame frame = new JFrame("Chat Client");
  private JTextField chatBox = new JTextField();
  private JTextArea chatArea = new JTextArea();
  // --- Fim das variáveis relacionadas coma interface gráfica

  // Se for necessário adicionar variáveis ao objecto ChatClient, devem
  // ser colocadas aqui

  Socket client = null;
  BufferedWriter send_to_server = null;

  // Método a usar para acrescentar uma string à caixa de texto
  // * NÃO MODIFICAR *
  public void printMessage(final String message)
  {
    chatArea.append(message);
  }

  // Construtor
  public ChatClient(String server, int port) throws IOException
  {
    // Inicialização da interface gráfica --- * NÃO MODIFICAR *
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(chatBox);
    frame.setLayout(new BorderLayout());
    frame.add(panel, BorderLayout.SOUTH);
    frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
    frame.setSize(500, 300);
    frame.setVisible(true);
    chatArea.setEditable(false);
    chatBox.setEditable(true);
    chatBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e)
        {
          try
          {
            newMessage(chatBox.getText());
          } catch (IOException ex) {
          } finally {
            chatBox.setText("");
          }
        }
      });
    frame.addWindowListener(new WindowAdapter() {
        public void windowOpened(WindowEvent e)
        {
          chatBox.requestFocusInWindow();
        }
      });

    // --- Fim da inicialização da interface gráfica
    // Se for necessário adicionar código de inicialização ao
    // construtor, deve ser colocado aqui

    client = new Socket(server, port);
    send_to_server = new BufferedWriter(
      new OutputStreamWriter(client.getOutputStream())
      );
  }

  // Método invocado sempre que o utilizador insere uma mensagem
  // na caixa de entrada
  public void newMessage(String message) throws IOException
  {
    printMessage(message + '\n');
    send_to_server.write(message);
    send_to_server.newLine();
    send_to_server.flush();
  }

  // Método principal do objecto
  public void run() throws IOException
  {
    BufferedReader read_from_server = new BufferedReader(
      new InputStreamReader(client.getInputStream())
      );

    new Thread(new Runnable() {
        public void run()
        {
          String message = null;

          try
          {
            while (
              (message = read_from_server.readLine()) != null &&
              client.isConnected()
              )
            {
              message = process(message);
              printMessage(message + '\n');
            }
          } catch (IOException io) {
            System.out.println("error: failed to read the message: " + io);
          }
        }
      }).run();
  }

  public String process(String message){
    if(message.startsWith("MESSAGE")){
      String[] mess = message.split(" ", 3);
      message = mess[1] + ": " + mess[2];
    }
    else if(message.startsWith("JOINED")){
      String[] mess = message.split(" ", 2);
      message = mess[1] + " joined the room";
    }
    else if(message.startsWith("LEFT")){
      String[] mess = message.split(" ", 2);
      message = mess[1] + " lefted the room";
    }
    else if(message.startsWith("NEWNICK")){
      String[] mess = message.split(" ", 3);
      message = mess[1] + " changed name to: " + mess[2];
    }
    else if(message.startsWith("PRIVATE")){
      String[] mess = message.split(" ", 3);
      message = "private message from " + mess[1] + ": " + mess[2];
    }
    return message;
  }

  // Instancia o ChatClient e arranca-o invocando o seu método run()
  // * NÃO MODIFICAR *
  public static void main(String[] args) throws IOException
  {
    ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
    client.run();
  }
}
