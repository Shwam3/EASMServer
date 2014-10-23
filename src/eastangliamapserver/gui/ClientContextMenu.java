package eastangliamapserver.gui;

import eastangliamapserver.*;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class ClientContextMenu extends JPopupMenu
{
    JList invoker;

    JMenuItem kick;
    JMenuItem history;

    ActionListener clickEvent = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent evt)
        {
            if (evt.getSource() == kick)
            {
                if (JOptionPane.showConfirmDialog(invoker, "Are you sure you want to kick this client?", "Are you sure?", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                {
                    String clientName = (String) invoker.getSelectedValue();
                    Client client = Clients.getClient(clientName.substring(clientName.lastIndexOf("(") + 1, clientName.length() - 1));

                    if (client != null)
                        client.disconnect();
                }
            }
            else if (evt.getSource() == history)
            {
                String clientName = (String) invoker.getSelectedValue();
                Client client = Clients.getClient(clientName.substring(clientName.lastIndexOf("(") + 1, clientName.length() - 1));

                if (client != null)
                    new ListDialog("Client " + client.name + "'s history", null, client.getHistory());
            }
            else
                EastAngliaSignalMapServer.printErr("[Client cnxmnu] \"" + evt.getSource() + "\" is not a valid source");
        }
    };

    public ClientContextMenu(Component invoker, int x, int y)
    {
        this.invoker = (JList) invoker;

        kick    = new JMenuItem("Kick");
        history = new JMenuItem("History");

        kick.addActionListener(clickEvent);
        history.addActionListener(clickEvent);

        add(kick);
        add(history);

        show(invoker, x, y);
        requestFocus();
    }
}