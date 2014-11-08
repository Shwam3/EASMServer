package eastangliamapserver;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class Client implements Runnable
{
    private final Socket client;
    public  String address = "--.--.--.--";
    public  String port    = "";
    public  String name    = "Unnamed";

    private Thread clientThread;

    private boolean stop   = false;
    private int     errors = 0;

    private ArrayList<String> history = new ArrayList<>();

    private long  lastMessageTime    = System.currentTimeMillis();
    private long  timeoutTime        = 30000;
    private       Timer timeoutTimer;

    public Client(Socket client) throws IOException
    {
        this.client = client;

        address = client.getInetAddress().getHostAddress();
        port    = Integer.toString(client.getPort());

        printClient("Initialise client at " + name + "/" + address, false);
        //addClientLog(String.format("Client at %s:%s joined", client.address, client.port + (client.name != null ? " (" + client.name + ")" : "")));
        addClientLog("Connected", true);

        try { timeoutTimer = new Timer("timeoutTimer:" + address); }
        catch (IllegalStateException e) {}
        startTimeoutTimer();

        clientThread = new Thread(this, "Client-" + address);
        clientThread.start();
    }

    @Override
    public void run()
    {
        while (!stop)
        {
            try
            {
                Object obj = new ObjectInputStream(client.getInputStream()).readObject();

                if (obj == null)
                    continue;

                if (obj instanceof Map)
                {
                    HashMap<String, Object> message = (HashMap<String, Object>) obj;
                    MessageType type = MessageType.getType((int) message.get("type"));

                    lastMessageTime = System.currentTimeMillis();

                    switch (type)
                    {
                        case SOCKET_CLOSE:
                            printClient("Closing connection", false);
                            addClientLog("Close connection", true);

                            stop = true;
                            break;

                        case HEARTBEAT_REQUEST:
                            //printClient("Sending heartbeat", false);
                            addClientLog("Heartbeat ->", false);

                            sendHeartbeatReply();
                            break;

                        case HEARTBEAT_REPLY:
                            //printClient("Received heartbeat", false);
                            addClientLog("Heartbeat <-", false);
                            break;

                        case REQUEST_ALL:
                            printClient("Sending full map", false);
                            addClientLog("Full map", true);

                            sendAll();
                            break;

                        case REQUEST_HIST_TRAIN:
                            printClient("Sending history of train " + message.get("headcode"), false);
                            addClientLog("Train history: " + message.get("headcode") + " (" + message.get("berth_id") + ")", true);

                            sendHistoryOfTrain((String) message.get("id"));
                            break;

                        case REQUEST_HIST_BERTH:
                            printClient("Sending history of berth " + message.get("berth_id"), false);
                            addClientLog("Berth history: " + message.get("berth_id"), true);

                            sendHistoryOfBerth((String) message.get("berth_id"));
                            break;

                        case SET_NAME:
                            String newName = (String) message.get("name");

                            printClient("Set name to '" + newName + "'", false);
                            addClientLog("Re-Name: " + name + " --> " + newName, true);

                            name = newName;

                            EastAngliaSignalMapServer.gui.updateClientList();
                            break;
                    }
                }
                else
                    printClient("Erroneous message received", true);

                errors = 0;
            }
            catch (EOFException e) { errors++; addClientLog(String.valueOf(e), false); }
            catch (IOException  e) { errors++; addClientLog(String.valueOf(e), false); }
            catch (ClassNotFoundException e) { addClientLog(String.valueOf(e), false); }
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
    public void sendSocketClose(String reason)
    {
        printClient("Socket closed sent (" + reason + ")", false);
        HashMap<String, Object> message = new HashMap<>();

        message.put("type", MessageType.SOCKET_CLOSE.getValue());
        if (reason != null && !reason.equals(""))
            message.put("reason", reason);

        try { new ObjectOutputStream(client.getOutputStream()).writeObject(message); errors = 0; }
        catch (IOException e) { addClientLog(String.valueOf(e), false); }
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

        HashMap map = EastAngliaSignalMapServer.CClassMap;
        map.put("XXMOTD", Berths.getBerth("XXMOTD").getHeadcode());

        message.put("type", MessageType.SEND_ALL.getValue());
        message.put("message", map);

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
            message.put("history",  (ArrayList<String>) train.get("history"));

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
        try { new ObjectOutputStream(client.getOutputStream()).writeObject(message); errors = 0; }
        catch (IOException e)
        {
            errors++;
            addClientLog(String.valueOf(e), false);
            e.printStackTrace();
        }

        testErrors();
    }

    private void testErrors()
    {
        if (errors > 3)
        {
            sendSocketClose("Errors:" + errors);
            closeSocket();
        }
    }

    public void closeSocket()
    {
        try
        {
            client.shutdownOutput();
            client.getOutputStream().close();
        }
        catch (IOException e) {}

        try
        {
            client.shutdownInput();
            client.getInputStream().close();
        }
        catch (IOException e) {}

        try { client.close(); }
        catch (IOException e) {}

        stop = true;

        try { timeoutTimer.cancel(); }
        catch (IllegalStateException e) {}

        Clients.remove(this);
    }

    private void startTimeoutTimer()
    {
        try
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
                        } else
                        {
                            printClient("Connection timed out", true);
                            addClientLog("Timed out (" + (System.currentTimeMillis() - lastMessageTime) + ")", true);
                            sendSocketClose("Timed Out");
                            closeSocket();
                            timeoutTime = 30000;
                        }
                    } else
                    {
                        timeoutTime = 30000;
                    }
                }
            }, 30000, 30000);
        }
        catch (IllegalStateException e)
        {
            printClient("safe: " + e, true);
            e.printStackTrace();
        }
    }

    public Socket getSocket()
    {
        return client;
    }

    public void disconnect(String resaon)
    {
        sendSocketClose(resaon);
        closeSocket();
    }

    public String getErrorString()
    {
        if (errors != 0)
            return " (" + errors + ")";
        else
            return "";
    }

    private void addClientLog(String message, boolean addToGlobal)
    {
        history.add(0, new SimpleDateFormat("[dd/MM HH:mm] ").format(new Date()) + message);

        if (addToGlobal)
            Clients.addClientLog("[" + name + "/" + address + "] " + message);
    }

    public ArrayList<String> getHistory()
    {
        return history;
    }
}