package eastangliamapserver.server;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.MessageType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class ClientOld implements Runnable, Client
{
    private final Socket client;
  //private String address = "0.0.0.0";
  //private String port    = "";
    private String name    = "Unnamed";

    private Properties props = new Properties();

    private boolean         stop   = false;
    private int             errors = 0;
    private List<Throwable> errorList = new ArrayList<>();

    private List<String> history = new ArrayList<>();

    private long  lastMessageTime = System.currentTimeMillis();
    private long  timeoutTime     = 30000;
    private       Timer timeoutTimer;

    public ClientOld(Socket client) throws IOException
    {
        this.client = client;

        String address = client.getInetAddress().getHostAddress();
        //port    = Integer.toString(client.getPort());

        ServerHandler.printServer("Initialise client at " + address, false);
        //addClientLog(String.format("Client at %s:%s joined", client.address, client.port + (client.name != null ? " (" + client.name + ")" : "")));
        addClientLog("Connected", true);

        try { timeoutTimer = new Timer("timeoutTimer-" + address); }
        catch (IllegalStateException e) {}
        startTimeoutTimer();

        Thread clientThread = new Thread(this, "Client-" + address);
        clientThread.start();
    }

    @Override
    public void run()
    {
        while (!stop)
        {
            try
            {
                Object obj = new ObjectInputStream(new BufferedInputStream(client.getInputStream())).readObject();

                if (obj == null)
                    continue;

                if (obj instanceof Map)
                {
                    Map<String, Object> message = (Map<String, Object>) obj;
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
                            addClientLog("Full map", false);

                            sendAll();
                            break;

                        case REQUEST_HIST_TRAIN:
                            printClient("Sending history of train " + message.get("id"), false);
                            addClientLog("Train history: " + message.get("id") + " (" + message.get("berth_id") + ")", true);

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

                            props = (message.get("props") == null ? new Properties() : (Properties) message.get("props"));

                            EastAngliaSignalMapServer.updateServerGUIs();
                            break;

                        case SEND_MESSAGE:
                            printClient((String) message.get("message"), false);
                            addClientLog((String) message.get("message"), true);
                            break;
                    }
                }
                else
                    printClient("Erroneous message received (" + String.valueOf(obj) + ")", true);

                errors = 0;
            }
            catch (IOException  e) { errorList.add(e); errors++; addClientLog(String.valueOf(e), false); }
            catch (NullPointerException | ClassNotFoundException e) { addClientLog(String.valueOf(e), false); }
            finally { testErrors(); }
        }

        if (stop)
            printClient("Disconnected", false);
        else
            printClient("Disconnected erroneously", true);

        closeSocket();
    }

    //<editor-fold defaultstate="collapsed" desc="SOCKET_CLOSE">
    public void sendSocketClose(String reason)
    {
        printClient("Socket closed sent (" + reason + ")", false);
        Map<String, Object> message = new HashMap<>();

        message.put("type", MessageType.SOCKET_CLOSE.getValue());
        if (reason != null && !reason.equals(""))
            message.put("reason", reason);

        try
        {
            new ObjectOutputStream(client.getOutputStream()).writeObject(message);

            errors = 0;
        }
        catch (IOException e) {}
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REQUEST">
    public void sendHeartbeatRequest()
    {
        Map<String, Object> message = new HashMap<>();

        message.put("type", MessageType.HEARTBEAT_REQUEST.getValue());

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REPLY">
    public void sendHeartbeatReply()
    {
        Map<String, Object> message = new HashMap<>();

        message.put("type", MessageType.HEARTBEAT_REPLY.getValue());

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_ALL">
    public void sendAll()
    {
        Map<String, Object> message = new HashMap<>();
        Map<String, String> dataMap = new HashMap<>(EastAngliaSignalMapServer.CClassMap.size() + EastAngliaSignalMapServer.SClassMap.size());

        Map<String, String> sclass = new HashMap<>(EastAngliaSignalMapServer.SClassMap);
        String[] keys = sclass.keySet().toArray(new String[0]);
        for (String key : keys)
            if (!key.contains(":"))
                sclass.remove(key);

        dataMap.putAll(EastAngliaSignalMapServer.CClassMap);
        dataMap.putAll(sclass);

        /*for (Map.Entry<String, Map<String, Integer>> pairs : new HashMap<>(EastAngliaSignalMapServer.SClassMap).entrySet())
        {
            if (pairs.getKey().toUpperCase().equals(pairs.getKey()))
                for (Map.Entry<String, Integer> pairs2 : pairs.getValue().entrySet())
                {
                    if (pairs2.getKey().toUpperCase().equals(pairs2.getKey()))
                    {
                        char[] chrs = String.format("%" + ((int) Math.ceil(Integer.toBinaryString(pairs2.getValue()).length() / 8f) * 8) + "s", Integer.toBinaryString(pairs2.getValue())).replace(" ", "0").toCharArray();
                        for (int i = 7; i > 0; i--)
                            dataMap.put(pairs.getKey() + pairs2.getKey() + ":" + i, chrs[i]);
                    }
                }
        }*/
        //if (version >= 13)
        //    dataMap.put("SClassData", EastAngliaSignalMapServer.SClassMap);

        message.put("type", MessageType.SEND_ALL.getValue());
        message.put("message", dataMap);

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_HIST_TRAIN">
    public void sendHistoryOfTrain(String id)
    {
        Berth berth = Berths.getBerth(id);

        if (berth != null)
        {
            Map<String, Object> message = new HashMap<>();

            message.put("type",     MessageType.SEND_HIST_TRAIN.getValue());
            message.put("berth_id", id);
            message.put("id",       id);
            message.put("headcode", berth.getHeadcode());
            message.put("history",  berth.getTrainsHistory());

            sendMessage(message);
        }
        else
        {
            Map<String, Object> train = Berths.getTrain(id);

            Map<String, Object> message = new HashMap<>();

            message.put("type",     MessageType.SEND_HIST_TRAIN.getValue());
            message.put("id",       id);
            message.put("berth_id", train == null ? "" : (String) train.get("berth_id"));
            message.put("headcode", train == null ? "" : (String) train.get("headcode"));
            message.put("history",  train == null ? new ArrayList<String>() : (List<String>) train.get("history"));

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
            Map<String, Object> message = new HashMap<>();

            message.put("type", MessageType.SEND_HIST_BERTH.getValue());
            message.put("berth_id", berthId);
            message.put("berth_descr", berth.getBerthDescription() + (!berth.getName().equals("") ? " (" + berth.getName() + ")" : ""));
            message.put("history", berth.getBerthsHistory());

            sendMessage(message);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_UPDATE">
    public void sendUpdate(Map<String, String> updateMap)
    {
        Map<String, Object> message = new HashMap<>();

        message.put("type",    MessageType.SEND_UPDATE.getValue());
        message.put("message", updateMap);

        sendMessage(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND TEXT MESSAGE">
    public void sendTextMessage(String message)
    {
        Map<String, Object> messageMap = new HashMap<>();

        messageMap.put("type",    MessageType.SEND_MESSAGE.getValue());
        messageMap.put("message", message);

        sendMessage(messageMap);
    }
    //</editor-fold>

    private void sendMessage(Object message)
    {
        try
        {
            new ObjectOutputStream(client.getOutputStream()).writeObject(message);

            errors = 0;
        }
        catch (IOException e)
        {
            errorList.add(e);
            errors++;
            addClientLog(String.valueOf(e), false);
            EastAngliaSignalMapServer.printThrowable(e, "Client " + name);
        }

        testErrors();
    }

    private void testErrors()
    {
        if (errors > 7 && !client.isClosed())
        {
            sendSocketClose("Errors: " + getErrorString());
            closeSocket();
        }
    }

    public void closeSocket()
    {
        try
        {
            client.shutdownOutput();
            //client.getOutputStream().close();
        }
        catch (IOException e) {}

        try
        {
            client.shutdownInput();
            //client.getInputStream().close();
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
            EastAngliaSignalMapServer.printThrowable(e, "Client " + name);
        }
    }

    public String getName()
    {
        return name;
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
        if (errors != 0 && !errorList.isEmpty())
        {
            String errorStr = "";
            errorStr = errorList.stream().map((t) -> t.getClass().getName() + ", ").reduce(errorStr, String::concat);

            return errorStr.substring(0, errorStr.length() - 2) + " (" + errors + ")";
        }
        else
            return "";
    }

    public List<String> getInfo()
    {
        List<String> list = new ArrayList<>();

        /*for (Map.Entry pairs : props.entrySet())
            list.add(pairs.getKey() + ": " + pairs.getValue());*/

        list.add("Java:      " + props.getProperty("java.version", ""));
        list.add("User name: " + props.getProperty("user.name", ""));
        list.add("OS:        " + props.getProperty("os.name", ""));
        list.add("Locale:    " + props.getProperty("user.language", "") + "_" + props.getProperty("user.country", ""));
        list.add("Timezone:  " + props.getProperty("user.timezone", ""));
        list.add("Command:   " + props.getProperty("sun.java.command", ""));

        return list;
    }

    private void addClientLog(String message, boolean addToGlobal)
    {
        history.add(0, "[" + EastAngliaSignalMapServer.sdfDateTimeShort.format(new Date()) + "] " + message);

        if (addToGlobal)
            Clients.addClientLog("[" + name + "/" + client.getInetAddress().getHostAddress() + "] " + message);
    }

    public List<String> getHistory()
    {
        return history;
    }
}