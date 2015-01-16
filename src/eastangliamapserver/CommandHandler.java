package eastangliamapserver;

import eastangliamapserver.gui.ListDialog;
import eastangliamapserver.stomp.StompConnectionHandler;
import eastangliamapserver.stomp.handlers.RTPPMHandler;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

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

            /*case "admin":
                if (args.length != 2 || args[1].equals("help"))
                    printCommand("Usage: admin <client_name/client_address>", true);
                else
                {
                    printCommand("Set client " + args[1] + " as an admin", false);
                }
                break;*/

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
                    printCommand("Usage: get <berth_id>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth != null)
                        printCommand("Berth " + berth.getBerthDescription() + " contains " + (berth.getHeadcode().equals("") ? "no headcode" : "headcode " + berth.getHeadcode()), false);
                    else
                        printCommand("Unrecognised berth id '" + args[1].toUpperCase() + "'", true);
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

            case "printmissing":
                Berths.printIds();
                break;

            case "printall":
                StompConnectionHandler.printCClass("All Berth Ids:", false);

                for (String berthId : Berths.getKeySet())
                    StompConnectionHandler.printCClass(berthId, false);

                break;

            case "savemap":
            case "sm":
                EastAngliaSignalMapServer.saveMap();
                break;

            case "readmap":
            case "rm":
                if (args.length == 2 && args[1].toLowerCase().equals("force"))
                    EastAngliaSignalMapServer.readSavedMap(true);
                else
                    EastAngliaSignalMapServer.readSavedMap(false);

                break;

            case "server":
                if (args.length != 2 && args.length != 1)
                    printCommand("Usage: server [open|close]", true);
                else if (args.length == 1)
                {
                    printCommand("Server " + (EastAngliaSignalMapServer.server.isClosed() ? "closed" : "open"), false);
                    printCommand(String.valueOf(EastAngliaSignalMapServer.server), false);
                }
                else if (args[1].toLowerCase().equals("open"))
                    if (EastAngliaSignalMapServer.server != null && !EastAngliaSignalMapServer.server.isClosed())
                        printCommand("Server already open", false);
                    else
                        try
                        {
                            EastAngliaSignalMapServer.server = new ServerSocket(EastAngliaSignalMapServer.port, 2);
                        }
                        catch (IOException e) { printCommand("Unable to open server:", true); EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                else if (args[1].toLowerCase().equals("close"))
                    if (EastAngliaSignalMapServer.server != null && !EastAngliaSignalMapServer.server.isClosed())
                        try
                        {
                            EastAngliaSignalMapServer.server.close();
                        }
                        catch (IOException e) { printCommand("Unable to close server:", true); EastAngliaSignalMapServer.printThrowable(e, "Command"); }
                    else
                        printCommand("Server already closed", false);
                else if (args[1].toLowerCase().equals("restart"))
                    try
                    {
                        if (!EastAngliaSignalMapServer.server.isClosed())
                            EastAngliaSignalMapServer.server.close();

                        EastAngliaSignalMapServer.server = new ServerSocket(EastAngliaSignalMapServer.port, 2);

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
                else if (args[1].toLowerCase().equals("stop"))
                    if (StompConnectionHandler.isConnected() && !StompConnectionHandler.isClosed())
                        StompConnectionHandler.disconnect();
                    else
                        printCommand("Stomp already stopped", false);

                break;

            case "pack":
                EastAngliaSignalMapServer.gui.frame.pack();
                EastAngliaSignalMapServer.gui.frame.setLocationRelativeTo(null);
                break;

            case "client_history":
            case "clhist":
                new ListDialog("Client history", "History of client stuffs", Clients.clientsHistory);
                break;

            case "motd":
                if (args.length == 1)
                {
                    Berth berth = Berths.createOrGetBerth("XXMOTD");
                    printCommand("MOTD: \"" + (berth != null ? berth.getHeadcode() : "") + "\"", false);
                }
                else
                {
                    Berth berth = Berths.createOrGetBerth("XXMOTD");

                    if (berth != null)
                    {
                        String motd = "";//new SimpleDateFormat("dd/MM HH:mm:ss:").format(new Date());
                        for (int i = 1; i < args.length; i++)
                            motd += (motd.length() > 0 ? " " : "") + args[i];

                        motd = motd.replaceAll("%date%", new SimpleDateFormat("dd/MM").format(new Date()));
                        motd = motd.replaceAll("%time%", new SimpleDateFormat("HH:mm:ss").format(new Date()));

                        berth.interpose(new Train(motd.trim(), berth));

                        Map<String, String> motdMap = new HashMap<>();
                        motdMap.put("XXMOTD", motd.trim());
                        Clients.broadcastUpdate(motdMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(motdMap);

                        try
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
                        catch (IOException e) {}


                        printCommand("MOTD (literal): \"" + motd + "\"", false);

                        EastAngliaSignalMapServer.gui.updateDataList();
                    }
                }
                break;

            case "sync":
                String changes = "";

                for (Map.Entry<String, Berth> pairs : Berths.getEntrySet())
                {
                    if (EastAngliaSignalMapServer.CClassMap.containsKey(pairs.getKey()) && !EastAngliaSignalMapServer.CClassMap.get(pairs.getKey()).equals(pairs.getValue().getHeadcode()))
                        changes += pairs.getValue().getBerthDescription() + ": " + EastAngliaSignalMapServer.CClassMap.get(pairs.getKey()) + " -> " + pairs.getValue().getHeadcode() + ", ";

                    EastAngliaSignalMapServer.CClassMap.put(pairs.getKey(), pairs.getValue().getHeadcode());
                }

                EastAngliaSignalMapServer.CClassMap = new HashMap<>(EastAngliaSignalMapServer.CClassMap);

                Clients.sendAll();

                printCommand("Synced map with berths (" + (!changes.isEmpty() ? changes.substring(0, changes.length() - 2) : "no changes") + ")", false);

                System.gc();
                break;

            case "addresses":
                if (args.length != 2)
                    printCommand("Usage: addresses <berth_id>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());
                    if (berth != null)
                    {
                        Map<String, Map<String, String>> map = berth.getPossibleAddreses();
                        List<String> possibleAddresses = new ArrayList<>(map.size());

                        for (Map.Entry<String, Map<String, String>> pairs : map.entrySet())
                            possibleAddresses.add(pairs.getValue().get("occurences") + ": " + pairs.getKey() + " " + (pairs.getValue().containsKey("old_data") ? pairs.getValue().get("old_data") : "--") + " --> " +  pairs.getValue().get("data") + " (" + (pairs.getValue().containsKey("bit_change") ? pairs.getValue().get("bit_change") : "-") + ")");

                        Collections.sort(possibleAddresses, new Comparator<String>()
                        {
                            @Override public int compare(String s1, String s2)
                            {
                                try
                                {
                                    String num1 = s1.substring(0, s1.indexOf(":")).trim();
                                    String num2 = s2.substring(0, s2.indexOf(":")).trim();
                                    return Integer.valueOf(num2) - Integer.valueOf(num1);
                                }
                                catch (NumberFormatException e) { return 0; }
                            }
                        });
                        new ListDialog("S-Class data", "S-Class data for " + berth.getBerthDescription(), possibleAddresses);
                    }
                    else
                        printCommand("Unrecognised berth id \"" + args[1].toUpperCase() + "\"", true);
                }
                break;

            case "updateip":
                EastAngliaSignalMapServer.updateIP();
                break;

            case "updateppm":
            case "updatertppm":
                RTPPMHandler.uploadHTML();
                break;

            case "gc":
            case "clean":
                EastAngliaSignalMapServer.CClassMap = new HashMap<>(EastAngliaSignalMapServer.CClassMap);
                Berths.cleanMaps();
                System.gc();
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