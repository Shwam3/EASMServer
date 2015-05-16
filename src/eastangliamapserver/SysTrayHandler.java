package eastangliamapserver;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SysTrayHandler
{
    private static TrayIcon trayIcon;

    public static void initSysTray()
    {
        if (SystemTray.isSupported())
        {
            ActionListener actionListener = (ActionEvent evt) ->
            {
                EastAngliaSignalMapServer.guiServer.frame.setVisible(true);
                EastAngliaSignalMapServer.guiServer.frame.requestFocus();
            };
            MouseListener mouseListener = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent evt)
                {
                    trayIcon.setPopupMenu(getPopupMenu());
                }
            };

            try
            {
                trayIcon = new TrayIcon(ImageIO.read(SysTrayHandler.class.getResource("/eastangliamapserver/resources/TrayIcon.png")));
                trayIcon.setToolTip("East Anglia Signal Map Server - v" + EastAngliaSignalMapServer.VERSION);
                trayIcon.setImageAutoSize(true);
                trayIcon.setPopupMenu(getPopupMenu());
                trayIcon.addActionListener(actionListener);
                trayIcon.addMouseListener(mouseListener);
                SystemTray.getSystemTray().add(trayIcon);
            }
            catch (IOException | AWTException e) {}
        }
    }

    private static PopupMenu getPopupMenu()
    {
        final PopupMenu pm = new PopupMenu();
        final MenuItem exit = new MenuItem("Exit");
        final MenuItem showWindow = new MenuItem("Show window");

        ActionListener menuListener = (ActionEvent evt) ->
        {
            Object src = evt.getSource();
            if (src == exit)
                EastAngliaSignalMapServer.stop();
            else if (src == showWindow)
                EastAngliaSignalMapServer.guiServer.frame.setVisible(true);
        };

        showWindow.addActionListener(menuListener);
        exit.addActionListener(menuListener);

        pm.add(showWindow);
        pm.addSeparator();
        pm.add(exit);

        return pm;
    }

    public static void popup(String message, TrayIcon.MessageType type)
    {
        if (trayIcon != null)
            trayIcon.displayMessage("East Anglia Signal Map Server - v" + EastAngliaSignalMapServer.VERSION, message, type);
    }

    public static void trayTooltip(String tooltip)
    {
        if (trayIcon != null)
            trayIcon.setToolTip("East Anglia Signal Map Server - v" + EastAngliaSignalMapServer.VERSION + (tooltip.equals("") ? "" : "\n") + tooltip);
    }
}