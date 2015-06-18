package eastangliamapserver;

import eastangliamapserver.gui.DataGui;
import eastangliamapserver.gui.ServerGui;
import eastangliamapserver.server.Clients;
import eastangliamapserver.server.ServerHandler;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.ConnectException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import jsonparser.JSONParser;

public class EastAngliaSignalMapServer
{
    public  static String VERSION = "9";

    public  static final int    port = 6321;
    public  static ServerSocket server;
    public  static ServerGui    guiServer;
    public  static DataGui      guiData;
    public  static boolean      stop = true;

    public  static boolean stompOffline  = false;
    public  static boolean serverOffline = false;
    public  static boolean disablePersistence = false;
    public  static boolean disableFileLog = false;

    public  static final File storageDir = new File(System.getProperty("user.home", "C:") + File.separator + ".easigmap");

    public static SimpleDateFormat sdfTime          = new SimpleDateFormat("HH:mm:ss");
    public static SimpleDateFormat sdfDate          = new SimpleDateFormat("dd/MM/YY");
    public static SimpleDateFormat sdfDateTime      = new SimpleDateFormat("dd/MM/YY HH:mm:ss");
    public static SimpleDateFormat sdfDateTimeShort = new SimpleDateFormat("dd/MM HH:mm:ss");

    public  static File         logFile;
    private static PrintStream  logStream;
    private static String       lastLogDate = "";

    public static String ftpBaseUrl = "";

    private static final AtomicLong lastUUID = new AtomicLong(0);

    public  static Map<String, String> CClassMap = new HashMap<>();
    public  static Map<String, String> SClassMap = new HashMap<>();

    public static void main(String[] args)
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) { printThrowable(e, "Look & Feel"); }

        Date logDate = new Date();
        logFile = new File(storageDir, "Logs" + File.separator + "EastAngliaSignalMapServer" + File.separator + sdfDate.format(logDate).replace("/", "-") + ".log");
        logFile.getParentFile().mkdirs();
        lastLogDate = sdfDate.format(logDate);

        try { logStream = new PrintStream(new FileOutputStream(logFile, logFile.length() > 0)); }
        catch (FileNotFoundException e) { printErr("Could not create log file"); printThrowable(e, "Startup"); }

        String argString = Arrays.deepToString(args).toLowerCase();
        System.setProperty("args", argString);
        stompOffline       = argString.contains("-stompoffline")  || argString.contains("-offline");
        serverOffline      = argString.contains("-serveroffline") || argString.contains("-offline");
        disablePersistence = argString.contains("-disablepersistence");
        disableFileLog     = argString.contains("-disablefilelog");

        CommandHandler.commandInputThread.start();

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

            ftpBaseUrl = "ftp://" + ftpLogin.getProperty("Username", "") + ":" + ftpLogin.getProperty("Password", "") + "@ftp.easignalmap.altervista.org/";
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) { printThrowable(e, "FTP Login"); }

        try
        {
            EventQueue.invokeAndWait(() ->
            {
                guiServer = new ServerGui();
                guiData   = new DataGui();
            });
        }
        catch (InvocationTargetException | InterruptedException e) { printThrowable(e, "Startup"); }

        SysTrayHandler.initSysTray();
        SignalMap.createBerthObjects();
        readSavedMap(false);
        TimerMethods.sleep(false);

        if (!EastAngliaSignalMapServer.stompOffline)
        {
            if (StompConnectionHandler.wrappedConnect())
                printMain("Stomp open", false);
            else
                printMain("Unble to start Stomp", true);
        }
        else
            printMain("Not starting stomp, in offline mode (offline: "+stompOffline+")", false);

        ServerHandler.init();

        stop = false;
    }

    //<editor-fold defaultstate="collapsed" desc="Print methods">
    public static void printMain(String message, boolean toErr)
    {
        if (toErr)
            printErr("[Main] " + message);
        else
            printOut("[Main] " + message);
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
                print("[" + sdfDateTime.format(new Date()) + "] " + message, false);
            else
                for (String msgPart : message.trim().split("\n"))
                    print("[" + sdfDateTime.format(new Date()) + "] " + msgPart, false);
    }

    public static void printErr(String message)
    {
        if (message != null && !message.equals(""))
            if (!message.contains("\n"))
                print("[" + sdfDateTime.format(new Date()) + "] !!!> " + message + " <!!!", false);
            else
                for (String msgPart : message.trim().split("\n"))
                    print("[" + sdfDateTime.format(new Date()) + "] !!!> " + msgPart + " <!!!", true);
    }

    private static synchronized void print(String message, boolean toErr)
    {
        if (toErr)
            System.err.println(message);
        else if ((!message.contains("[C-Class] ") && !message.contains("[S-Class] ")) || message.contains("!!>") || disableFileLog)
            System.out.println(message);

        filePrint(message);
    }

    private static synchronized void filePrint(String message)
    {
        if (!disableFileLog)
        {
            Date logDate = new Date();
            if (!lastLogDate.equals(sdfDate.format(logDate)))
            {
                logStream.flush();
                logStream.close();

                lastLogDate = sdfDate.format(logDate);

                logFile = new File(storageDir, "Logs" + File.separator + "EastAngliaSignalMapServer" + File.separator + lastLogDate.replace("/", "-") + ".log");
                logFile.getParentFile().mkdirs();

                try
                {
                    logFile.createNewFile();
                    logStream = new PrintStream(new FileOutputStream(logFile, true));
                }
                catch (IOException e) { printErr("Could not create log file"); printThrowable(e, "Logging"); }
            }

            logStream.println(message);
        }
    }
    //</editor-fold>

    public static synchronized void stop()
    {
        printMain("Stopping...", false);

        try
        {
            boolean wasStompOffline = EastAngliaSignalMapServer.stompOffline;
            EastAngliaSignalMapServer.stompOffline  = true;
            EastAngliaSignalMapServer.serverOffline = true;

            ServerHandler.stop();
            StompConnectionHandler.disconnect();

            try { guiServer.stop(); }
            catch (NullPointerException e) {}

            Clients.closeAll();

            try { server.close(); }
            catch (IOException | NullPointerException e) {}

            printMain("Closed", false);

            if (!wasStompOffline)
                saveMap();

            try { guiServer.dispose(); }
            catch (NullPointerException e) {}
        }
        catch (Exception e) { printThrowable(e, "Stop"); }

        System.exit(0);
    }

    public static synchronized String getNextUUID()
    {
        String UUID;

        UUID = String.valueOf(lastUUID.incrementAndGet());

        while (UUID.length() < 5)
            UUID = "0" + UUID;

        return UUID;
    }

    public static void readSavedMap(boolean force)
    {
        if (!disablePersistence || force)
        {
            long start = System.nanoTime();
            if (SignalMap.isCreated)
            {
                //<editor-fold defaultstate="collapsed" desc="Object Input Stream">
                /*
                try
                {
                    File mapSave = new File(storageDir, "EastAngliaSigMap.save");

                    if (mapSave.exists())
                    {
                        printOut("[Persistence] Reading map save file, may take some time");

                        Map<String, Object> savedMap = null;

                        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(mapSave))))
                        {
                            savedMap = (Map<String, Object>) ois.readObject();
                        }

                        if (savedMap != null)
                        {
                            Date date = (Date) savedMap.get("date-time");

                            if (date == null)
                                date = new Date(mapSave.lastModified());

                            boolean isOld = true;
                            if (date.after(new Date(System.currentTimeMillis() - 60000 * 20)) || force)
                                isOld = false;
                            else
                                printOut("Saved map file is out of date");

                            EastAngliaSignalMapServer.SClassMap.putAll((Map<String, String>) savedMap.get("SClassData"));

                            lastUUID.set(savedMap.get("TrainHistoryUUID") instanceof Integer ? (int) savedMap.get("TrainHistoryUUID") : lastUUID.get());

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

                            printOut("[Persistence] Read map save file");
                        }
                        else
                            printOut("[Persistence] Unable to read map save file (oos returned null)");
                    }
                    else
                        printOut("[Persistence] No map save file");

                }
                catch (ClassNotFoundException e) { EastAngliaSignalMapServer.printErr("[Persistence] Exception parsing map save file:\n" + e); }
                catch (IOException e) { EastAngliaSignalMapServer.printErr("[Persistence] Exception reading map save file:\n" + e); }
                catch (Exception e) {}
                */
                //</editor-fold>

                //<editor-fold defaultstate="collapsed" desc="JSON Files">
                try
                {
                    File jsonFileCClass = new File(storageDir, "EastAngliaSigMap-CClass.json");
                    if (jsonFileCClass.exists())
                    {
                        StringBuilder jsonStringCClass = new StringBuilder();

                        try (BufferedReader br = new BufferedReader(new FileReader(jsonFileCClass)))
                        {
                            String line;
                            while ((line = br.readLine()) != null)
                                jsonStringCClass.append(line);
                        }
                        catch (IOException e) { printThrowable(e, "Persistence"); }
                        jsonStringCClass.trimToSize();

                        Map<String, Object> jsonCClass = JSONParser.parseJSON(jsonStringCClass.toString());

                        boolean isOld = !(force || (((long) jsonCClass.get("Timestamp")) > (System.currentTimeMillis() - 60000 * 20)));
                        if (isOld)
                            printOut("C-Class JSON file is out of date");

                        Map<String, Map<String, Object>> berthMap = (Map<String, Map<String, Object>>) jsonCClass.get("CClassData");
                        Map<String, String> newCClassMap = new HashMap<>();
                        for (Map.Entry<String, Map<String, Object>> pairs : berthMap.entrySet())
                        {
                            Berth berth = Berths.getBerth(pairs.getKey());

                            if (!pairs.getValue().get("headcode").equals(""))
                            {
                                if (!isOld)
                                    newCClassMap.put(pairs.getKey(), (String) pairs.getValue().get("headcode"));

                                if (!isOld && berth != null)
                                    berth.interpose(new Train((String) pairs.getValue().get("headcode"), berth));
                            }

                            if (berth != null && pairs.getValue().get("berth_hist") != null)
                                berth.setHistory((List<String>) pairs.getValue().get("berth_hist"));

                            if (pairs.getValue().get("last_modified") == null)
                                Berths.addMissingBerth(pairs.getKey(), new Date());
                            else
                                Berths.addMissingBerth(pairs.getKey(), new Date((long) pairs.getValue().get("last_modified")));
                        }

                        EastAngliaSignalMapServer.CClassMap.putAll(newCClassMap);

                        printOut("[Persistence] Read C-Class data (" + newCClassMap.size() + " objects)");
                    }

                    long mid1 = System.nanoTime();
                    File jsonFileSClass = new File(storageDir, "EastAngliaSigMap-SClass.json");
                    if (jsonFileSClass.exists())
                    {
                        StringBuilder jsonStringSClass = new StringBuilder();

                        try (BufferedReader br = new BufferedReader(new FileReader(jsonFileSClass)))
                        {
                            String line;
                            while ((line = br.readLine()) != null)
                                jsonStringSClass.append(line);
                        }
                        catch (IOException e) { printThrowable(e, "Persistence"); }
                        jsonStringSClass.trimToSize();

                        Map<String, Object> jsonSClass = JSONParser.parseJSON(jsonStringSClass.toString());

                        boolean isOld = !(force || (((long) jsonSClass.get("Timestamp")) > (System.currentTimeMillis() - 60000 * 20)));
                        if (isOld)
                            printOut("S-Class JSON file is out of date");

                        EastAngliaSignalMapServer.SClassMap.putAll((Map<String, String>) jsonSClass.get("SClassData"));

                        printOut("[Persistence] Read S-Class data (" + ((Map<String, String>) jsonSClass.get("SClassData")).size() + " objects)");
                    }

                    long mid2 = System.nanoTime();
                    File jsonFileHist = new File(storageDir, "EastAngliaSigMap-TrainHist.json");
                    if (jsonFileHist.exists())
                    {
                        new Thread(() ->
                        {
                            StringBuilder jsonStringHist = new StringBuilder();

                            try (BufferedReader br = new BufferedReader(new FileReader(jsonFileHist)))
                            {
                                String line;
                                while ((line = br.readLine()) != null)
                                    jsonStringHist.append(line);
                            }
                            catch (IOException e) { printThrowable(e, "Persistence"); }
                            jsonStringHist.trimToSize();

                            Map<String, Object> jsonHist = JSONParser.parseJSON(jsonStringHist.toString());

                            boolean isOld = !(force || (((long) jsonHist.get("Timestamp")) > (System.currentTimeMillis() - 60000 * 20)));
                            if (isOld)
                                printOut("[Persistence] Train History JSON file is out of date");

                            lastUUID.set((long) jsonHist.get("TrainHistoryUUID"));

                            Map<String, Map<String, Object>> trainHistory = new HashMap<>((Map<String, Map<String, Object>>) jsonHist.get("TrainHistoryMap"));
                            for (Map.Entry<String, Map<String, Object>> pairs : trainHistory.entrySet())
                            {
                                Map<String, Object> historyData = pairs.getValue();
                                historyData.put("start",  historyData.get("start")  == null ? null : new Date((long) historyData.get("start")));
                                historyData.put("change", historyData.get("change") == null ? null : new Date((long) historyData.get("change")));
                                historyData.put("end",    historyData.get("end")    == null ? null : new Date((long) historyData.get("end")));
                                Berths.addTrainHistory(pairs.getKey(), historyData);
                            }

                            printOut("[Persistence] Read train history data (UUID: " + jsonHist.get("TrainHistoryUUID") + ", " + trainHistory.size() + " objects)");

                            long end = System.nanoTime();
                            printOut(String.format("[Persistence] JSON read took %sms, C-Class: %sms, S-Class %sms, History: %sms", (end-start)/1000000, (mid1-start)/1000000, (mid2-mid1)/1000000, (end-mid2)/1000000));
                        }, "TrainHistoryThing").start();
                    }
                    else
                    {
                        long end = System.nanoTime();
                        printOut(String.format("[Persistence] JSON read took %sms, C-Class: %sms, S-Class %sms, History: %sms", (end-start)/1000000, (mid1-start)/1000000, (mid2-mid1)/1000000, (end-mid2)/1000000));
                    }

                    if (EastAngliaSignalMapServer.guiData != null)
                        EastAngliaSignalMapServer.guiData.updateData();
                }
                catch (Exception e) { printThrowable(e, "Persistence"); }
                //</editor-fold>
            }
            else
                printMain("Not read save file", true);
        }
        else
            printMain("Not reading save file, persistence disabled", false);
    }

    public synchronized static void saveMap()
    {
        if (!disablePersistence)
        {
            long loopStart = System.nanoTime();

            //Map<String, Object> outMap = new HashMap<>();
            //outMap.put("date-time", new Date());

            Map<String, Map<String, Object>> berthMap = new HashMap<>();

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
                }
                berthDetail.put("last_modified", Berths.getBerthLastModifiedTime(pairs.getKey()).getTime());

                berthMap.put(pairs.getKey(), berthDetail);
            }

            //<editor-fold defaultstate="collapsed" desc="Object Output Sream">
            //outMap.put("CClassData", berthMap);
            //outMap.put("SClassData", new HashMap<>(EastAngliaSignalMapServer.SClassMap));
            //outMap.put("TrainHistoryMap", Berths.getTrainHistory());
            //outMap.put("TrainHistoryUUID", lastUUID.get());

            /*
            long mid = System.nanoTime();
            long mid2 = mid;

            File out = new File(storageDir, "EastAngliaSigMap-new.save");

            try
            {
                if (!out.exists())
                {
                    out.getParentFile().mkdirs();
                    out.createNewFile();
                }

                try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(out))))
                {
                    oos.writeObject(new HashMap<>(outMap));
                }

                mid2 = System.nanoTime();

                try { Files.move(out.toPath(), new File(storageDir, "EastAngliaSigMap.save").toPath(), StandardCopyOption.REPLACE_EXISTING); }
                catch (UnsupportedOperationException e) { EastAngliaSignalMapServer.printThrowable(e, "Persistence"); }

                EastAngliaSignalMapServer.printOut("[Persistence] Saved map");
            }
            catch (IOException e)
            {
                printErr("[Persistence] Failed to save map");
                EastAngliaSignalMapServer.printThrowable(e, "Persistence");

                out.delete();
            }
            */
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="JSON Persistence">
            String newLine = System.getProperty("line.separator", "\n");
            StringBuilder cclassSB = new StringBuilder().append("{\"CClassData\":{");

            for (Map.Entry<String, Map<String, Object>> pairs : berthMap.entrySet())
            {
                if (pairs.getKey().startsWith("XX"))
                    continue;

                cclassSB.append(newLine).append('"').append(pairs.getKey()).append("\":{");

                for (Map.Entry<String, Object> berthDetail : pairs.getValue().entrySet())
                {
                    cclassSB.append('"').append(berthDetail.getKey()).append('"').append(':');

                    if (berthDetail.getValue() instanceof List)
                    {
                        cclassSB.append('[');
                        for (Object item : ((List) berthDetail.getValue()))
                            cclassSB.append('"').append(item).append('"').append(',');

                        if (cclassSB.charAt(cclassSB.length()-1) == ',')
                            cclassSB.deleteCharAt(cclassSB.length()-1);
                        cclassSB.append(']');
                    }
                    else if (berthDetail.getValue() instanceof Integer || berthDetail.getValue() instanceof Long)
                        cclassSB.append(berthDetail.getValue());
                    else
                        cclassSB.append('"').append(berthDetail.getValue()).append('"');

                    cclassSB.append(',');
                }

                if (cclassSB.charAt(cclassSB.length()-1) == ',')
                    cclassSB.deleteCharAt(cclassSB.length()-1);
                cclassSB.append("},");
            }
            if (cclassSB.charAt(cclassSB.length()-1) == ',')
                cclassSB.deleteCharAt(cclassSB.length()-1);
            cclassSB.append("},").append(newLine).append("\"Timestamp\":").append(System.currentTimeMillis()).append('}');

            StringBuilder sclassSB = new StringBuilder().append("{\"SClassData\":{");
            for (Map.Entry<String, String> pairs : new HashMap<>(SClassMap).entrySet())
            {
                sclassSB.append(newLine).append('"').append(pairs.getKey()).append("\":\"").append(pairs.getValue()).append("\",");
            }

            if (sclassSB.charAt(sclassSB.length()-1) == ',')
                sclassSB.deleteCharAt(sclassSB.length()-1);
            sclassSB.append("},").append(newLine).append("\"Timestamp\":").append(System.currentTimeMillis()).append('}');

            StringBuilder trainhistorySB = new StringBuilder().append("{\"TrainHistoryMap\":{");
            Map<String, Map<String, Object>> historyMap = Berths.getTrainHistory();
            for (Map.Entry<String, Map<String, Object>> trainHistory : historyMap.entrySet())
            {
                if (((List) trainHistory.getValue().get("history")).size() <= 2)
                    continue;

                trainhistorySB.append(newLine).append('"').append(trainHistory.getKey()).append("\":{");

                for (Map.Entry<String, Object> trainHistoryDetail : trainHistory.getValue().entrySet())
                {
                    trainhistorySB.append('"').append(trainHistoryDetail.getKey()).append("\":");
                    Object value = trainHistoryDetail.getValue();

                    if (value == null)
                        trainhistorySB.append("null");
                    else if (value instanceof Date)
                        trainhistorySB.append(((Date) value).getTime());
                    else if (value instanceof List)
                    {
                        trainhistorySB.append('[');
                        for (Object item : ((List) value))
                        {
                            trainhistorySB.append('"').append(item).append('"').append(',');
                        }
                        if (trainhistorySB.charAt(trainhistorySB.length()-1) == ',')
                            trainhistorySB.deleteCharAt(trainhistorySB.length()-1);
                        trainhistorySB.append(']');
                    }
                    else
                        trainhistorySB.append('"').append(value).append('"');

                    trainhistorySB.append(',');
                }

                if (trainhistorySB.charAt(trainhistorySB.length()-1) == ',')
                    trainhistorySB.deleteCharAt(trainhistorySB.length()-1);
                trainhistorySB.append("},");
            }
            if (trainhistorySB.charAt(trainhistorySB.length()-1) == ',')
                trainhistorySB.deleteCharAt(trainhistorySB.length()-1);
            trainhistorySB.append("},");

            trainhistorySB.append(newLine).append("\"TrainHistoryUUID\":").append(lastUUID.get()).append(',');
            trainhistorySB.append(newLine).append("\"Timestamp\":").append(System.currentTimeMillis()).append('}');

            long fileStart = System.nanoTime();

            File CClassJSON = new File(storageDir, "EastAngliaSigMap-CClass.json");
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(CClassJSON)))
            {
                bw.write(cclassSB.toString());
            }
            catch (IOException e) { printThrowable(e, "Persistence"); }

            File SClassJSON = new File(storageDir, "EastAngliaSigMap-SClass.json");
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(SClassJSON)))
            {
                bw.write(sclassSB.toString());
            }
            catch (IOException e) { printThrowable(e, "Persistence"); }

            File TrainHistJSON = new File(storageDir, "EastAngliaSigMap-TrainHist.json");
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(TrainHistJSON)))
            {
                bw.write(trainhistorySB.toString());
            }
            catch (IOException e) { printThrowable(e, "Persistence"); }
            //</editor-fold>

            long end = System.nanoTime();

            printOut("[Persitence] Saved current state");
            printOut(String.format("[Persitence] Time all: %sms, json: %sms, file: %sms", (end-loopStart)/1000000, (fileStart-loopStart)/1000000, (end-fileStart)/1000000));
        }
        else
            printOut("[Persitence] Not saving state, persistence disabled");
    }

    public static void updateIP()
    {
        try
        {
            // Cant ping amazonaws.com
            /*if (!InetAddress.getByName("checkip.amazonaws.com").isReachable(2000))
            {
                printErr("[IPUpdater] \"http://checkip.amazonaws.com/\" is unavailable");
                return;
            }*/

            if (!InetAddress.getByName("easignalmap.altervista.org").isReachable(2000))
            {
                printErr("[IPUpdater] \"http://easignalmap.altervista.org/\" is unavailable");
                return;
            }
        }
        catch (IOException e)
        {
            printErr("[IPUpdater] IOException: " + e.toString());
            return;
        }

        String externalIP = "127.0.0.1";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com/").openStream())))
        {
            externalIP = br.readLine();
        }
        catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to amazomaws"); }
        catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
        catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "IPUpdater"); }

        String externalIPOld = "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(ftpBaseUrl + "server.ip;type=i").openStream())))
        {
            externalIPOld = br.readLine();
        }
        catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to altervista"); }
        catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
        catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "IPUpdater"); }


        if (!externalIP.equals(externalIPOld))
        {
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
        }
        else
        {
            printOut("[IPUpdater] Not updating IP (" + externalIP + ")");
        }
    }

    public static void updateServerGUIs()
    {
        if (guiServer != null)
            guiServer.updateClientList();

        if (guiData != null)
            guiData.updateData();
    }
}