package eastangliamapserver;

import eastangliamapserver.gui.ServerGui;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.EventQueue;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.security.auth.login.LoginException;
import javax.swing.*;

public class EastAngliaSignalMapServer
{
    public  static String BUILD = "5";

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

            readSavedMap(false);

            Runtime.getRuntime().addShutdownHook(new Thread("shutdownHook")
            {
                @Override
                public void run()
                {
                    File out = new File("C:\\Users\\Shwam\\Documents\\GitHub\\EastAngliaSignalMapServer\\dist", "EastAngliaSigMap.save");

                    if (out.exists())
                        out.delete();

                    HashMap<String, HashMap<String, Object>> outMap = new HashMap();

                    HashMap<String, Object> dateMap = new HashMap();
                    dateMap.put("date-time", new Date());
                    outMap.put("date-time", dateMap);

                    for (Map.Entry pairs : EastAngliaSignalMapServer.CClassMap.entrySet())
                    {
                        Berth berth = Berths.getBerth((String) pairs.getKey());

                        if (berth != null)
                        {
                            HashMap berthDetail = new HashMap();
                            berthDetail.put("headcode", berth.getHeadcode());
                            berthDetail.put("berth_hist", berth.getBerthsHistory());

                            outMap.put((String) pairs.getKey(), berthDetail);
                        }
                        else
                        {
                            HashMap berthDetail = new HashMap();
                            berthDetail.put("headcode", (String) pairs.getValue());

                            outMap.put((String) pairs.getKey(), berthDetail);
                        }
                    }

                    try
                    {
                        FileOutputStream outStream = new FileOutputStream(out);
                        ObjectOutputStream oos = new ObjectOutputStream(outStream);

                        oos.writeObject(outMap);

                        oos.close();
                        outStream.close();

                        EastAngliaSignalMapServer.printOut("[Persistance] Saved map");
                    }
                    catch (IOException e)
                    {
                        printErr("[Persistance] Failed to save map\n" + e);
                    }
                }
            });

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

    public static void readSavedMap(boolean force)
    {
        try
        {
            File mapSave = new File("C:\\Users\\Shwam\\Documents\\GitHub\\EastAngliaSignalMapServer\\dist", "EastAngliaSigMap.save");

            if (mapSave.exists())
            {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(mapSave));

                HashMap<String, Object> hm = (HashMap<String, Object>) in.readObject();

                HashMap<String, Object> hm2 = ((HashMap<String, Object>) hm.get("date-time"));
                Date date = (Date) hm2.get("date-time");

                if (date == null)
                    date = new Date(mapSave.lastModified());

                if (date.after(new Date(System.currentTimeMillis() - 60000 * 20)) || force)
                {
                    HashMap<String, String> newCClassMap = new HashMap<>();
                    for (Map.Entry pairs : hm.entrySet())
                    {
                        newCClassMap.put((String) pairs.getKey(), (String) ((HashMap<String, Object>) pairs.getValue()).get("headcode"));

                        Berth berth = Berths.getBerth((String) pairs.getKey());
                        if (berth != null)
                            berth.setHistory((List<String>) ((HashMap<String, Object>) pairs.getValue()).get("berth_hist"));
                    }

                    System.currentTimeMillis();

                    EastAngliaSignalMapServer.CClassMap.putAll(newCClassMap);

                    printOut("[Persistance] Read map save file");
                }
                else
                    printOut("Saved map file is out of date");
            }
            else
                printOut("No map save file");

        }
        catch (FileNotFoundException e) {}
        catch (ClassNotFoundException e) { EastAngliaSignalMapServer.printErr("[Persistance] Exception parsing map save file:\n" + e); }
        catch (IOException e) { EastAngliaSignalMapServer.printErr("[Persistance] Exception reading map save file:\n" + e); }
    }
}