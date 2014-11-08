package eastangliamapserver.gui;

import eastangliamapserver.*;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.awt.*;
import java.awt.event.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

public class ServerGui
{
    public final JFrame frame;

    private final DefaultListModel<String> clientListModel;
    private final JList<String>            dataList;
    private final JTextArea                logTextArea;
    private final JTextField               commandInput;

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
                EastAngliaSignalMapServer.stop();
            }
        });

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder(new EtchedBorder(), "Server Log & Commands"));
        logPanel.setPreferredSize(new Dimension(854, 241));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(false);

        try
        {
            FileInputStream in = new FileInputStream(EastAngliaSignalMapServer.logFile);

            String file = "";
            int read;

            while ((read = in.read()) != -1)
            {
                file += (char) read;
            }

            logTextArea.setText(logTextArea.getText() + file);
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) {}

        System.setOut(new PrintStream(new CustomOutStream(logTextArea, System.out), true)
        {
            @Override
            public void println(String message)
            {
                synchronized (logLock)
                {
                    super.println(message);
                }
            }
        });
        System.setErr(new PrintStream(new CustomOutStream(logTextArea, System.err), true)
        {
            @Override
            public void println(String message)
            {
                synchronized (logLock)
                {
                    super.println(message);
                }
            }
        });

        logPanel.add(new JScrollPane(logTextArea), BorderLayout.CENTER);

        commandInput = new JTextField();
        commandInput.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent evt)
            {
                commandInput.setText("");
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());

                if (!evt.getActionCommand().trim().isEmpty())
                    CommandHandler.handle(evt.getActionCommand().split(" ")[0], evt.getActionCommand().split(" "));
            }
        });
        logPanel.add(commandInput, BorderLayout.SOUTH);

        JPanel monitorPanel = new JPanel(new BorderLayout());
        monitorPanel.setPreferredSize(new Dimension(854, 241));

        clientListModel = new DefaultListModel<>();
        JList<String> clientList = new JList<>(clientListModel);

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

        JScrollPane clientScrollPane = new JScrollPane(clientList);

        clientScrollPane.setVerticalScrollBarPolicy(22);
        clientScrollPane.setHorizontalScrollBarPolicy(30);
        clientScrollPane.setBorder(new TitledBorder(new EtchedBorder(), "Clients"));

        monitorPanel.add(clientScrollPane, BorderLayout.WEST);
        updateClientList();

        dataList = new JList<>();
        dataList.setSelectionMode(0);
        dataList.setFont(new Font("Monospaced", 0, 12));
        JScrollPane dataScrollPane = new JScrollPane(dataList);
        dataScrollPane.setVerticalScrollBarPolicy(22);
        dataScrollPane.setHorizontalScrollBarPolicy(30);
        dataScrollPane.setBorder(new TitledBorder(new EtchedBorder(), "Data"));
        monitorPanel.add(dataScrollPane, BorderLayout.CENTER);
        updateDataList();

        new Timer(250, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    DefaultListModel<String> model = (DefaultListModel<String>) dataList.getModel();

                    long time = System.currentTimeMillis() - StompConnectionHandler.lastMessageTime;
                    model.setElementAt("Current Time:  " + EastAngliaSignalMapServer.sdf.format(new Date()), 0);
                    model.setElementAt(String.format("Last Message:  %s (%02d:%02d:%02d)",
                                EastAngliaSignalMapServer.sdf.format(new Date(StompConnectionHandler.lastMessageTime)),
                                (time / (3600000)) % 24,
                                (time / (60000)) % 60,
                                (time / 1000) % 60)
                            + (!StompConnectionHandler.isConnected() ? " - disconnected" : "")
                            + (StompConnectionHandler.isClosed()? " - closed" : "")
                            + (StompConnectionHandler.isTimedOut() ? " - timed out" : ""), 1);

                    time = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
                    model.setElementAt(String.format("Server Uptime: %02dd %02dh %02dm %02ds (%s)",
                                (time / (86400000)),
                                (time / (3600000)) % 24,
                                (time / (60000)) % 60,
                                (time / 1000) % 60,
                                new SimpleDateFormat("dd/MM HH:mm:ss").format(ManagementFactory.getRuntimeMXBean().getStartTime()))
                            + (EastAngliaSignalMapServer.server == null || EastAngliaSignalMapServer.server.isClosed() ? " - closed" : ""), 2);

                    model.setElementAt(String.format("Memory use:    %s mb"/* (f %s, t %s, m %s)"*/, (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576/*, Runtime.getRuntime().freeMemory(), Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory()*/), 3);

                    dataList.setModel(model);
                }
                finally
                {}
            }
        }).start();

        frame.add(monitorPanel, BorderLayout.CENTER);
        frame.add(logPanel,     BorderLayout.SOUTH);
        frame.setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/eastangliamapserver/resources/Icon.png")));

        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);
    }

    public void updateClientList()
    {
        clientListModel.clear();

        for (String client : Clients.getClientList())
            clientListModel.addElement(client);
    }

    public void updateDataList()
    {
        int listSelection = Math.max(dataList.getSelectedIndex(), 0);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String dataEntry : Berths.getCClassData(true))
            listModel.addElement(dataEntry);

        dataList.setModel(listModel);
        dataList.setSelectedIndex(Math.min(listSelection, dataList.getMaxSelectionIndex()));
    }

    public void stop()
    {
        commandInput.setEnabled(false);
    }

    public int[] getDims()
    {
        return new int[] {frame.getLocation().x, frame.getLocation().y, frame.getSize().width, frame.getSize().height};
    }

    public void dispose()
    {
        frame.dispose();
    }
}