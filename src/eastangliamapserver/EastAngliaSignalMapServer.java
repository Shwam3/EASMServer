package eastangliamapserver;

import eastangliamapserver.gui.ServerGui;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.EventQueue;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import javax.security.auth.login.LoginException;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class EastAngliaSignalMapServer
{
    public  static int BUILD = 3;

    public  static final int    port = 6321;
            static ServerSocket server;
    public  static ServerGui    gui;
    public  static boolean      stop = true;

    public  static SimpleDateFormat sdf    = new SimpleDateFormat("HH:mm:ss");
    public  static SimpleDateFormat sdfLog = new SimpleDateFormat("dd-MM-YY HH.mm.ss");
    public  static File             logFile;
    private static PrintStream      logStream;

    private static int lastUUID = 0;

    public  static HashMap<String, String>                   CClassMap = new HashMap<>();
    public  static HashMap<String, HashMap<String, Integer>> SClassMap = new HashMap<>();

    public static void main(String[] args)
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) { e.printStackTrace(); }

        logFile = new File(System.getProperty("java.io.tmpdir") + "\\EastAngliaSignalMapServer\\" + sdfLog.format(new Date(System.currentTimeMillis())) + " logfile.log");
        logFile.getParentFile().mkdirs();

        try
        {
            logStream = new PrintStream(new FileOutputStream(logFile));
        }
        catch (FileNotFoundException ex) { printErr("Could not create log file\n" + ex); }

        sdfLog = new SimpleDateFormat("[dd/MM/YY HH:mm:ss] ");

        try
        {
            TimerMethods.sleep(false);

            StompConnectionHandler.connect();

            EventQueue.invokeLater(new Runnable()
            {
                @Override
                public void run()
                {
                    gui = new ServerGui();
                }
            });
        }
        catch (IOException e)
        {
            printServer("Problem connecting to NR servers\n" + e, true);
            JOptionPane.showMessageDialog(null, "Problem connecting to NR servers\n" + e, "IO Exception", JOptionPane.ERROR_MESSAGE);
            close();
        }
        catch (LoginException e)
        {
            printServer("Problem connecting to NR servers\n" + e, true);
            JOptionPane.showMessageDialog(null, "Problem connecting to NR servers\n" + e, "Login Exception", JOptionPane.ERROR_MESSAGE);
            close();
        }

        stop = false;

        while (!stop)
        {
            if (Clients.getClientList().size() >= 5)
            {
                if (!server.isClosed())
                    try
                    {
                        printServer("Server full, closing until space opens", false);
                        server.close();
                    }
                    catch (IOException e) {}

                continue;
            }
            else
            {
                if (server == null || server.isClosed())
                    try
                    {
                        server = new ServerSocket(port, 2);
                        printServer(String.format("Opening server on %s:%s", server.getInetAddress().toString(), server.getLocalPort()), false);
                    }
                    catch (IOException e)
                    {
                        printServer("Problem creating server\n" + e, true);
                        JOptionPane.showMessageDialog(null, "Problem creating server\n" + e, "IO Exception", JOptionPane.ERROR_MESSAGE);
                        close();
                    }
            }

            try
            {
                Socket clientSocket = EastAngliaSignalMapServer.server.accept();

                if (clientSocket != null && !Clients.hasClient(clientSocket))
                {
                    Client client = new Client(clientSocket);
                    Clients.addClient(client);

                    client.sendAll();
                }
            }
            catch (SocketTimeoutException e) {}
            catch (SocketException e) { printServer("Socket Exception:\n" + e, true); }
            catch (IOException e) { printServer("Client failed to connect\n" + e, true); }
        }

        close();
    }

    //<editor-fold defaultstate="collapsed" desc="Print methods">
    public static void printServer(String message, boolean toErr)
    {
        if (toErr)
            printErr("[Server] " + message);
        else
            printOut("[Server] " + message);
    }

    public static void printOut(String message)
    {
        //synchronized (logLock)
        //{
            String output = sdfLog.format(new Date()) + message;
            System.out.println(output);
            filePrint(output);
        //}
    }

    public static void printErr(String message)
    {
        //synchronized (logLock)
        //{
            String output = sdfLog.format(new Date()) + " !!!> " + message + " <!!!";
            System.err.println(output);
            filePrint(output);
        //}
    }

    private static void filePrint(String message)
    {
        logStream.println(message);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Exit methods">
    public static void stop()
    {
        printServer("Stopping...", false);
        stop = true;

        try { gui.stop(); }
        catch (NullPointerException e) {}

        Clients.closeAll();

        try { server.close(); }
        catch (IOException | NullPointerException e) {}
    }

    private static void close()
    {
        try { gui.stop(); }
        catch (NullPointerException e) {}

        try { server.close(); }
        catch (IOException | NullPointerException e) {}

        try { StompConnectionHandler.client.disconnect(); }
        catch (NullPointerException e) {}

        printServer("Closed", false);

        try { Thread.sleep(1000); }
        catch (InterruptedException e) {}

        try { gui.dispose(); }
        catch (NullPointerException e) {}

        System.exit(0);
    }
    //</editor-fold>

    public static String getNextUUID()
    {
        String UUID = "";

        lastUUID += 1;
        UUID = String.valueOf(lastUUID);

        while (UUID.length() < 5)
        {
            UUID = "0" + UUID;
        }
        return UUID.substring(0, 5);
    }
}