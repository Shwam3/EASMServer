package eastangliamapserver.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class ClientContextMenu extends JPopupMenu
{
    JMenuItem kick;

    ActionListener clickEvent = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent evt)
        {
            //Do things
        }
    };

    public ClientContextMenu(Component invoker, int x, int y)
    {
        kick = new JMenuItem("Kick");

        kick.addActionListener(clickEvent);

        add(kick);

        show(invoker, x, y);
        requestFocus();
    }
}