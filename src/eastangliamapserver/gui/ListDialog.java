package eastangliamapserver.gui;

import eastangliamapserver.EastAngliaSignalMapServer;
import java.awt.BorderLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

public class ListDialog
{
    private JDialog dialog;

    public ListDialog(String title, String message, List<String> list)
    {
        if (list == null)
            list = new ArrayList<>();

        dialog = new JDialog();
        dialog.setIconImage(EastAngliaSignalMapServer.guiServer.frame.getIconImage());

        dialog.setTitle(title);
        dialog.setPreferredSize(new Dimension(305, 319));
        dialog.setResizable(true);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationByPlatform(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setModalityType(ModalityType.APPLICATION_MODAL);

        JPanel pnl = new JPanel(null);
        pnl.setLayout(new BorderLayout(10, 10));
        pnl.setBorder(new EmptyBorder(10, 10, 10, 10));

        if (message != null && !message.equals(""))
        {
            JLabel lblMessage = new JLabel(message);
            lblMessage.setVerticalAlignment(SwingConstants.CENTER);
            lblMessage.setHorizontalAlignment(SwingConstants.LEFT);
            lblMessage.setToolTipText(message);
            pnl.add(lblMessage);
        }

        JList<String> jList = new JList<>(list.toArray(new String[0]));
        jList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jList.setVisibleRowCount(5);
        jList.setLayoutOrientation(JList.VERTICAL);
        JScrollPane jListSP = new JScrollPane(jList);
        jListSP.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jListSP.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pnl.add(jListSP, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.setBounds(102, pnl.getHeight() - 23, 73, 23);
        okButton.addActionListener((ActionEvent e) -> { dialog.dispose(); });
        JPanel buttonPnl = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPnl.add(okButton);
        pnl.add(buttonPnl, BorderLayout.SOUTH);

        dialog.getRootPane().registerKeyboardAction((ActionEvent e) -> { dialog.dispose(); }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
        dialog.getRootPane().registerKeyboardAction((ActionEvent e) -> { dialog.dispose(); }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,  0), JComponent.WHEN_FOCUSED);

        dialog.add(pnl);
        dialog.pack();
        dialog.setLocationRelativeTo(EastAngliaSignalMapServer.guiServer.frame);

        okButton.requestFocusInWindow();
        dialog.setVisible(true);
    }
}