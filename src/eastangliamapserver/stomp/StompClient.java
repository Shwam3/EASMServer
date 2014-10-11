package eastangliamapserver.stomp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Command;
import net.ser1.stomp.MessageReceiver;
import net.ser1.stomp.Receiver;
import net.ser1.stomp.Stomp;

public class StompClient extends Stomp implements MessageReceiver
{
    private Thread       listener;
    private OutputStream output;
    private InputStream  input;
    private Socket       socket;

    public StompClient(String server, int port, String login, String pass, String clientId) throws IOException, LoginException
    {
        socket = new Socket(server, port);
        input  = socket.getInputStream();
        output = socket.getOutputStream();

        listener = new Receiver(this, input);
        listener.start();

        // Connect to the server
        HashMap header = new HashMap();
        header.put("login", login);
        header.put("passcode", pass);
        header.put("client-id", clientId);
        header.put("heart-beat", "0,10000");

        transmit(Command.CONNECT, header, null);

        try
        {
            String error = null;
            while (!isConnected() && ((error = nextError()) == null))
            {
              Thread.sleep(100);
            }

            if (error != null) throw new LoginException(error);
        }
        catch (InterruptedException e) {}
    }

    @Override
    public void disconnect(Map header)
    {
        if (!isConnected())
            return;

        transmit(Command.DISCONNECT, header, null);
        listener.interrupt();
        Thread.yield();

        try { input.close(); }
        catch (IOException e) {}

        try { output.close(); }
        catch (IOException e) {}

        try { socket.close(); }
        catch (IOException e) {}

        _connected = false;
    }

    public void ack(String subscriptionId, String messageId)
    {
        try
        {
            StringBuilder message = new StringBuilder("ACK\n");

            message.append("subscription:" + subscriptionId + "\n");
            message.append("message-id:"   + messageId      + "\n");

            message.append("\n");
            message.append("\000");

            output.write(message.toString().getBytes(Command.ENCODING));
        }
        catch (IOException e)
        {
            receive(Command.ERROR, null, e.getMessage());
        }
    }

    @Override
    protected void transmit(Command command, Map header, String body)
    {
        try
        {
            StringBuilder message = new StringBuilder(command.toString());
            message.append( "\n" );

            if (header != null)
            {
                for (Iterator keys = header.keySet().iterator(); keys.hasNext();)
                {
                    String key   = (String) keys.next();
                    String value = (String) header.get(key);
                    message.append(key + ":" + value + "\n");
                }
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