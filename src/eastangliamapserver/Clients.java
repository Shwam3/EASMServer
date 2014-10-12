package eastangliamapserver;

import java.net.Socket;
import java.util.*;

public class Clients
{
    private static final List<Client> clients = new ArrayList<>();

    public static final List<String> clientsHistory = new ArrayList<>();

    public static void addClient(Client client)
    {
        clients.add(client);
        addClientLog(String.format("Client at %s:%s joined", client.address, client.port + (client.name != null ? " (" + client.name + ")" : "")));

        EastAngliaSignalMapServer.gui.updateClientList();
    }

    public static List<String> getClientList()
    {
        List<String> clientList = new ArrayList<>();

        try
        {
            for (Client client : clients)
                clientList.add(client.name + " (" + client.address + ":" + client.port + ")" + client.getErrorString());
        }
        catch (Exception e) {}

        Collections.sort(clientList);
        return clientList;
    }

    public static void kickAll()
    {
        for (Client client : clients.toArray(new Client[0]))
            client.disconnect();

        addClientLog("Kicked all clients");
    }

    public static boolean hasClient(Socket clientSocket)
    {
        for (Client client : clients)
            if (client.getSocket() == clientSocket)
                return true;

        return false;
    }

    public static void broadcastUpdate(Map update)
    {
        if (!EastAngliaSignalMapServer.stop)
            for (Client client : clients)
                client.sendUpdate(update);
    }

    public static void remove(Client client)
    {
        if (!EastAngliaSignalMapServer.stop)
        {
            try { clients.remove(client); }
            catch (Exception e) {}

            EastAngliaSignalMapServer.gui.updateClientList();
        }
    }

    public static void closeAll()
    {
        for (Client client : clients.toArray(new Client[0]))
        {
            client.printClient("Closing connection", false);

            client.sendSocketClose();
            client.closeSocket();
        }

        addClientLog("Closed all clients");
    }

    public static Client getClient(String name)
    {
        for (Client client : clients)
            if (client.name.equals(name) || name.equals(client.address + ":" + client.port))
                return client;

        return null;
    }

    public static boolean hasMultiple(String name)
    {
        int clientsByName = 0;

        for (Client client : clients)
            if (client.name.equals(name))
                clientsByName++;

        return clientsByName > 1;
    }

    public static void addClientLog(String message)
    {
        clientsHistory.add(EastAngliaSignalMapServer.sdfLog.format(new Date()) + message);
    }
}