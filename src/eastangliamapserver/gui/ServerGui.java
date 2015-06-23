package eastangliamapserver.gui;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.CommandHandler;
import eastangliamapserver.CustomOutStream;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.server.Clients;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

public class ServerGui
{
    public final JFrame frame;

    private final JList<String> clientList;
    //private final JList<String> dataList;
    private final JTextArea     logTextArea;
    private final JTextField    commandInput;

    public final Object logLock = new Object();

    public ServerGui(int x, int y, int width, int height)
    {
        this();

        frame.setSize(width, height);
        frame.setLocation(x, y);
    }

    public ServerGui()
    {
        frame = new JFrame("East Anglia Signal Map - Server (v" + EastAngliaSignalMapServer.VERSION + ")");

        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.setMinimumSize(new Dimension(870, 520));
        frame.setPreferredSize(new Dimension(870, 520));
        frame.setMaximumSize(new Dimension(1920, 1080));
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent evt)
            {
                if (SystemTray.isSupported())
                    frame.setVisible(false);
                else if (JOptionPane.showConfirmDialog(frame, "Are you sure you wish to close?", "Shutting down", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                    EastAngliaSignalMapServer.stop();
            }
        });

        JPanel logPanel = new JPanel(new BorderLayout(3, 3));
        logPanel.setBorder(new TitledBorder(new EtchedBorder(), "Server Log & Commands"));
        logPanel.setPreferredSize(new Dimension(854, 241));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(false);

        setLogStuff();

        logPanel.add(new JScrollPane(logTextArea), BorderLayout.CENTER);

        commandInput = new JTextField();
        commandInput.addActionListener(evt ->
        {
            commandInput.setText("");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());

            if (!evt.getActionCommand().trim().isEmpty())
            {
                String[] command = evt.getActionCommand().split(" ");
                CommandHandler.handle(command[0], command);
            }
        });
        logPanel.add(commandInput, BorderLayout.SOUTH);

        JPanel monitorPanel = new JPanel(new BorderLayout());
        monitorPanel.setPreferredSize(new Dimension(854, 241));

        clientList = new JList<>(new DefaultListModel<>());
        clientList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                JList<String> list = (JList<String>) evt.getSource();

                if (SwingUtilities.isRightMouseButton(evt) && !list.isSelectionEmpty() && list.locationToIndex(evt.getPoint()) == list.getSelectedIndex())
                    new ClientContextMenu(list, evt.getX(), evt.getY());
            }
        });

        clientList.setFont(new Font("Monospaced", 0, 12));

        JScrollPane jspClients = new JScrollPane(clientList);
        jspClients.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jspClients.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JPanel pnlClients = new JPanel(new BorderLayout());
        pnlClients.add(jspClients);
        pnlClients.setBorder(new TitledBorder(new EtchedBorder(), "Clients"));

        monitorPanel.add(pnlClients, BorderLayout.WEST);
        updateClientList();

        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new BorderLayout());
        dataPanel.setOpaque(true);
        dataPanel.setBackground(Color.WHITE);

        JPanel pnlDataText = new JPanel(new FlowLayout());
        pnlDataText.setOpaque(true);
        pnlDataText.setBackground(Color.WHITE);
        JPanel pnlDataControl = new JPanel(new FlowLayout());
        pnlDataControl.setOpaque(true);
        pnlDataControl.setBackground(Color.WHITE);

        JLabel clockLabel = new JLabel(EastAngliaSignalMapServer.sdfTime.format(new Date()));
        clockLabel.setFont(new Font(Font.MONOSPACED, Font.TRUETYPE_FONT, 22));
        clockLabel.setHorizontalAlignment(SwingConstants.CENTER);
        clockLabel.setPreferredSize(new Dimension(500, 27));
        dataPanel.add(clockLabel, BorderLayout.NORTH);

        JLabel statsLabel = new JLabel("<html><pre>Last Message: <br>Uptime: <br>   CPU:<br>   RAM:</pre></html>");
        statsLabel.setFont(new Font(Font.MONOSPACED, Font.TRUETYPE_FONT, 12));
        statsLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statsLabel.setPreferredSize(new Dimension(500, 65));
        pnlDataText.add(statsLabel);

        JLabel motdLabel = new JLabel("<html>MOTD: \"\"</html>");
        motdLabel.setFont(new Font(Font.MONOSPACED, Font.TRUETYPE_FONT, 12));
        motdLabel.setHorizontalAlignment(SwingConstants.LEFT);
        motdLabel.setVerticalAlignment(SwingConstants.TOP);
        motdLabel.setPreferredSize(new Dimension(500, 30));
        pnlDataText.add(motdLabel);

        JButton dataGuiButton = new JButton("Data Viewer");
        dataGuiButton.addActionListener(e -> EastAngliaSignalMapServer.guiData.setVisible0(true));
        pnlDataControl.add(dataGuiButton);

        JButton controlMenu = new JButton("▲");
        controlMenu.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                if (SwingUtilities.isLeftMouseButton(evt))
                    new ControlContextMenu(evt.getComponent());
            }
        });
        pnlDataControl.add(controlMenu);

        dataPanel.add(pnlDataText, BorderLayout.CENTER);
        dataPanel.add(pnlDataControl, BorderLayout.SOUTH);
        dataPanel.setBorder(new TitledBorder(new EtchedBorder(), "Status"));
        monitorPanel.add(dataPanel, BorderLayout.CENTER);

        final RuntimeMXBean rtMxBean = ManagementFactory.getRuntimeMXBean();
        final com.sun.management.OperatingSystemMXBean osMxBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        final int numProcessors = osMxBean.getAvailableProcessors();
        final AtomicLong lastUptime = new AtomicLong(rtMxBean.getUptime());
        final AtomicLong lastProcessCpuTime = new AtomicLong(osMxBean.getProcessCpuTime());

        ActionListener listenerTimer = evt ->
        {
            try
            {
                clockLabel.setText(EastAngliaSignalMapServer.sdfTime.format(new Date()));

                Berth motdBerth = Berths.getBerth("XXMOTD");
                motdLabel.setText("<html>MOTD: \"" + (motdBerth == null ? "" : motdBerth.getHeadcode().trim()) + "\"</html>");

                String stats = "<html><pre>";
                long timeLastMessage = System.currentTimeMillis() - StompConnectionHandler.lastMessageTime;
                stats += (String.format("Last Message: %s (%02d:%02d:%02d)",
                        EastAngliaSignalMapServer.sdfTime.format(new Date(StompConnectionHandler.lastMessageTime)),
                        (timeLastMessage / (3600000)) % 24,
                        (timeLastMessage / (60000)) % 60,
                        (timeLastMessage / 1000) % 60)
                        + (EastAngliaSignalMapServer.stompOffline   ? " - offline" :
                            ((!StompConnectionHandler.isConnected() ? " - disconnected" : "")
                            + (StompConnectionHandler.isClosed()    ? " - closed" : "")
                            + (StompConnectionHandler.isTimedOut()  ? " - timed out" : ""))));
                stats += "<br>";

                long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
                stats += (String.format("Uptime: %02dd %02dh %02dm %02ds (%s)",
                        (uptime / (86400000)),
                        (uptime / (3600000)) % 24,
                        (uptime / (60000)) % 60,
                        (uptime / 1000) % 60,
                        EastAngliaSignalMapServer.sdfDateTimeShort.format(ManagementFactory.getRuntimeMXBean().getStartTime()))
                        + (EastAngliaSignalMapServer.serverOffline ? " - offline" : (EastAngliaSignalMapServer.server != null && EastAngliaSignalMapServer.server.isClosed() ? " - closed" : "")));
                stats += "<br>";

                long processCpuTime = osMxBean.getProcessCpuTime();
                if (lastUptime.get() > 0L && uptime > lastUptime.get())
                {
                    long elapsedCpu = processCpuTime - lastProcessCpuTime.get();
                    long elapsedTime = uptime - lastUptime.get();
                    stats += (String.format("   CPU: %.2f%%", elapsedCpu / (elapsedTime * 10000F * numProcessors)));
                }
                else
                    stats += "   CPU: --.--%";
                stats += "<br>";

                lastUptime.set(uptime);
                lastProcessCpuTime.set(processCpuTime);
                MemoryMXBean mmxb = ManagementFactory.getMemoryMXBean();
                long nonHeap = mmxb.getNonHeapMemoryUsage().getUsed();
                long heap    = mmxb.getHeapMemoryUsage().getUsed();
                stats += (String.format("   RAM: %.2fMiB (heap: %.2fMiB, non-heap: %.2fMiB)", (heap + nonHeap) / 1048576f, heap / 1048576f, nonHeap / 1048576f));
                stats += "<br></pre></html>";

                statsLabel.setText(stats);

            }
            catch (Exception e) { e.printStackTrace(); }

            if (EastAngliaSignalMapServer.guiData != null)
                EastAngliaSignalMapServer.guiData.updateData();
        };
        // Do an initial update
        listenerTimer.actionPerformed(null);
        new Timer(250, listenerTimer).start();

        frame.add(monitorPanel, BorderLayout.CENTER);
        frame.add(logPanel,     BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/eastangliamapserver/resources/Icon.png")));

        frame.setVisible(true);
    }

    public void updateClientList()
    {
        DefaultListModel<String> model = new DefaultListModel<>();
        List<String> clientNames = Clients.getClientList();
        Collections.sort(clientNames, String.CASE_INSENSITIVE_ORDER);
        clientNames.stream().forEachOrdered(client -> model.addElement(client));
        clientList.setModel(model);
    }

    /*public void updateDataList()
    {
        int listSelection = dataList.getSelectedIndex();

        List<String> newData = new ArrayList<>();

        String[] sortedKeys = EastAngliaSignalMapServer.SClassMap.keySet().toArray(new String[0]);
        Arrays.sort(sortedKeys);

        long time = System.currentTimeMillis() - StompConnectionHandler.lastMessageTime;
        newData.add("Current Time:  " + EastAngliaSignalMapServer.sdfTime.format(new Date()));
        newData.add(String.format("Last Message:  %s (%02d:%02d:%02d)",
                        EastAngliaSignalMapServer.sdfTime.format(new Date(StompConnectionHandler.lastMessageTime)),
                        (time / (3600000)) % 24,
                        (time / (60000)) % 60,
                        (time / 1000) % 60)
                    + (!StompConnectionHandler.isConnected() ? " - disconnected" : "")
                    + (StompConnectionHandler.isClosed()? " - closed" : "")
                    + (StompConnectionHandler.isTimedOut() ? " - timed out" : ""));
        time = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        newData.add(String.format("Server Uptime: %02dd %02dh %02dm %02ds (%s)",
                        (time / (86400000)),
                        (time / (3600000)) % 24,
                        (time / (60000)) % 60,
                        (time / 1000) % 60,
                        EastAngliaSignalMapServer.sdfDateTimeShort.format(ManagementFactory.getRuntimeMXBean().getStartTime()))
                    + (EastAngliaSignalMapServer.server == null || EastAngliaSignalMapServer.server.isClosed() ? " - closed" : ""));
        newData.add(String.format("Memory use:    %.3fmb (f %sb, t %sb, m %sb)", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576f, Runtime.getRuntime().freeMemory(), Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory()));

        Berth motdBerth = Berths.getBerth("XXMOTD");
        if (motdBerth != null && !motdBerth.getHeadcode().trim().equals(""))
            newData.add("<html><pre>MOTD:          &quot;" + motdBerth.getHeadcode().trim() + "&quot;</pre></html>");

        newData.add(" ");
        newData.add("------ C Class Data (#" + EastAngliaSignalMapServer.CClassMap.size() + ") ---------------");
        newData.addAll(Berths.getCClassData(true));
      //newData.addAll(Berths.getCClassData(false));

        String[] sclassIds = EastAngliaSignalMapServer.SClassMap.keySet().toArray(new String[0]);
        Arrays.sort(sclassIds);
        newData.add(" ");
        newData.add("------ S Class Data (#" + EastAngliaSignalMapServer.SClassMap.size() + ") ---------------");
        for (String sclassId : sclassIds)
            newData.add((sclassId.contains(":") ? sclassId : "- " + sclassId) + ": " + String.valueOf(EastAngliaSignalMapServer.SClassMap.get(sclassId)));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String element : newData)
            model.addElement(element);

        dataList.setModel(model);

        if (listSelection > -1)
            dataList.setSelectedIndex(Math.min(listSelection, dataList.getMaxSelectionIndex()));

        dataList.repaint();
    }*/

    public void stop()
    {
        commandInput.setEnabled(false);
    }

    public void restart()
    {
        commandInput.setEnabled(true);
    }

    public void dispose()
    {
        frame.dispose();
    }

    private void setLogStuff()
    {
        System.setOut(new PrintStream(new CustomOutStream(logTextArea, System.out), true)
        {
            @Override
            public void println(Object x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(String x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(boolean x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(char x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(char[] x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(double x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(float x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(int x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println(long x) { synchronized (logLock) { super.println(x); }}
            @Override
            public void println() { synchronized (logLock) { super.println(); }}
        });
        System.setErr(new PrintStream(new CustomOutStream(logTextArea, System.err), true)
        {
                @Override
                public void println(Object x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(String x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(boolean x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(char x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(char[] x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(double x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(float x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(int x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println(long x) { synchronized (logLock) { super.println(x); }}
                @Override
                public void println() { synchronized (logLock) { super.println(); }}
        });
    }
}