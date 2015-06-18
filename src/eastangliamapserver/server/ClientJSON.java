package eastangliamapserver.server;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.MessageType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import jsonparser.JSONParser;

public class ClientJSON implements Runnable, Client
{
    private final BufferedWriter out;
    private final BufferedReader in;
    private final Socket client;
  //private String address = "0.0.0.0";
  //private String port    = "";
    private String name    = "Unnamed";

    private Map<String, String> properties = new HashMap<>();

    private boolean         stop   = false;
    private int             errors = 0;
    private List<Throwable> errorList = new ArrayList<>();

    private List<String> history = new ArrayList<>();

    private long  lastMessageTime = System.currentTimeMillis();
    private long  timeoutTime     = 30000;
    private       Timer timeoutTimer;

    public ClientJSON(Socket client) throws IOException
    {
        this.client = client;

        out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
        in  = new BufferedReader(new InputStreamReader(client.getInputStream()));

        String address = client.getInetAddress().getHostAddress();

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
                String jsonMessage = in.readLine();

                if (jsonMessage != null && !jsonMessage.isEmpty())
                {
                    Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) JSONParser.parseJSON(jsonMessage)).get("Message");
                    MessageType type = MessageType.getType(String.valueOf(message.get("type")));

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

                            properties = (message.get("props") == null ? new HashMap<>() : (Map<String, String>) message.get("props"));

                            EastAngliaSignalMapServer.updateServerGUIs();
                            break;

                        case SEND_MESSAGE:
                            printClient((String) message.get("message"), false);
                            addClientLog((String) message.get("message"), true);
                            break;
                    }

                    errors = 0;
                }
            }
            catch (IOException  e) { errorList.add(e); errors++; addClientLog(String.valueOf(e), false); }
            finally { testErrors(); }
        }

        if (stop)
            printClient("Disconnected", false);
        else
            printClient("Disconnected erroneously (" + (errorList.isEmpty() ? "None" : errorList.get(errorList.size()-1).toString()) + ")", true);

        closeSocket();
    }

    //<editor-fold defaultstate="collapsed" desc="SOCKET_CLOSE">
    public void sendSocketClose(String reason)
    {
        printClient("Socket closed sent (" + reason + ")", false);
        /*Map<String, Object> message = new HashMap<>();

        message.put("type", MessageType.SOCKET_CLOSE.getName());
        if (reason != null && !reason.equals(""))
            message.put("reason", reason);*/

        StringBuilder sb = new StringBuilder("{\"Message\":{");
        sb.append("\"type\":\"").append(MessageType.SOCKET_CLOSE.getName()).append("\",");
        sb.append("\"reason\":\"").append(reason != null && !reason.isEmpty() ? reason : "").append("\"");
        sb.append("}}");

        try
        {
            out.write(sb.toString());
            out.write("\r\n");
            out.flush();

            errors = 0;
        }
        catch (IOException e) {}
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REQUEST">
    public void sendHeartbeatRequest()
    {
        sendMessage("{\"Message\":{\"type\":\"" + MessageType.HEARTBEAT_REQUEST.getName() + "\"}}");
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="HEARTBEAT_REPLY">
    public void sendHeartbeatReply()
    {
        sendMessage("{\"Message\":{\"type\":\"" + MessageType.HEARTBEAT_REPLY.getName() + "\"}}");
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_ALL">
    public void sendAll()
    {
        StringBuilder sb = new StringBuilder("{\"Message\":{");
        sb.append("\"type\":\"").append(MessageType.SEND_ALL.getName()).append("\",");
        sb.append("\"message\":{");

        EastAngliaSignalMapServer.CClassMap.entrySet().stream()
                .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

        EastAngliaSignalMapServer.SClassMap.entrySet().stream()
                .filter(p -> p.getKey().contains(":"))
                .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

        if (sb.charAt(sb.length()-1) == ',')
            sb.deleteCharAt(sb.length()-1);
        sb.append("}}}");

        sendMessage(sb.toString());
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_HIST_TRAIN">
    public void sendHistoryOfTrain(String id)
    {
        Berth berth = Berths.getBerth(id);

        if (berth != null)
        {
            StringBuilder sb = new StringBuilder("{\"Message\":{");
            sb.append("\"type\":\"").append(MessageType.SEND_HIST_TRAIN.getName()).append("\",");
            sb.append("\"id\":\"").append(id).append("\",");
            sb.append("\"berth_id\":\"").append(id).append("\",");
            sb.append("\"headcode\":\"").append(berth.getHeadcode()).append("\",");

            sb.append("\"history\":[");
            List<String> hist = berth.getTrainsHistory();
            for (int i = 0; i < hist.size(); i++)
                sb.append("\"").append(hist.get(i)).append("\"").append(i >= hist.size()-1 ? "" : ",");
            sb.append("]");

            sb.append("}}");
            sendMessage(sb.toString());
        }
        else
        {

            StringBuilder sb = new StringBuilder("{\"Message\":{");
            sb.append("\"type\":\"").append(MessageType.SEND_HIST_TRAIN.getName()).append("\",");
            sb.append("\"id\":\"").append(id).append("\",");

            Map<String, Object> train = Berths.getTrain(id);
            sb.append("\"berth_id\":\"").append(train == null ? "" : (String) train.get("berth_id")).append("\",");
            sb.append("\"headcode\":\"").append(train == null ? "" : (String) train.get("headcode")).append("\",");

            sb.append("\"history\":[");
            if (train != null)
            {
                List<String> hist = (List<String>) train.get("history");
                for (int i = 0; i < hist.size(); i++)
                    sb.append("\"").append(hist.get(i)).append("\"").append(i >= hist.size()-1 ? "" : ",");
            }
            sb.append("]");

            sb.append("}}");
            sendMessage(sb.toString());
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_HIST_BERTH">
    public void sendHistoryOfBerth(String berthId)
    {
        StringBuilder sb = new StringBuilder("{\"Message\":{");
        Berth berth = Berths.getBerth(berthId);

        sb.append("\"type\":\"").append(MessageType.SEND_HIST_BERTH.getName()).append("\",");
        sb.append("\"berth_id\":\"").append(berthId).append("\"");

        if (berth != null)
        {
            sb.append(",");
            sb.append("\"berth_descr\":\"").append(berth.getBerthDescription());
            if (!berth.getName().isEmpty())
                sb.append(" (").append(berth.getName()).append(")");
            sb.append("\",");

            sb.append("\"history\":[");
            List<String> hist = berth.getBerthsHistory();
            for (int i = 0; i < hist.size(); i++)
                sb.append("\"").append(hist.get(i)).append("\"").append(i >= hist.size()-1 ? "" : ",");
            sb.append("]");
        }
        else
            sb.append(",\"berth_descr\":\"Unknown berth\",\"history\":[]");

        sb.append("}}");
        sendMessage(sb.toString());
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND_UPDATE">
    public void sendUpdate(Map<String, String> updateMap)
    {
        StringBuilder sb = new StringBuilder("{\"Message\":{");
        sb.append("\"type\":\"").append(MessageType.SEND_UPDATE.getName()).append("\",");
        sb.append("\"message\":{");
        updateMap.entrySet().stream().forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));
        if (sb.charAt(sb.length()-1) == ',')
            sb.deleteCharAt(sb.length()-1);
        sb.append("}}}");

        sendMessage(sb.toString());
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="SEND TEXT MESSAGE">
    public void sendTextMessage(String message)
    {
        sendMessage("{\"Message\":{\"type\":\"" + MessageType.SEND_MESSAGE.getName() + "\","
                + "\"message\":\"" + message.replace("\"", "\\\"") + "\"}}");
    }
    //</editor-fold>

    private void sendMessage(String message)
    {
        try
        {
            out.write(message);
            out.write("\r\n");
            out.flush();

            errors = 0;
        }
        catch (SocketException e) { errors++; }
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
        try { client.shutdownOutput(); }
        catch (IOException e) {}

        try { client.shutdownInput(); }
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
                        }
                        else
                        {
                            printClient("Connection timed out", true);
                            addClientLog("Timed out (" + (System.currentTimeMillis() - lastMessageTime) + ")", true);
                            sendSocketClose("Timed Out");
                            closeSocket();
                            timeoutTime = 30000;
                        }
                    }
                    else
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
            errorStr = errorList.stream().map(t -> t.toString() + ", ").reduce(errorStr, String::concat);

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

        list.add("Java:      " + properties.getOrDefault("java.version", ""));
        list.add("User name: " + properties.getOrDefault("user.name", ""));
        list.add("OS:        " + properties.getOrDefault("os.name", ""));
        list.add("Locale:    " + properties.getOrDefault("user.language", "") + "_" + properties.getOrDefault("user.country", ""));
        list.add("Timezone:  " + properties.getOrDefault("user.timezone", ""));
        list.add("Command:   " + properties.getOrDefault("sun.java.command", ""));

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