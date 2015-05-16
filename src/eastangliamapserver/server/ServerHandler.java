package eastangliamapserver.server;

import eastangliamapserver.EastAngliaSignalMapServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import javax.swing.JOptionPane;

public class ServerHandler
{
    private static ServerSocket serverOld;
    private static ServerSocket serverJSON;

    private static Thread threadServerJSON;
    private static Thread threadServerOld;

    private static boolean stop = true;

    public static void init()
    {
        if (!EastAngliaSignalMapServer.serverOffline)
        {
            try
            {
                serverOld  = new ServerSocket(6321, 5);
                serverJSON = new ServerSocket(6323, 5);
                printServer(String.format("Opening server on %s:%s and %s:%s",
                        serverJSON.getInetAddress().toString(), serverJSON.getLocalPort(),
                        serverOld.getInetAddress().toString(), serverOld.getLocalPort()), false);

                setupThreads();

                stop = false;

                threadServerOld.start();
                threadServerJSON.start();
            }
            catch (IOException e)
            {
                printServer("Problem creating server", true);
                EastAngliaSignalMapServer.printThrowable(e, "Server");
                JOptionPane.showMessageDialog(null, "Problem creating server: " + e.toString(), "IO Exception", JOptionPane.ERROR_MESSAGE);
            }

            EastAngliaSignalMapServer.updateIP();
        }
        else
            printServer("Not starting server, in offline mode (offline: "+EastAngliaSignalMapServer.serverOffline+")", false);
    }

    public static void stop()
    {
        stop = true;
        threadServerOld.interrupt();
        threadServerJSON.interrupt();
    }

    private static void setupThreads()
    {
        threadServerOld = new Thread(() ->
        {
            while (!stop || Thread.currentThread().isInterrupted())
            {
                if (!EastAngliaSignalMapServer.serverOffline)
                {
                    try
                    {
                        Socket clientSocket = serverOld.accept();

                        if (clientSocket != null && !Clients.hasClient(clientSocket))
                        {
                            Client client = new ClientOld(clientSocket);
                            Clients.addClient(client);

                            try { Thread.sleep(10); }
                            catch (InterruptedException e) {}

                            client.sendAll();
                        }
                    }
                    catch (SocketTimeoutException | NullPointerException e) {}
                    catch (SocketException e) {printServer("Socket Exception: " + e.toString(), true); }
                    catch (IOException e) { printServer("Client failed to connect: " + e.toString(), true); }
                    catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "Server"); }
                }
                else
                {
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) {}
                }
            }
        }, "Server-Old");
        threadServerJSON = new Thread(() ->
        {
            while (!stop)
            {
                if (!EastAngliaSignalMapServer.serverOffline)
                {
                    try
                    {
                        Socket clientSocket = serverJSON.accept();

                        if (clientSocket != null && !Clients.hasClient(clientSocket))
                        {
                            Client client = new ClientJSON(clientSocket);
                            Clients.addClient(client);

                            try { Thread.sleep(10); }
                            catch (InterruptedException e) {}

                            client.sendAll();
                        }
                    }
                    catch (SocketTimeoutException | NullPointerException e) {}
                    catch (SocketException e) {printServer("Socket Exception: " + e.toString(), true); }
                    catch (IOException e) { printServer("Client failed to connect: " + e.toString(), true); }
                    catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "Server"); }
                }
                else
                {
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) {}
                }
            }
        }, "Server-JSON");
    }

    public static void printServer(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Server] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Server] " + message);
    }
}