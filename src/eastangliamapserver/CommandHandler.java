package eastangliamapserver;

import eastangliamapserver.gui.ListDialog;
import eastangliamapserver.gui.ServerGui;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.util.*;

public class CommandHandler
{
    private static final String HELP        = "help";
    private static final String STOP        = "stop";
    private static final String SET_ADMIN   = "admin";
    private static final String KICK        = "kick";
    private static final String CLIENT_LIST = "clients";
    private static final String CANCEL      = "cancel";
    private static final String INTERPOSE   = "interpose";
    private static final String PROBLEM     = "problem";
    private static final String GET         = "get";
    private static final String SEARCH      = "search";
    private static final String SEND_ALL    = "sendall";
    private static final String ADD_BERTH   = "addberth";
    private static final String RECONNECT   = "reconnect";
    private static final String SLEEP       = "sleep";
    private static final String OPEN_LOG    = "openlog";
    private static final String NAME_BERTH  = "nameberth";
    private static final String MISSING     = "printmissing";
    private static final String ALL         = "printall";
    private static final String SAVE_MAP    = "savemap";

    public static void handle(String command, String... args)
    {
        switch(command.toLowerCase())
        {
            case HELP:
                //<editor-fold defaultstate="collapsed" desc="Help">
                printCommand("stop", false);
                printCommand("Stops the server", false);

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

            case STOP:
                printCommand("Stop server", false);
                EastAngliaSignalMapServer.stop();
                break;

            /*case SET_ADMIN:
                if (args.length != 2 || args[1].equals("help"))
                    printCommand("Usage: admin <client_name/client_address>", true);
                else
                {
                    printCommand("Set client " + args[1] + " as an admin", false);
                }
                break;*/

            case KICK:
                if (args.length != 2 || args[1].equals("help"))
                    printCommand("Usage: kick <all|client_name|client_address>", true);
                else
                {
                    if (args[1].equals("all"))
                    {
                        Clients.kickAll();
                        printCommand("Kicked all clients", false);
                    }
                    else
                    {
                        if (Clients.hasMultiple(args[1]))
                        {
                            printCommand("Multiple clients by the same name, use IP to kick", false);
                            return;
                        }

                        Client client = Clients.getClient(args[1]);
                        printCommand("Kick client " + args[1], false);

                        if (client == null)
                            printCommand("Unrecognised client name/address", true);
                        else
                            client.disconnect();
                    }
                }
                break;

            case CLIENT_LIST:
                if (Clients.getClientList().isEmpty())
                    printCommand("No clients connected", false);
                else
                {
                    printCommand("Current clients", false);
                    for (String client : Clients.getClientList())
                        printCommand("    " + client, false);
                }
                break;

            case INTERPOSE:
                if (args.length != 3)
                    printCommand("Usage: interpose <berth_id> <headcode>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());
                    if (berth != null)
                    {
                        berth.interpose(new Train(args[2].toUpperCase(), berth));

                        HashMap updateMap = new HashMap();
                        updateMap.put(args[1].toUpperCase(), args[2].toUpperCase());
                        Clients.broadcastUpdate(updateMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                        printCommand("Interposed " + args[2].toUpperCase() + " to berth " + berth.getBerthDescription(), false);
                    }
                    else
                        printCommand("Unrecognised berth id", true);
                }
                break;

            case CANCEL:
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

                        HashMap updateMap = new HashMap();
                        updateMap.put(args[1].toUpperCase(), "");
                        Clients.broadcastUpdate(updateMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                        printCommand("Cancelled train in berth " + berth.getBerthDescription(), false);
                    }
                    else
                        printCommand("Unrecognised berth id (" + args[1].toUpperCase() + ")", true);
                }
                break;

            case PROBLEM:
                if (args.length != 3 || (!args[2].toLowerCase().equals("true") && !args[2].toLowerCase().equals("false")))
                    printCommand("Usage: problem <berth_id> <true|false>", true);
                else
                {
                    Berth berth = Berths.getBerth(args[1].toUpperCase());

                    if (berth != null)
                    {
                        berth.setProblematicBerth(args[2].toLowerCase().equals("true"));

                        /*HashMap updateMap = new HashMap();
                        updateMap.put(args[1].toUpperCase(), args[2].toLowerCase().equals("true"));
                        Clients.broadcastUpdate(updateMap);*/

                        printCommand("Set berth " + args[1].toUpperCase() + " to " + (args[2].toLowerCase().equals("false") ? "not " : "") + "problematic", false);
                    }
                    else
                        printCommand("Unrecognised berth id", true);
                }
                break;

            case SEARCH:
                if (args.length != 2 && args.length != 3)
                    printCommand("Usage: search <headcode [area] | station>", true);
                else if (args.length == 2)
                    try
                    {
                        Desktop.getDesktop().browse(new URI("http://www.realtimetrains.co.uk/search/advancedhandler?type=advanced&qs=true&search=" + args[1].toUpperCase()));
                    }
                    catch (IOException | URISyntaxException e) { printCommand("" + e, true); }
                else
                    try
                    {
                        Desktop.getDesktop().browse(new URI("http://www.realtimetrains.co.uk/search/advancedhandler?type=advanced&qs=true&search=" + args[1].toUpperCase() + "&area=" + args[2].toUpperCase()));
                    }
                    catch (IOException | URISyntaxException e) { printCommand("" + e, true); }
                break;

            case GET:
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

            case SEND_ALL:
                Clients.broadcastUpdate(EastAngliaSignalMapServer.CClassMap);
                printCommand("Broadcast full map to all clients", false);
                break;

            case ADD_BERTH:
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

            case RECONNECT:
                StompConnectionHandler.reconnect();

                break;

            case SLEEP:
                if (args.length != 1 && args.length != 2 && !(args[1].toLowerCase().equals("true") || args[1].toLowerCase().equals("false")))
                    printCommand("Usage: sleep [true|false]", true);
                else
                    if (args.length == 1)
                        printCommand(TimerMethods.sleep() ? "Stopped preventing sleeping" : "Prevented sleeping", false);
                    else
                        printCommand(TimerMethods.sleep(args[1].toLowerCase().equals("true")) ? "Stopped prevented sleeping" : "Preventing sleeping", false);
                break;

            case OPEN_LOG:
                try
                {
                    Desktop.getDesktop().open(EastAngliaSignalMapServer.logFile);
                    printCommand("Opened log file", false);
                }
                catch (IOException e)
                {
                    printCommand("Unable to open log file:\n" + e, true);
                }
                break;

            case NAME_BERTH:
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

            case MISSING:
                Berths.printIds();
                break;

            case ALL:
                StompConnectionHandler.printCClass("All Berth Ids:", false);

                for (String berthId : Berths.getKeySet())
                    StompConnectionHandler.printCClass(berthId, false);

                break;

            case SAVE_MAP:
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
                        berthDetail.put("train_hist", berth.getTrainsHistory());

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

                    printCommand("Saved map", false);
                }
                catch (IOException e)
                {
                    printCommand("Failed to save map\n" + e, true);
                }
                break;

            case "reset":
                ServerGui oldGui = EastAngliaSignalMapServer.gui;

                if (args.length == 2 && args[1].toLowerCase().equals("size"))
                    EastAngliaSignalMapServer.gui = new ServerGui();
                else
                {
                    int[] dims = oldGui.getDims();
                    EastAngliaSignalMapServer.gui = new ServerGui(dims[0], dims[1], dims[2], dims[3]);
                }

                oldGui.dispose();
                break;

            case "client_history":
                new ListDialog("Client history", "History of client stuffs", Clients.clientsHistory);
                break;

            case "gc":
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