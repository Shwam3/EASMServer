package eastangliamapserver;

import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.JOptionPane;

public class Clients
{
    private static final ArrayList<Client> clients = new ArrayList<>();
    public  static final ArrayList<String> clientsHistory = new ArrayList<>();

    public static void addClient(Client client)
    {
        if (hasSpace())
        {
            clients.add(client);
            EastAngliaSignalMapServer.gui.updateClientList();
        }
        else
            client.disconnect("Server full");
    }

    public static ArrayList<String> getClientList()
    {
        ArrayList<String> clientList = new ArrayList<>();

        try
        {
            for (Client client : clients)
                clientList.add(client.name + " (" + client.address + ":" + client.port + ")" + client.getErrorString());
        }
        catch (Exception e) {}

        Collections.sort(clientList);
        return clientList;
    }

    public static void kickAll(String reason)
    {
        if (reason == null)
            reason = JOptionPane.showInputDialog(EastAngliaSignalMapServer.gui.frame, "Add a message to kickAll:");

        if (reason != null && !reason.equals(""))
            reason = "You have been kicked: " + reason;
        else
            reason = "You have been kicked";

        for (Client client : clients)
            client.disconnect(reason);

        addClientLog("Kicked all clients (" + reason + ")");
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

            client.disconnect("Server closed");
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
        clientsHistory.add(0, new SimpleDateFormat("[dd/MM HH:mm] ").format(new Date()) + message);
    }

    public static boolean hasSpace()
    {
        return clients.size() < 5;
    }
}