package eastangliamapserver.server;

import eastangliamapserver.EastAngliaSignalMapServer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;

public class Clients
{
    private static final List<Client> clients = new ArrayList<>();
    public  static final List<String> clientsHistory = new ArrayList<>();

    private static final Map<String, String> queuedUpdates = new HashMap<>();

    public static void addClient(Client client)
    {
        if (hasSpace())
        {
            clients.add(client);
            EastAngliaSignalMapServer.updateServerGUIs();
        }
        else
            client.disconnect("Server full");
    }

    public static List<String> getClientList()
    {
        List<String> clientList = new ArrayList<>();

        try
        {
            clients.stream().forEach(client -> clientList.add(client.getName()
                    + " (" + client.getSocket().getInetAddress().getHostAddress()
                    + ":" + client.getSocket().getPort() + ")"
                    + client.getErrorString()));
        }
        catch (Exception e) {}

        Collections.sort(clientList);
        return clientList;
    }

    public static void kickAll(String reason)
    {
        if (reason == null)
            reason = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Add a reason:");

        if (reason != null && !reason.equals(""))
            reason = "You have been kicked: " + reason;
        else
            reason = "You have been kicked";

        final String finReason = reason;
        clients.parallelStream().forEach(client -> client.disconnect(finReason));

        addClientLog("Kicked all clients (" + reason + ")");
    }

    public static boolean hasClient(Socket clientSocket)
    {
        return clients.stream().anyMatch(client -> clientSocket.equals(client.getSocket()));
    }

    public static void scheduleForNextUpdate(String id, String headcode)
    {
        queuedUpdates.put(id, headcode);
    }

    public static void broadcastUpdate(final Map<String, String> updateMap)
    {
        if (!EastAngliaSignalMapServer.stop)
        {
            if (!queuedUpdates.isEmpty())
            {
                EastAngliaSignalMapServer.printOut("[Stomp] " + queuedUpdates.size() + " queued updates");

                updateMap.putAll(queuedUpdates);
                queuedUpdates.clear();
            }
            clients.stream().filter(c -> c != null).forEach(client -> client.sendUpdate(updateMap));
        }
    }

    public static void sendAll()
    {
        if (!EastAngliaSignalMapServer.stop)
            clients.stream().filter(c -> c != null).forEach(client -> client.sendAll());
    }

    public static void remove(Client client)
    {
        if (!EastAngliaSignalMapServer.stop)
        {
            try { clients.remove(client); }
            catch (Exception e) {}

            EastAngliaSignalMapServer.updateServerGUIs();
        }
    }

    public static void closeAll()
    {
        clients.stream().filter(c -> c != null).forEach(client ->
        {
            client.printClient("Closing connection", false);
            client.disconnect("Server closed");
        });

        addClientLog("Closed all clients");
    }

    public static Client getClient(String name)
    {
        return clients.stream().filter(c -> c != null)
                .filter(client -> client.getName().equals(name) ||
                        client.getName().equals(name) ||
                        client.getName().equals(name) ||
                        name.equals(client.getSocket().getInetAddress().getHostAddress() + ":" + client.getSocket().getPort()))
                .findFirst().orElse(null);
    }

    public static boolean hasMultiple(String name)
    {
        return clients.stream().filter(c -> c != null).filter(client -> client.getName().equals(name)).count() > 1L;
    }

    public static void addClientLog(String message)
    {
        clientsHistory.add(0, "[" + EastAngliaSignalMapServer.sdfDateTimeShort.format(new Date()) + "] " + message);
    }

    public static boolean hasSpace()
    {
        return clients.size() < 5;
    }
}