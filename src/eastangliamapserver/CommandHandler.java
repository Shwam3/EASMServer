package eastangliamapserver;

import eastangliamapserver.gui.ListDialog;
import eastangliamapserver.server.Client;
import eastangliamapserver.server.Clients;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHandler
{
    public static Thread commandInputThread = new Thread("commandInput")
    {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        @Override
        public void run()
        {
            while (!isInterrupted())
            {
                try
                {
                    final String command = br.readLine();
                    EastAngliaSignalMapServer.printOut(command);

                    Thread t = new Thread() { @Override public void run() { handle(command.split(" ")[0], command.split(" ")); }};
                    t.setName(command);
                    t.start();
                }
                catch (IOException e) {}
            }
        }
    };

    public static void handle(String command, String... args)
    {
        switch(command.toLowerCase())
        {
            case "help":
                //<editor-fold defaultstate="collapsed" desc="Help">
                printCommand("stop", false);
                printCommand("Stops the server", false);

              //printCommand("admin", false);
              //printCommand("Sets the specified client as an admin", false);

                printCommand("kick", false);
                printCommand("Disconnects the specified client", false);

                printCommand("clients", false);
                printCommand("Lists all clients", false);

                printCommand("cancel", false);
                printCommand("Removes the train in the spcified berth", false);

                printCommand("interpose", false);
                printCommand("Inserts the specified train into the spacified berth", false);

              //printCommand("problem", false);
              //printCommand("Sets the specified berth as having a problem", false);

                printCommand("get", false);
                printCommand("Get the data (headcode) in the specified berth", false);

                printCommand("search", false);
                printCommand("Opens a 'RealTimeTrains.co.uk' search for the given train", false);

                printCommand("sendall", false);
                printCommand("Send the full data map to all clients", false);

                printCommand("addberth", false);
                printCommand("Add the specified berth id to the tracked berths", false);

                printCommand("reconnect", false);
                printCommand("Attempts to reconnect to Network Rail\'s servers", false);

                printCommand("sleep", false);
                printCommand("Weather or not the computer is allowed to sleep", false);

                printCommand("openlog", false);
                printCommand("Open the log file in your default text editor", false);
                //</editor-fold>
                break;

            case "stop":
                EastAngliaSignalMapServer.stop();
                break;

            case "kick":
                if (args.length != 2 || args[1].equals("help"))
                    printCommand("Usage: kick <all|client_name|client_address> [reason]", true);
                else
                {
                    if (args[1].equals("all"))
                    {
                        Clients.kickAll(args.length == 3 ? args[2] : null);
                        printCommand("Kicked all clients", false);
                    }
                    else
                    {
                        if (Clients.hasMultiple(args[1]))
                        {
                            printCommand("Multiple clients by the same name, use IP address to kick", false);
                            return;
                        }

                        Client client = Clients.getClient(args[1]);
                        printCommand("Kick client " + args[1], false);

                        if (client == null)
                            printCommand("Unrecognised client name/address", true);
                        else
                            client.disconnect("You have been kicked" + (args.length == 3 && args[2] != null && !args[2].equals("") ? ": " + args[2] : ""));
                    }
                }
                break;

            case "clients":
                if (Clients.getClientList().isEmpty())
                    printCommand("No clients connected", false);
                else
                {
                    printCommand("Current clients", false);
                    for (String client : Clients.getClientList())
                        printCommand("    " + client, false);
                }
                break;

            case "data":
                EastAngliaSignalMapServer.guiData.setVisible0(!EastAngliaSignalMapServer.guiData.isVisible());
                if (EastAngliaSignalMapServer.guiData.isVisible())
                    EastAngliaSignalMapServer.guiData.requestFocus();
                break;

            case "interpose":
                if (args.length != 3)
                    printCommand("Usage: interpose <berth_id> <headcode>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());
                    if (berth != null)
                    {
                        berth.interpose(new Train(args[2].toUpperCase(), berth));

                        Map<String, String> updateMap = new HashMap<>();
                        updateMap.put(args[1].toUpperCase(), args[2].toUpperCase());
                        Clients.broadcastUpdate(updateMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                        printCommand("Interposed " + args[2].toUpperCase() + " to berth " + berth.getBerthDescription(), false);
                    }
                    else
                        printCommand("Unrecognised berth id \"" + args[1].toUpperCase() + "\"", true);
                }
                break;

            case "cancel":
                if (args.length != 2 && args.length != 3)
                    printCommand("Usage: cancel <berth_id>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth != null)
                    {
                        if (args.length == 3)
                            berth.cancel(args[2].substring(0, 4).toUpperCase(), null);
                        else
                            berth.cancel(berth.getHeadcode(), null);

                        Map<String, String> updateMap = new HashMap<>();
                        updateMap.put(args[1].toUpperCase(), "");
                        Clients.broadcastUpdate(updateMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                        printCommand("Cancelled train in berth " + berth.getBerthDescription(), false);
                    }
                    else
                        printCommand("Unrecognised berth id \"" + args[1].toUpperCase() + "\"", true);
                }
                break;

            case "step":
                if (args.length != 3 && args.length != 4)
                    printCommand("Usage: step <from_berth> <to_berth> [headcode]", true);
                else
                {
                    try
                    {
                        Berth fromBerth = Berths.getBerth(args[1].toUpperCase());
                        Berth toBerth = Berths.getBerth(args[2].toUpperCase());
                        Train newTrain = null;

                        if (fromBerth == null)
                            printCommand("Unrecognised berth id \"" + args[1].toUpperCase() + "\"", true);

                        if (toBerth == null)
                            printCommand("Unrecognised berth id \"" + args[2].toUpperCase() + "\"", true);

                        if (fromBerth != null && toBerth != null)
                        {
                            if (fromBerth.hasTrain())
                                newTrain = fromBerth.getTrain();
                            else
                            {
                                printCommand("No train to step", true);
                                return;
                            }

                            if (fromBerth.hasAdjacentBerths())
                                for (Train train : fromBerth.getAdjacentTrains())
                                    if (train.getHeadcode().equals(fromBerth.getHeadcode()))
                                    {
                                        toBerth = train.getCurrentBerth();

                                        if (!toBerth.getHeadcode().equals(newTrain.getHeadcode()))
                                            toBerth.interpose(newTrain);

                                        fromBerth.cancel(newTrain.getHeadcode(), null);
                                    }
                            else
                                fromBerth.cancel(newTrain.getHeadcode(), null);

                            if (fromBerth.canStepToBerth(toBerth.getId(), fromBerth.getId()))
                                for (String berthId : fromBerth.getStepToBerthsFor(toBerth.getId(), fromBerth.getId()))
                                    Berths.getBerth(berthId).suggestTrain(newTrain, fromBerth.getTypeFor(berthId, fromBerth.getId()));

                            Map<String, String> updateMap = new HashMap<>();
                            updateMap.put(fromBerth.getId(), "");
                            updateMap.put(toBerth.getId(), newTrain.getHeadcode());

                            Clients.broadcastUpdate(updateMap);
                            EastAngliaSignalMapServer.CClassMap.putAll(updateMap);
                        }
                    }
                    catch (Exception e)
                    {
                        EastAngliaSignalMapServer.printThrowable(e, "Command");
                    }
                }
                break;

            case "set":
                if (args.length != 2 && args.length != 3)
                    printCommand("Usage: set <address> [1|0]", true);
                else if (args.length == 2)
                {
                    if (EastAngliaSignalMapServer.SClassMap.containsKey(args[1]))
                        printCommand("Bit " + args[1] + " = " + EastAngliaSignalMapServer.SClassMap.get(args[1]), false);
                    else
                        printCommand("Unrecognised bit address \"" + args[1] + "\"", true);
                }
                else if (args.length == 3)
                {
                    if (!args[2].equals("0") && !args[2].equals("1"))
                        printCommand("Invalid value \"" + args[2] + "\"", true);
                    else if (args[1].length() == 6)
                    {
                        EastAngliaSignalMapServer.SClassMap.put(args[1].toUpperCase(), args[2]);
                        Clients.scheduleForNextUpdate(args[1].toUpperCase(), args[2]);
                        printCommand("Set bit " + args[1] + " to " + args[2], false);
                    }
                    else
                        printCommand("Invalid bit address \"" + args[1].toUpperCase()+ "\"", true);
                }
                break;

            /*case "problem":
                if (args.length != 3 || (!args[2].toLowerCase().equals("true") && !args[2].toLowerCase().equals("false")))
                    printCommand("Usage: problem <berth_id> <true|false>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth != null)
                    {
                        berth.setProblematicBerth(args[2].toLowerCase().equals("true"));

                        Map updateMap = new HashMap<>();
                        updateMap.put(args[1].toUpperCase(), args[2].toLowerCase().equals("true"));
                        Clients.broadcastUpdate(updateMap);

                        printCommand("Set berth " + args[1].toUpperCase() + " to " + (args[2].toLowerCase().equals("false") ? "not " : "") + "problematic", false);
                    }
                    else
                        printCommand("Unrecognised berth id", true);
                }
                break;*/

            case "search":
                if (args.length != 2 && args.length != 3)
                    printCommand("Usage: search <headcode [area] | station>", true);
                else if (args.length == 2)
                    try
                    {
                        Desktop.getDesktop().browse(new URI("http://www.realtimetrains.co.uk/search/advancedhandler?type=advanced&qs=true&search=" + args[1].toUpperCase()));
                    }
                    catch (IOException | URISyntaxException e) { EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                else
                    try
                    {
                        Desktop.getDesktop().browse(new URI("http://www.realtimetrains.co.uk/search/advancedhandler?type=advanced&qs=true&search=" + args[1].toUpperCase() + "&area=" + args[2].toUpperCase()));
                    }
                    catch (IOException | URISyntaxException e) { EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                break;

            case "get":
                if (args.length != 2)
                    printCommand("Usage: get <berth_id|bit_address>", true);
                else
                {
                    String id = args[1].toUpperCase();
                    Berth berth = Berths.getBerth(id);

                    if (berth != null)
                    {
                        String mapHC = EastAngliaSignalMapServer.CClassMap.get(id);
                        printCommand("Berth " + id + " contains " + (mapHC == null || mapHC.isEmpty() ? "no headcode" : "headcode \"" + mapHC + "\"") + " (map)", false);
                        printCommand("Berth " + berth.getBerthDescription() + " contains " + (berth.getHeadcode().isEmpty() ? "no headcode" : "headcode \"" + berth.getHeadcode() + "\"") + " (berth)", false);
                    }
                    else if (EastAngliaSignalMapServer.SClassMap.containsKey(id))
                    {
                        printCommand("Bit " + id + " is " + EastAngliaSignalMapServer.SClassMap.get(id), false);
                    }
                    else
                        printCommand("Unrecognised id '" + args[1].toUpperCase() + "'", true);
                }
                break;

            case "sendall":
            case "sendfull":
            case "fullmap":
                Clients.sendAll();
                printCommand("Broadcast full map to all clients", false);
                break;

            case "addberth":
                if (args.length == 2 || args.length == 6)
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth == null)
                    {
                        berth = Berths.createOrGetBerth(args[1].toUpperCase());
                        printCommand("Created berth with id '" + berth.getBerthDescription() + "'", false);
                    }
                    else
                        printCommand("Berth already exists", true);

                    if (args.length == 6)
                    {
                        berth.addStepToBerth(args[2].toUpperCase(), args[3].toUpperCase(), args[4].toUpperCase(), args[5].toLowerCase());
                        printCommand(String.format("Added step instructions 'from: %s, copyto: %s, to: %s, type: %s' to berth %s", args[2].toUpperCase(), args[3].toUpperCase(), args[4].toUpperCase(), args[5].toUpperCase(), berth.getBerthDescription()), false);
                    }
                }
                else
                    printCommand("Usage: addberth <berth_id> [<real_from> <fake_to> <real_to> <suggest|interpose>]", true);
                break;

            case "reconnect":
            case "rc":
                StompConnectionHandler.disconnect();
                StompConnectionHandler.wrappedConnect();
                break;

            case "sleep":
                if (args.length != 1 && args.length != 2 && !(args[1].toLowerCase().equals("true") || args[1].toLowerCase().equals("false")))
                    printCommand("Usage: sleep [true|false]", true);
                else
                    if (args.length == 1)
                        printCommand(TimerMethods.sleep() ? "Stopped preventing sleeping" : "Prevented sleeping", false);
                    else
                        printCommand(TimerMethods.sleep(args[1].toLowerCase().equals("true")) ? "Stopped prevented sleeping" : "Preventing sleeping", false);
                break;

            case "openlog":
            case "openfile":
            case "openlogfile":
                try
                {
                    Desktop.getDesktop().open(EastAngliaSignalMapServer.logFile);
                }
                catch (IOException e)
                {
                    printCommand("Unable to open log file:", true);
                    EastAngliaSignalMapServer.printThrowable(e, "Command");
                }
                break;

            case "openfolder":
            case "openlogfolder":
                try { Runtime.getRuntime().exec("explorer.exe /select," + EastAngliaSignalMapServer.logFile.getAbsolutePath()); }
                catch (IOException e) {}
                break;

            case "nameberth":
                if (args.length < 3)
                    printCommand("Usage: nameberth <berth_id> <name>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth != null)
                    {
                        String name = "";
                        for (int i = 2; i < args.length; i++)
                            name += " " + args[i];

                        berth.setName(name.trim());
                        printCommand("Named berth " + berth.getBerthDescription() + " \"" + name.trim() + "\"", false);
                    }
                    else
                        printCommand("Unrecognised berth id " + args[1].toUpperCase(), true);
                }
                break;

            /*case "printmissing":
                Berths.printIds();
                break;

            case "printall":
                StompConnectionHandler.printCClass("All Berth Ids:", false);

                for (String berthId : Berths.getKeySet())
                    StompConnectionHandler.printCClass(berthId, false);

                break;*/

            case "savemap":
            case "sm":
                EastAngliaSignalMapServer.saveMap();
                break;

            case "readmap":
            case "rm":
                if (args.length == 2 && (args[1].toLowerCase().equals("force") || args[1].toLowerCase().equals("f")))
                    new Thread(() -> { EastAngliaSignalMapServer.readSavedMap(true); }).start();
                else
                    EastAngliaSignalMapServer.readSavedMap(false);

                break;

            case "server":
                if (args.length != 2 && args.length != 1)
                    printCommand("Usage: server [open|close|restart]", true);
                else if (args.length == 1)
                {
                    printCommand("Server " + (EastAngliaSignalMapServer.server.isClosed() ? "closed" : "open"), false);
                    printCommand(String.valueOf(EastAngliaSignalMapServer.server), false);
                }
                else if (args[1].toLowerCase().equals("open"))
                    if (EastAngliaSignalMapServer.server != null && !EastAngliaSignalMapServer.server.isClosed())
                        printCommand("Server already open", false);
                    else
                    {
                        EastAngliaSignalMapServer.serverOffline = false;
                        try { EastAngliaSignalMapServer.server = new ServerSocket(EastAngliaSignalMapServer.port, 2); }
                        catch (IOException e) { printCommand("Unable to open server:", true); EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                    }
                else if (args[1].toLowerCase().equals("close"))
                    if (EastAngliaSignalMapServer.server != null && !EastAngliaSignalMapServer.server.isClosed())
                    {
                        EastAngliaSignalMapServer.serverOffline = true;
                        try { EastAngliaSignalMapServer.server.close(); }
                        catch (IOException e) { printCommand("Unable to close server:", true); EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                    }
                    else
                        printCommand("Server already closed", false);
                else if (args[1].toLowerCase().equals("restart"))
                    try
                    {
                        Clients.kickAll("Server restarting");
                        EastAngliaSignalMapServer.server.close();

                        EastAngliaSignalMapServer.server = new ServerSocket(EastAngliaSignalMapServer.port);

                        printCommand("Server restarted", false);
                    }
                    catch (IOException e)
                    {
                        printCommand("Unable to restart server:", true);
                        EastAngliaSignalMapServer.printThrowable(e, "Command");
                    }
                break;

            case "stomp":
                if (args.length != 2 && args.length != 1)
                    printCommand("Usage: stomp [start|stop]", true);
                else if (args.length == 1)
                    printCommand("Stomp " + (!StompConnectionHandler.isConnected() ? "disconnected " : "")
                            + (StompConnectionHandler.isClosed()? "closed " : "")
                            + (StompConnectionHandler.isTimedOut() ? "timed out " : "")
                            + (!StompConnectionHandler.isClosed() && StompConnectionHandler.isConnected() && !StompConnectionHandler.isTimedOut() ? "normal" : ""), false);
                else if (args[1].toLowerCase().equals("start"))
                {
                    EastAngliaSignalMapServer.stompOffline = false;
                    if (StompConnectionHandler.isConnected() && !StompConnectionHandler.isClosed())
                        printCommand("Stomp already started", false);
                    else
                    {
                        StompConnectionHandler.disconnect();

                        if (StompConnectionHandler.wrappedConnect())
                            printCommand("Stomp started", false);
                        else
                            printCommand("Unable to restart stomp", true);
                    }
                }
                else if (args[1].toLowerCase().equals("stop"))
                {
                    EastAngliaSignalMapServer.stompOffline = true;
                    if (StompConnectionHandler.isConnected() && !StompConnectionHandler.isClosed())
                        StompConnectionHandler.disconnect();
                    else
                        printCommand("Stomp already stopped", false);
                }
                break;

            case "pack":
                EastAngliaSignalMapServer.guiServer.frame.pack();
                EastAngliaSignalMapServer.guiServer.frame.setLocationRelativeTo(null);
                break;

            case "client_history":
            case "clhist":
                new ListDialog("Client history", "History of client stuffs", Clients.clientsHistory);
                break;

            case "train_history":
            case "trhist":
                if (args.length != 2)
                    printCommand("Usage: trhist <train UUID>", true);
                else
                {
                    Map<String, Object> train = Berths.getTrain(args[1]);
                    if (train == null)
                        printCommand("Train doesn't exist", false);
                    else
                        new ListDialog("Train History", "History of train \"" + args[1] + "\" (" + train.get("headcode") + ")", (List<String>) train.get("history"));
                }
                break;

            case "motd":
                Berth berth = Berths.createOrGetBerth("XXMOTD");
                if (args.length == 1)
                {
                    printCommand("MOTD: \"" + (berth != null ? berth.getHeadcode() : "") + "\"", false);
                }
                else
                {
                    if (berth != null)
                    {
                        String motd = "";
                        for (int i = 1; i < args.length; i++)
                            motd += (motd.length() > 0 ? " " : "") + args[i];

                        motd = motd.replaceAll("%date%", EastAngliaSignalMapServer.sdfDate.format(new Date()));
                        motd = motd.replaceAll("%time%", EastAngliaSignalMapServer.sdfTime.format(new Date()));

                        motd = motd.trim();

                        berth.interpose(new Train(motd, berth));

                        Map<String, String> motdMap = new HashMap<>();
                        motdMap.put("XXMOTD", motd);
                        Clients.broadcastUpdate(motdMap);
                        EastAngliaSignalMapServer.CClassMap.put("XXMOTD", motd);

                        try
                        {
                            File motdFile = new File(EastAngliaSignalMapServer.storageDir, "MOTD.txt");
                            if (!motdFile.exists())
                            {
                                motdFile.getParentFile().mkdirs();
                                motdFile.createNewFile();
                            }

                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(motdFile)))
                            {
                                bw.write(motd);
                                bw.write("\r\n");
                            }
                        }
                        catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "MOTD"); }

                        /*try
                        {
                            URLConnection con = new URL(EastAngliaSignalMapServer.ftpBaseUrl + "status/status.txt;type=i").openConnection();
                            con.setConnectTimeout(10000);

                            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream())))
                            {
                                out.write(motd);
                            }
                            catch (IOException e) {}
                        }
                        catch (FileNotFoundException | MalformedURLException e) {}
                        catch (IOException e) {}*/


                        printCommand("MOTD: \"" + motd + "\"", false);

                        EastAngliaSignalMapServer.updateServerGUIs();
                    }
                }
                break;

            case "sync":
                String changes = "";

                for (Map.Entry<String, Berth> pairs : Berths.getEntrySet())
                {
                    if (EastAngliaSignalMapServer.CClassMap.containsKey(pairs.getKey()) && !EastAngliaSignalMapServer.CClassMap.get(pairs.getKey()).equals(pairs.getValue().getHeadcode()))
                    {
                        changes += pairs.getValue().getBerthDescription() + ": " + EastAngliaSignalMapServer.CClassMap.get(pairs.getKey()) + " -> " + pairs.getValue().getHeadcode() + ", ";
                        Clients.scheduleForNextUpdate(pairs.getKey(), pairs.getValue().getHeadcode());
                    }

                    EastAngliaSignalMapServer.CClassMap.put(pairs.getKey(), pairs.getValue().getHeadcode());
                }

                EastAngliaSignalMapServer.CClassMap = new HashMap<>(EastAngliaSignalMapServer.CClassMap);

                printCommand("Synced map with berths (" + (!changes.isEmpty() ? changes.substring(0, changes.length() - 2) : "no changes") + ")", false);

                System.gc();
                break;

            case "updateip":
                EastAngliaSignalMapServer.updateIP();
                break;

            case "incrementid":
                StompConnectionHandler.incrementConnectionId();
                break;

            case "setmaxtimeout":
                if (args.length == 1 || args.length == 2)
                {
                    try
                    {
                        int newTimeout = Integer.parseInt(args[1]);

                        StompConnectionHandler.setMaxTimeoutWait(newTimeout);
                    }
                    catch (NumberFormatException | IndexOutOfBoundsException e)
                    {
                        printCommand("Usage: setmaxtimeout <seconds>", true);
                    }
                }
                else
                    printCommand("Usage: setmaxtimeout <seconds>", false);
                break;

            case "reset":
                EastAngliaSignalMapServer.CClassMap.clear();
                EastAngliaSignalMapServer.SClassMap.clear();
                for (Map.Entry<String, Berth> pairs : Berths.getEntrySet())
                    pairs.getValue().cancel("", "");
                Berths.purgeHistories();
                break;

            case "gc":
            case "clean":
                EastAngliaSignalMapServer.CClassMap = new HashMap<>(EastAngliaSignalMapServer.CClassMap);
                Berths.cleanMaps();
                System.gc();
                break;

            case "savereplay":
            case "sr":
                String newLine = System.getProperty("line.separator", "\n");
                StringBuilder sb = new StringBuilder().append("{\"ReplayData\":{");

                sb.append(newLine);

                EastAngliaSignalMapServer.CClassMap.entrySet().stream().filter(p -> !p.getKey().contains("XX")).forEach(pairs ->
                    sb.append('"').append(pairs.getKey()).append("\":\"").append(pairs.getValue()).append("\",")
                );

                sb.append(newLine);

                EastAngliaSignalMapServer.SClassMap.entrySet().stream().filter(p -> p.getKey().contains(":")).forEach(pairs ->
                    sb.append('"').append(pairs.getKey()).append("\":\"").append(pairs.getValue()).append("\",")
                );

                if (sb.charAt(sb.length()-1) == ',')
                    sb.deleteCharAt(sb.length()-1);
                sb.append(newLine).append("}}");

                try
                {
                    File ReplayJSON = new File(EastAngliaSignalMapServer.storageDir, "Logs" + File.separator + "ReplaySaves" + File.separator + EastAngliaSignalMapServer.sdfDateTime.format(new Date()).replace("/", "-").replace(":", ".") + ".json");
                    ReplayJSON.getParentFile().mkdirs();
                    ReplayJSON.createNewFile();

                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(ReplayJSON)))
                    {
                        bw.write(sb.toString());
                    }
                    catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "Persistence"); }

                    EastAngliaSignalMapServer.printOut("[Persistence] Saved replay file (" + ReplayJSON.length() / 1024L + "KiB)");
                }
                catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "Persistence");}
                break;

            default:
                printCommand("Unrecognised command '" + command + "'", true);
                break;
        }
    }

    private static void printCommand(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Command] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Command] " + message);
    }
}