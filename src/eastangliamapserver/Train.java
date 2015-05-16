package eastangliamapserver;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Train
{
    private final String  UID;
    private final String  TRUST_ID;
    public  final String  UUID;
    public  final Date    start;
    public        Date    end;
    private       String  headcode = "";
    private       Berth   currentBerth;
    public        boolean isCancelled = false;
    private       List<String> history = new ArrayList<>();

    public Train(String headcode, Berth startBerth)
    {
        UUID = EastAngliaSignalMapServer.getNextUUID();

        UID = "";
        TRUST_ID = "";
        this.headcode = headcode.trim();
        start = new Date();
        currentBerth = startBerth;

        history.add(0, "Start: " + EastAngliaSignalMapServer.sdfDateTimeShort.format(start) + ": " + headcode + " (" + UUID + ")");
        addCurrentBerthToHistory();
    }

    public Train(String headcode, String UID, String trustId)
    {
        UUID = EastAngliaSignalMapServer.getNextUUID();

        while (headcode.length() < 4)
            headcode += " ";

        this.UID = UID;
        TRUST_ID = trustId;
        this.headcode = headcode;
        start = new Date();

        history.add(0, "Start: " + EastAngliaSignalMapServer.sdfDateTimeShort.format(start) + ": " + headcode + " (" + UUID + ")");
        addCurrentBerthToHistory();
    }

    public void setBerth(Berth berth)
    {
        if (berth != currentBerth)
        {
            currentBerth = berth;
            addCurrentBerthToHistory();
        }
    }

    private void addCurrentBerthToHistory()
    {
        if (currentBerth == null/* || !currentBerth.getHeadcode().equals(headcode)*/)
        {
            isCancelled = true;
            end = new Date();

            history.set(0, "Start: " + EastAngliaSignalMapServer.sdfDateTimeShort.format(start) + ", End: " + EastAngliaSignalMapServer.sdfDateTimeShort.format(end) + ": " + headcode + " (" + UUID + ")");
        }
        else
            history.add(1, EastAngliaSignalMapServer.sdfDateTimeShort.format(new Date()) + ": " + currentBerth.getBerthDescription() + (currentBerth.getName().equals("") ? "" : " (" + currentBerth.getName() + ")"));

        Map<String, Object> historyMap = new HashMap<>();

        historyMap.put("start",    start);
        historyMap.put("end",      end);
        //historyMap.put("berth",    currentBerth);
        historyMap.put("headcode", headcode);
        historyMap.put("history",  history);
        historyMap.put("changed",  new Date());
        Berths.addTrainHistory(UUID,  historyMap);
    }

    public String getUID() { return UID; }

    public String getTrustId() { return TRUST_ID; }

    public String getHeadcode() { return headcode; }

    public Berth getCurrentBerth() { return currentBerth; }

    public List<String> getTrainsHistory()
    {
        return history;
    }

    public void clean()
    {
        history = new ArrayList<>(history);
    }

    @Override
    public String toString()
    {
        return "eastangliamapserver.Train[headcode=" + headcode + ",uuid=" + UUID + "]";
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Train)
        {
            Train train = (Train) obj;
            return headcode.equals(train.getHeadcode()) && this.UUID.equals(train.UUID);
        }

        return false;
    }
}