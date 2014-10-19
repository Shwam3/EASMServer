package eastangliamapserver.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

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
            /*if (evt.getSource() == kick)
            {

            }
            else if (evt.getSource() == history)
            {
                invoker.getSelectedIndex();
            }
            else
                EastAngliaSignalMapServer.printErr("[Client cm] \"" + evt.getSource() + "\" is not a valid source");*/
        }
    };

    public ClientContextMenu(Component invoker, int x, int y)
    {
        this.invoker = (JList) invoker;

        kick    = new JMenuItem("Kick");
        history = new JMenuItem("History");

        kick.addActionListener(clickEvent);

        add(kick);

        show(invoker, x, y);
        requestFocus();
    }
}