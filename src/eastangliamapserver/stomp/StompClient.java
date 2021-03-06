package eastangliamapserver.stomp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Command;
import net.ser1.stomp.Listener;
import net.ser1.stomp.MessageReceiver;
import net.ser1.stomp.Receiver;
import net.ser1.stomp.Stomp;

public class StompClient extends Stomp implements MessageReceiver
{
    private Thread       listener;
    private OutputStream output;
    private InputStream  input;
    private Socket       socket;

    private String clientID;

    public StompClient(String server, int port, String login, String pass, String clientId) throws IOException, LoginException
    {
        socket = new Socket(server, port);
        input  = socket.getInputStream();
        output = socket.getOutputStream();

        listener = new Receiver(this, input);
        listener.start();

        // Connect to the server
        Map<String, String> header = new HashMap<>();
        header.put("login", login);
        header.put("passcode", pass);
        header.put("client-id", clientId);
        header.put("heart-beat", "0,10000");
        header.put("accept-version", "1.2");

        transmit(Command.CONNECT, header, null);
        this.clientID = clientId;

        try
        {
            int connectAttempts = 0;
            String error = null;

            while (connectAttempts <= 20 && (!isConnected() && ((error = nextError()) == null)))
            {
                Thread.sleep(100);
                connectAttempts++;
            }

            if (error != null)
                throw new LoginException(error);
        }
        catch (InterruptedException e) {}
    }

    @Override
    public void disconnect(Map<String, String> header)
    {
        if (!isConnected())
            return;

        transmit(Command.DISCONNECT, header, null);
        listener.interrupt();

        try { input.close(); }
        catch (IOException e) {}

        try { output.close(); }
        catch (IOException e) {}

        try { socket.close(); }
        catch (IOException e) {}

        connected = false;
    }

    public void ack(String id)
    {
        try
        {
            if (id == null || id.isEmpty() || id.equals("null"))
                throw new IllegalArgumentException("id cannot be null or empty");

            StringBuilder message = new StringBuilder("ACK\n");

            message.append("id:").append(id.replace("\\c", ":")).append("\n");                                  // 1.2

            message.append("\n");
            message.append("\000");

            output.write(message.toString().getBytes(Command.ENCODING));
        }
        catch (IOException e)
        {
            receive(Command.ERROR, null, e.getMessage());
        }
    }

    public void subscribe(String topicName, String topicID, Listener listener)
    {
        Map<String, String> headers = new HashMap<>();

        headers.put("ack", "client-individual");
        headers.put("id",  clientID + "-" + topicID);
        headers.put("activemq.subscriptionName", clientID + "-" + topicID);

        super.subscribe(topicName, listener, headers);

        StompConnectionHandler.printStomp("Subscribed to \"" + topicName + "\" (ID: \"" + clientID + "-" + topicID + "\")", false);
    }

    @Override
    protected void transmit(Command command, Map<String, String> header, String body)
    {
        try
        {
            StringBuilder message = new StringBuilder(command.toString());
            message.append("\n");

            if (header != null)
                for (String key : header.keySet())
                {
                    String value = header.get(key);
                    message.append(key).append(":").append(value).append("\n");
                }

            message.append("\n");

            if (body != null)
                message.append(body);

            message.append("\000");

            output.write(message.toString().getBytes(Command.ENCODING));
        }
        catch (IOException e)
        {
            receive(Command.ERROR, null, e.getMessage());
        }
    }

    @Override
    public boolean isClosed()
    {
        return socket.isClosed();
    }
}