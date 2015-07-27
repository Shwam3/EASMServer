package eastangliamapserver.gui;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.Train;
import eastangliamapserver.server.Clients;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

public class ControlContextMenu extends JPopupMenu
{
    JCheckBoxMenuItem disableServer;
    JCheckBoxMenuItem disableStomp;
    //JCheckBoxMenuItem toggleVerbosity;

    JMenuItem editMOTD;
    JMenuItem stompReconnect;
    JMenuItem trainHistory;

    JMenu editCClass;
    JMenuItem editCClassInterpose;
    JMenuItem editCClassCancel;
    JMenuItem editCClassStep;
    JMenu editSClass;
    JMenuItem editSClassSet;

    JMenuItem serverKickAll;
    JMenuItem serverMessage;
    JMenuItem maxClients;

    //JCheckBoxMenuItem preventSleep;
    //JCheckBoxMenuItem minToSysTray;
    JMenuItem exit;

    ActionListener clickEvent = evt ->
    {
        Object src = evt.getSource();
        if (src == disableServer)
        {
            if (EastAngliaSignalMapServer.serverOffline)
            {
                if (EastAngliaSignalMapServer.server == null || EastAngliaSignalMapServer.server.isClosed())
                {
                    EastAngliaSignalMapServer.serverOffline = false;
                    try { EastAngliaSignalMapServer.server = new ServerSocket(EastAngliaSignalMapServer.port, 2); }
                    catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "Server"); }
                }
            }
            else //if (!EastAngliaSignalMapServer.serverOffline))
            {
                if (EastAngliaSignalMapServer.server != null && !EastAngliaSignalMapServer.server.isClosed())
                {
                    EastAngliaSignalMapServer.serverOffline = true;
                    try { EastAngliaSignalMapServer.server.close(); }
                    catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "Server"); }
                }
            }
        }
        else if (src == disableStomp)
        {
            if (EastAngliaSignalMapServer.stompOffline)
            {
                EastAngliaSignalMapServer.stompOffline = false;
                StompConnectionHandler.disconnect();
                StompConnectionHandler.wrappedConnect();
            }
            else //if (!EastAngliaSignalMapServer.stompOffline)
            {
                EastAngliaSignalMapServer.stompOffline = true;
                if (StompConnectionHandler.isConnected() && !StompConnectionHandler.isClosed())
                    StompConnectionHandler.disconnect();
            }
        }
        else if (src == editMOTD)
        {
            String newMOTD = (String) JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Input a new MOTD", "Edit MOTD", JOptionPane.QUESTION_MESSAGE, null, null, EastAngliaSignalMapServer.CClassMap.getOrDefault("XXMOTD", ""));

            if (newMOTD != null)
            {
                Berth berth = Berths.getBerth("XXMOTD");

                newMOTD = newMOTD.replaceAll("%date%", EastAngliaSignalMapServer.sdfDate.format(new Date()));
                newMOTD = newMOTD.replaceAll("%time%", EastAngliaSignalMapServer.sdfTime.format(new Date()));
                newMOTD = newMOTD.trim();

                berth.interpose(new Train(newMOTD, berth));

                Map<String, String> motdMap = new HashMap<>();
                motdMap.put("XXMOTD", newMOTD);
                Clients.broadcastUpdate(motdMap);
                EastAngliaSignalMapServer.CClassMap.putAll(motdMap);

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
                        bw.write(newMOTD);
                        bw.write("\r\n");
                    }
                }
                catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "MOTD"); }
            }
        }
        else if (src == stompReconnect)
        {
            StompConnectionHandler.disconnect();
            StompConnectionHandler.wrappedConnect();
        }
        else if (src == trainHistory)
        {
            String UUID = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter Train UUID:", "Train History", JOptionPane.QUESTION_MESSAGE);

            if (UUID != null)
                if (UUID.length() >= 5 && UUID.matches("[0-9]+"))
                    Berths.getTrain(UUID);
                else
                    JOptionPane.showMessageDialog(EastAngliaSignalMapServer.guiServer.frame, "'" + UUID + "' is not a valid train UUID", "Error", JOptionPane.WARNING_MESSAGE);
        }
        else if (src == editCClassInterpose)
        {
            String berthStr = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter Berth Id", "Interpose", JOptionPane.QUESTION_MESSAGE);
            Berth berth = Berths.getBerth(berthStr);
            if (berth != null)
            {
                String newHC = (String) JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter New Description", "Interpose", JOptionPane.QUESTION_MESSAGE, null, null, berth.getHeadcode());
                if (newHC != null)
                {
                    newHC = (newHC.length() > 4 ? newHC.substring(0, 4) : newHC).toUpperCase();
                    berth.interpose(new Train(newHC, berth));

                    Map<String, String> updateMap = new HashMap<>();
                    updateMap.put(berth.getId(), newHC);
                    Clients.broadcastUpdate(updateMap);
                    EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                    EastAngliaSignalMapServer.printOut("Interpose " + newHC + " to " + berth.getBerthDescription());
                }
            }
            else if (berthStr != null)
                EastAngliaSignalMapServer.printErr("Unrecognised berth id \"" + berthStr + "\"");
        }
        else if (src == editCClassCancel)
        {
            String berthStr = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter Berth Id", "Cancel", JOptionPane.QUESTION_MESSAGE);
            Berth berth = Berths.getBerth(berthStr);

            if (berth != null)
            {
                String hc = berth.getHeadcode();

                berth.cancel(hc, null);
                Map<String, String> updateMap = new HashMap<>();
                updateMap.put(berth.getId(), "");
                Clients.broadcastUpdate(updateMap);
                EastAngliaSignalMapServer.CClassMap.putAll(updateMap);

                EastAngliaSignalMapServer.printOut("Cancel " + hc + " from " + berth.getBerthDescription());
            }
            else if (berthStr != null)
                EastAngliaSignalMapServer.printErr("Unrecognised berth id \"" + berthStr + "\"");
        }
        else if (src == editCClassStep)
        {
            String fromBerthStr = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter from Berth Id", "Step", JOptionPane.QUESTION_MESSAGE).toUpperCase();

            if (fromBerthStr != null)
            {
                String toBerthStr = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter to Berth Id",   "Step", JOptionPane.QUESTION_MESSAGE).toUpperCase();

                if (toBerthStr != null)
                {
                    Berth fromBerth = Berths.getBerth(fromBerthStr);
                    Berth toBerth = Berths.getBerth(toBerthStr);
                    Train newTrain = null;

                    if (fromBerth == null)
                        EastAngliaSignalMapServer.printErr("Unrecognised berth id \"" + fromBerthStr + "\"");

                    if (toBerth == null)
                        EastAngliaSignalMapServer.printErr("Unrecognised berth id \"" + toBerthStr + "\"");

                    if (fromBerth != null && toBerth != null)
                    {
                        if (fromBerth.hasTrain())
                            newTrain = fromBerth.getTrain();
                        else
                            return;

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

                        EastAngliaSignalMapServer.printOut("Step " + toBerth.getHeadcode() + " from " + fromBerth.getBerthDescription() + " to " + toBerth.getBerthDescription());
                    }
                }
            }
        }
        else if (src == editSClassSet)
        {
            String bit = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter Bit Address", "Set", JOptionPane.QUESTION_MESSAGE).toUpperCase();

            if (bit != null)
            {
                String value = (String)JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter Bit Address", "Set", JOptionPane.QUESTION_MESSAGE, null, new String[]{"0", "1"}, "0");

                if (value != null)
                {
                    if (bit.length() == 6)
                    {
                        String from = EastAngliaSignalMapServer.SClassMap.getOrDefault(bit, "0");
                        EastAngliaSignalMapServer.SClassMap.put(bit, value);
                        Clients.scheduleForNextUpdate(bit, value);
                        EastAngliaSignalMapServer.printOut("Change " + bit + " from " + from + " to " + value);
                    }
                    else
                        EastAngliaSignalMapServer.printErr("Invalid bit address \"" + bit + "\"");
                }
            }
        }
        else if (src == serverKickAll)
        {
            Clients.kickAll(null);
        }
        else if (src == serverMessage)
        {
            String message = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Add a reason", "Kick all clients", JOptionPane.QUESTION_MESSAGE);

            if (message != null && !message.isEmpty())
                Clients.getClientList().stream().filter(c -> Clients.getClient(c) != null).forEach(c -> Clients.getClient(c).sendTextMessage(message));
        }
        else if (src == maxClients)
        {
            String maxClientsStr = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Enter new maximum: ", "Max no of Clients", JOptionPane.QUESTION_MESSAGE);

            if (maxClientsStr != null)
            {
                try
                {
                    int maxClientsOld = Clients.getMaxClients();
                    int maxClientsNew = Integer.parseInt(maxClientsStr);
                    Clients.setMaxClients(maxClientsNew);

                    EastAngliaSignalMapServer.printOut("Max clients changed from " + maxClientsOld + " to " + maxClientsNew);
                }
                catch (NumberFormatException e) {}
            }
        }
        else if (src == exit)
        {
            int ret = JOptionPane.showConfirmDialog(EastAngliaSignalMapServer.guiServer.frame, "Do you wish to save the state?", "Save & Exit", JOptionPane.YES_NO_CANCEL_OPTION);
            if (ret == JOptionPane.YES_OPTION)
            {
                EastAngliaSignalMapServer.stompOffline = true;
                StompConnectionHandler.disconnect();

                EastAngliaSignalMapServer.saveMap();
            }

            if (ret == JOptionPane.YES_OPTION || ret == JOptionPane.NO_OPTION)
                System.exit(0);
        }
    };

    public ControlContextMenu(Component invoker)
    {
        super();

        disableServer   = new JCheckBoxMenuItem("Disable Server", EastAngliaSignalMapServer.serverOffline);
        disableStomp    = new JCheckBoxMenuItem("Disable Stomp",  EastAngliaSignalMapServer.stompOffline);
      //toggleVerbosity = new JCheckBoxMenuItem("Verbose Log",    EastAngliaSignalMapServer.verbose);
        editMOTD        = new JMenuItem("Edit MOTD");
        stompReconnect  = new JMenuItem("Stomp Reconnect");
        trainHistory    = new JMenuItem("Train History");

        editCClass      = new JMenu("C Class");
        editCClassInterpose = new JMenuItem("Interpose");
        editCClassCancel    = new JMenuItem("Cancel");
        editCClassStep      = new JMenuItem("Step");

        editSClass      = new JMenu("S Class");
        editSClassSet       = new JMenuItem("Set Bit");

        serverKickAll   = new JMenuItem("Kick All");
        serverMessage   = new JMenuItem("Client Message");
      //preventSleep    = new JCheckBoxMenuItem("Keep your PC Awake",      EastAngliaMapClient.preventSleep);
      //minToSysTray    = new JCheckBoxMenuItem("Minimise to System Tray", EastAngliaMapClient.minimiseToSysTray);
        exit            = new JMenuItem("Exit");

        add(disableServer).addActionListener(clickEvent);
        add(disableStomp).addActionListener(clickEvent);
        addSeparator();
        add(editMOTD).addActionListener(clickEvent);
        add(stompReconnect).addActionListener(clickEvent);
        add(trainHistory).addActionListener(clickEvent);
        addSeparator();
        editCClass.add(editCClassInterpose).addActionListener(clickEvent);
        editCClass.add(editCClassCancel).addActionListener(clickEvent);
        editCClass.add(editCClassStep).addActionListener(clickEvent);
        add(editCClass);
        editSClass.add(editSClassSet).addActionListener(clickEvent);
        add(editSClass);
        addSeparator();
        add(serverKickAll).addActionListener(clickEvent);
        add(serverMessage).addActionListener(clickEvent);
        addSeparator();
      //add(preventSleep).addActionListener(clickEvent);
      //add(minToSysTray).addActionListener(clickEvent);
        add(exit).addActionListener(clickEvent);

        pack();

        show(invoker, /*-143 + 42*/ 1, -211);
        requestFocus();
    }
}