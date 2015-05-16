package eastangliamapserver.gui;

import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.server.Client;
import eastangliamapserver.server.Clients;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

public class ClientContextMenu extends JPopupMenu
{
    JList<String> invoker;
    String selectedValue;

    JMenuItem kick;
    JMenuItem message;
    JMenuItem info;
    JMenuItem history;

    ActionListener clickEvent = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent evt)
        {
            Client client = Clients.getClient(selectedValue.substring(selectedValue.lastIndexOf("(") + 1, selectedValue.length() - 1));

            if (evt.getSource() == kick)
            {
                if (JOptionPane.showConfirmDialog(invoker, "Are you sure you want to kick this client?", "Are you sure?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                {
                    String reason = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Add a kick message:");

                    if (client != null)
                        client.disconnect("You have been kicked" + (reason != null && !reason.equals("") ? ": " + reason : " (no reason given)"));
                }
            }
            else if (evt.getSource() == message)
            {
                String message = JOptionPane.showInputDialog(EastAngliaSignalMapServer.guiServer.frame, "Message to send:");

                if (client != null && message != null && !message.isEmpty())
                    client.sendTextMessage(message);
            }
            else if (evt.getSource() == info)
            {
                if (client != null)
                    new ListDialog("Info for \"" + client.getName() + "\"", null, client.getInfo());
            }
            else if (evt.getSource() == history)
            {
                if (client != null)
                    new ListDialog("Client " + client.getName() + "'s history", null, client.getHistory());
            }
            else
                EastAngliaSignalMapServer.printErr("[Client cnxmnu] \"" + evt.getSource() + "\" is not a valid source");
        }
    };

    public ClientContextMenu(Component invoker, int x, int y)
    {
        this.invoker = (JList<String>) invoker;
        selectedValue = this.invoker.getSelectedValue();

        kick    = new JMenuItem("Kick");
        message = new JMenuItem("Message");
        info    = new JMenuItem("Info");
        history = new JMenuItem("History");

        kick.addActionListener(clickEvent);
        message.addActionListener(clickEvent);
        info.addActionListener(clickEvent);
        history.addActionListener(clickEvent);

        add(kick);
        addSeparator();
        add(message);
        add(info);
        add(history);

        show(invoker, x, y);
        requestFocus();
    }
}