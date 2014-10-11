package eastangliamapserver;

import java.net.Socket;
import java.util.*;

public class Clients
{
    private static final List<Client> clients = new ArrayList<>();

    public static void addClient(Client client)
    {
        clients.add(client);

        SocketServer.gui.updateClientList();
    }

    public static List<String> getClientList()
    {
        List<String> clientList = new ArrayList<>();

        for (Client client : clients)
            clientList.add(client.name + " (" + client.address + ":" + client.port + ")" + client.getErrorString());

        Collections.sort(clientList);
        return clientList;
    }

    public static void kickAll()
    {
        for (Client client : clients.toArray(new Client[0]))
            client.disconnect();
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
        if (!SocketServer.stop)
            for (Client client : clients)
                client.sendUpdate(update);
    }

    public static void remove(Client client)
    {
        if (!SocketServer.stop)
        {
            try { clients.remove(client); }
            catch (Exception e) {}

            SocketServer.gui.updateClientList();
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
}