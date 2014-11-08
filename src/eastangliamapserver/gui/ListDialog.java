package eastangliamapserver.gui;

import eastangliamapserver.EastAngliaSignalMapServer;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;

public class ListDialog
{
    private JDialog dialog;

    public ListDialog(String title, String message, ArrayList<String> list)
    {
        if (list == null)
            list = new ArrayList<>();

        dialog = new JDialog();

        dialog.setTitle(title);
        dialog.setPreferredSize(new Dimension(305, 319));
        dialog.setResizable(true);
        dialog.setLayout(null);
        dialog.setLocationByPlatform(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setModalityType(ModalityType.APPLICATION_MODAL);

        JPanel pnl = new JPanel(null);
        pnl.setBounds(10, 10, 279, 271);

        if (message != null && !message.equals(""))
        {
            JLabel lblMessage = new JLabel(message);
            lblMessage.setVerticalAlignment(SwingConstants.CENTER);
            lblMessage.setHorizontalAlignment(SwingConstants.LEFT);
            lblMessage.setBounds(0, 0, pnl.getWidth(), 15);
            lblMessage.setToolTipText(message);
            pnl.add(lblMessage);
        }

        JList jList = new JList(list.toArray());
        jList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jList.setVisibleRowCount(5);
        jList.setLayoutOrientation(JList.VERTICAL);
        JScrollPane jListSP = new JScrollPane(jList);
        jListSP.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jListSP.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        if (message != null && !message.equals(""))
            jListSP.setBounds(0, 25, pnl.getWidth(), pnl.getHeight() - 58);
        else
            jListSP.setBounds(0, 0, pnl.getWidth(), pnl.getHeight() - 38);

        pnl.add(jListSP);

        JButton okButton = new JButton("OK");
        okButton.setBounds(102, pnl.getHeight() - 23, 73, 23);
        okButton.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                dialog.dispose();
            }
        });
        pnl.add(okButton);

        dialog.add(pnl);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(EastAngliaSignalMapServer.gui.frame);
        dialog.setVisible(true);

        SwingUtilities.getRootPane(okButton).setDefaultButton(okButton);
        dialog.getRootPane().setDefaultButton(okButton);
    }
}