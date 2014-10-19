package eastangliamapserver;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class Client implements Runnable
{
    private final Socket client;
    public  String address = "0.0.0.0";
    public  String port    = "";
    public  String name    = "Unnamed";

    private InputStream  in;
    private OutputStream out;

    private Thread clientThread;

    private boolean stop   = false;
    private int     errors = 0;

    private long  lastMessageTime    = System.currentTimeMillis();
    private long  timeoutTime        = 30000;
    private final Timer timeoutTimer = new Timer("timeoutTimer:" + address);

    public Client(Socket client) throws IOException
    {
        this.client = client;

        address = client.getInetAddress().getHostAddress();
        port    = Integer.toString(client.getPort());

        in  = client.getInputStream();
        out = client.getOutputStream();

        printClient("Initialise client at " + name + "/" + address, false);
        //addClientLog(String.format("Client at %s:%s joined", client.address, client.port + (client.name != null ? " (" + client.name + ")" : "")));
        addClientLog("Joined");

        clientThread = new Thread(this, "Client-" + address);
        clientThread.start();

        startTimeoutTimer();
    }

    @Override
    public void run()
    {
        while (!stop)
        {
            try
            {
                Object obj = new ObjectInputStream(in).readObject();

                if (obj == null)
                    continue;

                if (obj instanceof Map)
                {
                    HashMap<String, Object> message = (HashMap) obj;
                    MessageType type = MessageType.getType((int) message.get("type"));

                    lastMessageTime = System.currentTimeMillis();

                    switch (type)
                    {
                        case SOCKET_CLOSE:
                            printClient("Closing connection", false);
                            addClientLog("Close connection");

                            stop = true;
                            break;

                        case HEARTBEAT_REQUEST:
                            printClient("Sending heartbeat", false);
                            //addClientLog("Heartbeat ->");

                            sendHeartbeatReply();
                            break;

                        case HEARTBEAT_REPLY:
                            printClient("Received heartbeat", false);
                            //addClientLog("Heartbeat <-");
                            break;

                        case REQUEST_ALL:
                            printClient("Sending full map", false);
                            addClientLog("Full map");

                            sendAll();
                            break;

                        case REQUEST_HIST_TRAIN:
                            printClient("Sending history of train " + message.get("headcode"), false);
                            addClientLog("Train history: " + message.get("headcode") + " (" + message.get("berth_id") + ")");

                            sendHistoryOfTrain((String) message.get("id"));
                            break;

                        case REQUEST_HIST_BERTH:
                            printClient("Sending history of berth " + message.get("berth_id"), false);
                            addClientLog("Berth history: " + message.get("berth_id"));

                            sendHistoryOfBerth((String) message.get("berth_id"));
                            break;

                        case SET_NAME:
                            String newName = (String) message.get("name");

                            printClient("Set name to '" + newName + "'", false);
                            addClientLog("Re-Name: " + name + " --> " + newName);

                            name = newName;

                            EastAngliaSignalMapServer.gui.updateClientList();
                            break;
                    }
                }
                else
                    printClient("Erroneous message received", true);

                errors = 0;
            }
            catch (EOFException e) { errors++; }
            catch (IOException  e) { errors++; }
            catch (ClassNotFoundException e) {}
            finally { testErrors(); }
        }

        if (stop)
            printClient("Disconnected", false);
        else
            printClient("Disconnected erroneously", true);

        closeSocket();
    }

    public void printClient(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Client " + name + "] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Client " + name + "] " + message);
    }

    //<editor-fold defaultstate="collapsed" desc="SOCKET_CLOSE">
    public void sendSocketClose()
    {
        HashMap<String, Object> message = new HashMap<>();

        message.put("type", MessageType.SOCKET_CLOSE.getValue());

        try { new ObjectOutputStream(out).writeObject(message); errors = 0; }
        catch (IOException e) {}
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REQUEST">
    public void sendHeartbeatRequest()
    {
        HashMap<String, Object> message = new HashMap<>();

        message.put("type", MessageType.HEARTBEAT_REQUEST.getValue());

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REPLY">
    public void sendHeartbeatReply()
    {
        HashMap<String, Object> message = new HashMap<>();

        message.put("type", MessageType.HEARTBEAT_REPLY.getValue());

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_ALL">
    public void sendAll()
    {
        HashMap<String, Object> message = new HashMap<>();

        message.put("type", MessageType.SEND_ALL.getValue());
        message.put("message", EastAngliaSignalMapServer.CClassMap);

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_HIST_TRAIN">
    public void sendHistoryOfTrain(String id)
    {
        Berth berth = Berths.getBerth(id);

        if (berth != null)
        {
            HashMap<String, Object> message = new HashMap<>();

            message.put("type",     MessageType.SEND_HIST_TRAIN.getValue());
            message.put("berth_id", id);
            message.put("headcode", berth.getHeadcode());
            message.put("history",  berth.getTrainsHistory());

            sendMessage(message);
        }
        else
        {
            HashMap<String, Object> train = Berths.getTrain(id);

            HashMap<String, Object> message = new HashMap<>();

            message.put("type",     MessageType.SEND_HIST_TRAIN.getValue());
            message.put("berth_id", (String) train.get("berth_id"));
            message.put("headcode", (String) train.get("headcode"));
            message.put("history",  (List<String>) train.get("history"));

            sendMessage(message);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_HIST_BERTH">
    public void sendHistoryOfBerth(String berthId)
    {
        Berth berth = Berths.getBerth(berthId);

        if (berth != null)
        {
            HashMap<String, Object> message = new HashMap<>();

            message.put("type", MessageType.SEND_HIST_BERTH.getValue());
            message.put("berth_id", berthId);
            message.put("berth_descr", berth.getBerthDescription() + (!berth.getName().equals("") ? " (" + berth.getName() + ")" : ""));
            message.put("history", berth.getBerthsHistory());

            sendMessage(message);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_UPDATE">
    public void sendUpdate(Map updateMap)
    {
        HashMap<String, Object> message = new HashMap<>();

        message.put("type",    MessageType.SEND_UPDATE.getValue());
        message.put("message", updateMap);

        sendMessage(message);
    }
    //</editor-fold>

    private void sendMessage(Object message)
    {
        try { new ObjectOutputStream(out).writeObject(message); errors = 0; }
        catch (IOException e) { errors++; }

        testErrors();
    }

    private void testErrors()
    {
        if (errors > 3)
        {
            sendSocketClose();
            closeSocket();
        }
    }

    public void closeSocket()
    {
        try
        {
            client.shutdownOutput();
            out.close();
        }
        catch (IOException e) {}

        try
        {
            client.shutdownInput();
            in.close();
        }
        catch (IOException e) {}

        try { client.close(); }
        catch (IOException e) {}

        stop = true;
        timeoutTimer.cancel();

        Clients.remove(this);
    }

    private void startTimeoutTimer()
    {
        timeoutTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                if (System.currentTimeMillis() - lastMessageTime >= timeoutTime)
                {
                    if (timeoutTime == 30000)
                    {
                        timeoutTime = 60000;
                        sendHeartbeatRequest();
                    }
                    else
                    {
                        printClient("Connection timed out", true);
                        addClientLog("Timed out (" + (System.currentTimeMillis() - lastMessageTime) + ")");
                        sendSocketClose();
                        closeSocket();
                        timeoutTime = 30000;
                    }
                }
                else
                    timeoutTime = 30000;
            }
        }, 30000, 30000);
    }

    public Socket getSocket()
    {
        return client;
    }

    public void disconnect()
    {
        sendSocketClose();
        closeSocket();
    }

    public String getErrorString()
    {
        if (errors != 0)
            return " (" + errors + ")";
        else
            return "";
    }

    private void addClientLog(String message)
    {
        Clients.addClientLog("[" + name + "/" + address + "] " + message);
    }
}