package eastangliamapserver.gui;

import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

public class DataGui extends JDialog
{
    public final JList<String> listCClass = new JList<>(new DefaultListModel<>());
    public final JList<String> listSClass = new JList<>(new DefaultListModel<>());

    private boolean includeBlanks = false;
    private boolean includeMissing = true;

    private boolean filterCA = true;
    private boolean filterCC = true;
    private boolean filterEN = true;
    private boolean filterLS = true;
    private boolean filterSE = true;
    private boolean filterSI = true;
    private boolean filterSO = false;
    private boolean filterSX = true;
    private boolean filterWG = true;
    private boolean filterXX = false;
    private String  filterString = "";

    public DataGui()
    {
        setTitle("Data");
        setPreferredSize(new Dimension(600, 400));
        setMinimumSize(new Dimension(715, 350));
        setLayout(new BorderLayout());
        setLocationByPlatform(true);
        setLocationRelativeTo(EastAngliaSignalMapServer.guiServer.frame);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        setIconImage(EastAngliaSignalMapServer.guiServer.frame.getIconImage());

        JPanel mainPanel = new JPanel(new BorderLayout());

        Font font = new Font("Monospaced", 0, 12);
        listCClass.setFont(font);
        listSClass.setFont(font);
        listCClass.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listCClass.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane jspCClass = new JScrollPane(listCClass);
        JScrollPane jspSClass = new JScrollPane(listSClass);
        jspCClass.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jspSClass.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jspCClass.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        jspSClass.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JPanel pnlCClass = new JPanel(new BorderLayout());
        JPanel pnlSClass = new JPanel(new BorderLayout());
        pnlCClass.add(jspCClass);
        pnlSClass.add(jspSClass);
        pnlCClass.setBorder(new TitledBorder(new EtchedBorder(), "C Class"));
        pnlSClass.setBorder(new TitledBorder(new EtchedBorder(), "S Class"));
        JSplitPane pnlSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        pnlSplit.add(pnlCClass, JSplitPane.LEFT);
        pnlSplit.add(pnlSClass, JSplitPane.RIGHT);
        mainPanel.add(pnlSplit, BorderLayout.CENTER);

        JPanel pnlFilters = new JPanel();
        pnlFilters.setBorder(new TitledBorder(new EtchedBorder(), "Filters"));
        pnlFilters.setPreferredSize(new Dimension(350, 60));
        JCheckBox cbBlank   = new JCheckBox("Blank berths");
        JCheckBox cbMissing = new JCheckBox("Missing berths");
        JCheckBox cbCA = new JCheckBox("CA");
        JCheckBox cbCC = new JCheckBox("CC");
        JCheckBox cbEN = new JCheckBox("EN");
        JCheckBox cbLS = new JCheckBox("LS");
        JCheckBox cbSE = new JCheckBox("SE");
        JCheckBox cbSI = new JCheckBox("SI");
        JCheckBox cbSO = new JCheckBox("SO");
        JCheckBox cbSX = new JCheckBox("SX");
        JCheckBox cbWG = new JCheckBox("WG");
        JCheckBox cbXX = new JCheckBox("XX");
        JTextField jtfFilter = new JTextField();
        jtfFilter.setPreferredSize(new Dimension(60, 20));
        cbBlank.setSelected(includeBlanks);
        cbMissing.setSelected(includeMissing);
        cbCA.setSelected(filterCA);
        cbCC.setSelected(filterCC);
        cbEN.setSelected(filterEN);
        cbLS.setSelected(filterLS);
        cbSE.setSelected(filterSE);
        cbSI.setSelected(filterSI);
        cbSO.setSelected(filterSO);
        cbSX.setSelected(filterSX);
        cbWG.setSelected(filterWG);
        cbXX.setSelected(filterXX);
        cbBlank.addItemListener(evt -> { includeBlanks = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbMissing.addItemListener(evt -> { includeMissing = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbCA.addItemListener(evt -> { filterCA = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbCC.addItemListener(evt -> { filterCC = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbEN.addItemListener(evt -> { filterEN = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbLS.addItemListener(evt -> { filterLS = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbSE.addItemListener(evt -> { filterSE = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbSI.addItemListener(evt -> { filterSI = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbSO.addItemListener(evt -> { filterSO = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbSX.addItemListener(evt -> { filterSX = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbWG.addItemListener(evt -> { filterWG = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        cbXX.addItemListener(evt -> { filterXX = evt.getStateChange() == ItemEvent.SELECTED; updateData(); });
        jtfFilter.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent evt) { super.keyReleased(evt); filterString = (((JTextField) evt.getComponent()).getText()).trim(); updateData(); }});
        pnlFilters.add(cbBlank);
        pnlFilters.add(cbMissing);
        pnlFilters.add(cbCA);
        pnlFilters.add(cbCC);
        pnlFilters.add(cbEN);
        pnlFilters.add(cbLS);
        pnlFilters.add(cbSE);
        pnlFilters.add(cbSI);
        pnlFilters.add(cbSO);
        pnlFilters.add(cbSX);
        pnlFilters.add(cbWG);
        pnlFilters.add(cbXX);
        pnlFilters.add(jtfFilter, BorderLayout.CENTER);
        mainPanel.add(pnlFilters, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> setVisible0(false));
        okButton.setPreferredSize(new Dimension(73, 23));
        okButton.setOpaque(false);

        JPanel buttonPnl = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        buttonPnl.add(okButton);
        buttonPnl.setOpaque(false);
        add(buttonPnl, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> setVisible0(false), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> setVisible0(false), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(EastAngliaSignalMapServer.guiServer.frame);
    }

    public void setVisible0(boolean visible)
    {
        if (visible)
        {
            EventQueue.invokeLater(() -> setVisible(true));
            updateData();
        }
        else
        {
            setVisible(false);
            dispose();
        }
    }

    public void updateData()
    {
        if (isVisible())
        {
            setTitle("Data" + (filterString.isEmpty() ? "" : " - " + filterString));
            DefaultListModel<String> modelCClass = new DefaultListModel<>();
            //modelCClass.addElement("------ C Class Data (#" + EastAngliaSignalMapServer.CClassMap.size() + ") ---------------");
            //for (String element : Berths.getCClassData(false))
            for (String element : Berths.getCClassData(includeBlanks, includeMissing))
                if (elementFitsFilter(element))
                    modelCClass.addElement(element);
            listCClass.setModel(modelCClass);

            DefaultListModel<String> modelSClass = new DefaultListModel<>();
            String[] sclassIds = EastAngliaSignalMapServer.SClassMap.keySet().toArray(new String[0]);
            Arrays.sort(sclassIds);
            //modelSClass.addElement("------ S Class Data (#" + EastAngliaSignalMapServer.SClassMap.size() + ") ---------------");
            for (String sclassId : sclassIds)
                if (elementFitsFilter(sclassId))
                    modelSClass.addElement((sclassId.contains(":") ? sclassId : "- " + sclassId) + ": " + String.valueOf(EastAngliaSignalMapServer.SClassMap.get(sclassId)));
            listSClass.setModel(modelSClass);
        }
    }

    private boolean elementFitsFilter(String element)
    {
        return (
                (element.startsWith("CA") && filterCA) ||
                (element.startsWith("CC") && filterCC) ||
                (element.startsWith("EN") && filterEN) ||
                (element.startsWith("LS") && filterLS) ||
                (element.startsWith("SE") && filterSE) ||
                (element.startsWith("SI") && filterSI) ||
                (element.startsWith("SO") && filterSO) ||
                (element.startsWith("SX") && filterSX) ||
                (element.startsWith("WG") && filterWG) ||
                (element.startsWith("XX") && filterXX) ||
               !(element.startsWith("CA") || element.startsWith("CC") ||
                 element.startsWith("EN") || element.startsWith("LS") ||
                 element.startsWith("SE") || element.startsWith("SI") ||
                 element.startsWith("SO") || element.startsWith("SX") ||
                 element.startsWith("WG") || element.startsWith("XX"))) &&
                 element.toLowerCase().contains(filterString.toLowerCase());
    }
}