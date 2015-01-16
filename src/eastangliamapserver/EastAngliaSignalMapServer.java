package eastangliamapserver;

import eastangliamapserver.gui.ServerGui;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.EventQueue;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;

public class EastAngliaSignalMapServer
{
    public  static String VERSION = "7";

    public  static final int    port = 6321;
    public  static ServerSocket server;
    public  static ServerGui    gui;
    public  static boolean      stop = true;

    public  static final File storageDir = new File(System.getProperty("user.home", "C:") + File.separator + ".easigmap");

    public  static SimpleDateFormat sdf    = new SimpleDateFormat("HH:mm:ss");
    public  static SimpleDateFormat sdfLog = new SimpleDateFormat("dd-MM-YY HH.mm.ss");
    public  static File             logFile;
    private static PrintStream      logStream;

    public static String ftpBaseUrl = "";

    private static int lastUUID = 0;

    public  static Map<String, String> CClassMap = new HashMap<>();

    public  static Map<String, Map<String, Integer>> SClassMap = new HashMap<>();
    public  static DataMap SClassDataMap = new DataMap("LS","SE","SI","CC","CA","EN","WG","SO","SX");
  //public  static List<Map<String, String>> SClassLog = new ArrayList<>();

    public static void main(String[] args)
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) { printThrowable(e, "Look & Feel"); }

        logFile = new File(System.getProperty("java.io.tmpdir") + File.separator + "EastAngliaSignalMapServer" + File.separator + sdfLog.format(new Date(System.currentTimeMillis())) + " logfile.log");
        logFile.getParentFile().mkdirs();

        System.gc();
        try
        {
            logStream = new PrintStream(new FileOutputStream(logFile));
        }
        catch (FileNotFoundException e) { printErr("Could not create log file"); printThrowable(e, "Startup"); }

        sdfLog = new SimpleDateFormat("[dd/MM/YY HH:mm:ss] ");

        try
        {
            File ftpLoginFile = new File(storageDir, "Website_FTP_Login.properties");
            if (!ftpLoginFile.exists())
            {
                ftpLoginFile.getParentFile().mkdirs();
                ftpLoginFile.createNewFile();
            }

            Properties ftpLogin = new Properties();
            ftpLogin.load(new FileInputStream(ftpLoginFile));

            ftpBaseUrl  = "ftp://" + ftpLogin.getProperty("Username", "") + ":" + ftpLogin.getProperty("Password", "") + "@ftp.easignalmap.altervista.org/";
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) { printThrowable(e, "FTP Login"); }

        SignalMap.createBerthObjects();
        readSavedMap(false);
        TimerMethods.sleep(false);

        if (StompConnectionHandler.wrappedConnect())
            printServer("Stomp open", false);
        else
            printServer("Unble to start Stomp", true);

        /*try
        {
            StompConnectionHandler.connect();
        }
        catch (IOException e)
        {
            printServer("Problem connecting to NR servers\n" + e, true);
            JOptionPane.showMessageDialog(null, "Problem connecting to NR servers\n" + e, "IO Exception", JOptionPane.ERROR_MESSAGE);
            //stop();
        }
        catch (LoginException e)
        {
            printServer("Problem connecting to NR servers\n" + e, true);
            JOptionPane.showMessageDialog(null, "Problem connecting to NR servers\n" + e, "Login Exception", JOptionPane.ERROR_MESSAGE);
            //stop();
        }*/

        EventQueue.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                gui = new ServerGui();
            }
        });

        stop = false;
        try
        {
            server = new ServerSocket(port, 2);
            printServer(String.format("Opening server on %s:%s", server.getInetAddress().toString(), server.getLocalPort()), false);
        }
        catch (IOException e)
        {
            printServer("Problem creating server\n" + e, true);
            JOptionPane.showMessageDialog(null, "Problem creating server\n" + e, "IO Exception", JOptionPane.ERROR_MESSAGE);
            stop();
        }

        CommandHandler.commandInputThread.start();
        System.gc();

        while (!stop)
        {
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
            catch (SocketTimeoutException | NullPointerException e) {}
            catch (SocketException e) { printServer("Socket Exception: " + e.toString(), true); }
            catch (IOException e) { printServer("Client failed to connect: " + e.toString(), true); }
            catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "Server"); }
        }

        stop();
    }

    //<editor-fold defaultstate="collapsed" desc="Print methods">
    public static void printServer(String message, boolean toErr)
    {
        if (toErr)
            printErr("[Server] " + message);
        else
            printOut("[Server] " + message);
    }

    public static void printThrowable(Throwable t, String name)
    {
        printErr((name != null && !name.isEmpty() ? "[" + name + "] " : "") + t.toString());

        for (StackTraceElement element : t.getStackTrace())
            printErr((name != null && !name.isEmpty() ? "[" + name + "] -> " : "-> ") + element.toString());

        for (Throwable sup : t.getSuppressed())
            printThrowable0(sup, name);

        printThrowable0(t.getCause(), name);
    }

    private static void printThrowable0(Throwable t, String name)
    {
        if (t != null)
        {
            printErr((name != null && !name.isEmpty() ? "[" + name + "] " : "") + t.toString());

            for (StackTraceElement element : t.getStackTrace())
                printErr((name != null && !name.isEmpty() ? "[" + name + "] -> " : " -> ") + element.toString());
        }
    }

    public static void printOut(String message)
    {
        if (message != null && !message.equals(""))
            if (!message.contains("\n"))
                print(sdfLog.format(new Date()) + message, false);
            else
                for (String msgPart : message.split("\n"))
                    print(sdfLog.format(new Date()) + msgPart, false);
    }

    public static void printErr(String message)
    {
        if (message != null && !message.equals(""))
            if (!message.contains("\n"))
                print(sdfLog.format(new Date()) + "!!!> " + message + " <!!!", false);
            else
                for (String msgPart : message.split("\n"))
                    print(sdfLog.format(new Date()) + "!!!> " + msgPart + " <!!!", true);
    }

    private static synchronized void print(String message, boolean toErr)
    {
        if (toErr)
            System.err.println(message);
        else
            System.out.println(message);

        filePrint(message);
    }

    private static void filePrint(String message)
    {
        logStream.println(message);
    }
    //</editor-fold>

    public static void stop()
    {
        printServer("Stopping...", false);

        stop = true;
        try
        {
            StompConnectionHandler.disconnect();

            try { gui.stop(); }
            catch (NullPointerException e) {}

            Clients.closeAll();

            try { server.close(); }
            catch (IOException | NullPointerException e) {}

            printServer("Closed", false);
            saveMap();

            try { gui.dispose(); }
            catch (NullPointerException e) {}
        }
        catch (Throwable t) { printThrowable(t, "Stop"); }

        System.exit(0);
    }

    public static String getNextUUID()
    {
        String UUID;

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
        if (SignalMap.isCreated)
            try
            {
                File mapSave = new File(storageDir, "EastAngliaSigMap.save");

                if (mapSave.exists())
                {
                    ObjectInputStream in = new ObjectInputStream(new FileInputStream(mapSave));

                    Map<String, Object> savedMap = (Map<String, Object>) in.readObject();

                    Date date = (Date) savedMap.get("date-time");

                    if (date == null)
                        date = new Date(mapSave.lastModified());

                    boolean isOld = true;
                    if (date.after(new Date(System.currentTimeMillis() - 60000 * 20)) || force)
                        isOld = false;
                    else
                        printOut("Saved map file is out of date");

                    EastAngliaSignalMapServer.SClassMap.putAll((Map<String, Map<String, Integer>>) savedMap.get("SClassData"));

                    lastUUID = (int) savedMap.get("TrainHistoryUUID");

                    try {
                    Map<String, Map<String, Object>> trainHistory = new HashMap<>((Map<String, Map<String, Object>>) savedMap.get("TrainHistory"));
                    for (Map.Entry<String, Map<String, Object>> pairs : trainHistory.entrySet())
                        Berths.addTrainHistory(pairs.getKey(), pairs.getValue());
                    } catch (NullPointerException e) {}

                    Map<String, Map<String, Object>> berthMap = (Map<String, Map<String, Object>>) savedMap.get("CClassData");

                    Map<String, String> newCClassMap = new HashMap<>();
                    for (Map.Entry<String, Map<String, Object>> pairs : berthMap.entrySet())
                    {
                        Berth berth = Berths.getBerth(pairs.getKey());

                        if (!isOld)
                            newCClassMap.put(pairs.getKey(), (String) pairs.getValue().get("headcode"));

                        if (!isOld && berth != null)
                            berth.interpose(new Train((String) pairs.getValue().get("headcode"), berth));

                        if (berth != null)
                            berth.setHistory((List<String>) pairs.getValue().get("berth_hist"));
                        else
                            Berths.addMissingBerth(pairs.getKey(), (Date) pairs.getValue().get("last_modified"));
                    }

                    EastAngliaSignalMapServer.CClassMap.putAll(newCClassMap);

                    printOut("[Persistance] Read map save file");
                }
                else
                    printOut("No map save file");

            }
            catch (FileNotFoundException e) {}
            catch (ClassNotFoundException e) { EastAngliaSignalMapServer.printErr("[Persistance] Exception parsing map save file:\n" + e); }
            catch (IOException e) { EastAngliaSignalMapServer.printErr("[Persistance] Exception reading map save file:\n" + e); }
        else
            printServer("Not read save file", true);
    }

    public synchronized static void saveMap()
    {
        Map<String, Object> outMap = new HashMap<>();
        Map<String, Map<String, Object>> berthMap = new HashMap<>();

        outMap.put("date-time", new Date());

        for (Map.Entry<String, String> pairs : EastAngliaSignalMapServer.CClassMap.entrySet())
        {
            Berth berth = Berths.getBerth(pairs.getKey());

            Map<String, Object> berthDetail = new HashMap<>();
            if (berth != null)
            {
                berthDetail.put("headcode", berth.getHeadcode());
                berthDetail.put("berth_hist", berth.getBerthsHistory());
            }
            else
            {
                berthDetail.put("headcode", pairs.getValue());
                //berthDetail.put("last_modified", Berths.getMissingBerthFoundDate(pairs.getKey()));
            }

            berthMap.put(pairs.getKey(), berthDetail);
        }

        outMap.put("CClassData", berthMap);
        outMap.put("SClassData", new HashMap<>(EastAngliaSignalMapServer.SClassMap));
        outMap.put("TrainHistoryMap", Berths.getTrainHistory());
        outMap.put("TrainHistoryUUID", lastUUID);

        File out = new File(storageDir, "EastAngliaSigMap-new.save");

        try
        {
            if (!out.exists())
            {
                out.getParentFile().mkdirs();
                out.createNewFile();
            }

            try (FileOutputStream outStream = new FileOutputStream(out); ObjectOutputStream oos = new ObjectOutputStream(outStream))
            {
                oos.writeObject(outMap);
            }

            try { Files.move(out.toPath(), new File(storageDir, "EastAngliaSigMap.save").toPath(), StandardCopyOption.REPLACE_EXISTING); }
            catch (UnsupportedOperationException e) { EastAngliaSignalMapServer.printThrowable(e, "Persistance"); }

            EastAngliaSignalMapServer.printOut("[Persistance] Saved map");
        }
        catch (IOException e)
        {
            printErr("[Persistance] Failed to save map");
            EastAngliaSignalMapServer.printThrowable(e, "Persistance");

            out.delete();
        }
    }

    public static void updateIP()
    {
        //if (InetAddress.getByName("http://www.google.co.uk").isReachable(2000)
        //        || InetAddress.getByName("http://easignalmap.altervista.org").isReachable(2000)
        //       || InetAddress.getByName("http://www.copy.com").isReachable(2000))
        //{
            String externalIP = "127.0.0.1";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com/").openStream())))
            {
                externalIP = br.readLine();
            }
            catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to amazomaws"); }
            catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
            catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "IPUpdater"); }

            try
            {
                URLConnection con = new URL(ftpBaseUrl + "server.ip;type=i").openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(con.getOutputStream())))
                {
                    bw.write(externalIP);

                    EastAngliaSignalMapServer.printOut("[IPUpdater] Updated server IP to " + externalIP);
                }
                catch (ConnectException e) { EastAngliaSignalMapServer.printErr("[IPUpdater] Unable to connect to FTP server"); }
                catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
                catch (IOException e) {}
            }
            catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "IPUpdater"); }
        //}
    }
}