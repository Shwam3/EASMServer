package eastangliamapserver.server;

import eastangliamapserver.EastAngliaSignalMapServer;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public interface Client
{
    public abstract void sendSocketClose(String reason);
    public abstract void sendHeartbeatRequest();
    public abstract void sendHeartbeatReply();
    public abstract void sendAll();
    public abstract void sendHistoryOfTrain(String id);
    public abstract void sendHistoryOfBerth(String berthId);
    public abstract void sendUpdate(Map<String, String> updateMap);
    public abstract void sendTextMessage(String message);

    public abstract String getName();
    public abstract Socket getSocket();
    public abstract String getErrorString();
    public abstract List<String> getInfo();
    public abstract List<String> getHistory();
    public abstract void disconnect(String reason);

    default public void printClient(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Client-" + getName() + "] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Client-" + getName() + "] " + message);
    }
}