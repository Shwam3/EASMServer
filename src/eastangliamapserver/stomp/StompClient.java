package eastangliamapserver.stomp;

import java.io.*;
import java.net.Socket;
import java.util.*;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.*;

public class StompClient extends Stomp implements MessageReceiver
{
    private Thread       listener;
    private OutputStream output;
    private InputStream  input;
    private Socket       socket;

    private String subscriptionID = "";

    public StompClient(String server, int port, String login, String pass, String clientId) throws IOException, LoginException
    {
        socket = new Socket(server, port);
        input  = /*new BufferedInputStream(*/socket.getInputStream()/*)*/;
        output = /*new BufferedOutputStream(*/socket.getOutputStream()/*)*/;

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

    public void ack(String messageId)
    {
        try
        {
            StringBuilder message = new StringBuilder("ACK\n");

            message.append("subscription:" + subscriptionID + "\n");
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

    public void subscribe(String name, Listener listener, String subID, Map headers)
    {
        subscriptionID = subID;
        super.subscribe(name, listener, headers);
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