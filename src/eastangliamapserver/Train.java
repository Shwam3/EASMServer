package eastangliamapserver;

import java.text.SimpleDateFormat;
import java.util.*;

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
    private final List<String> history = new ArrayList<>();

    public Train(String headcode, Berth startBerth)
    {
        UUID = EastAngliaSignalMapServer.getNextUUID();

        UID = "";
        TRUST_ID = "";
        this.headcode = headcode.trim();
        start = new Date();
        currentBerth = startBerth;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
        history.add(0, "Start: " + sdf.format(start) + ": " + headcode + " (" + UUID + ")");
        addCurrentBerthToHistory();
    }

    public Train(String headcode, String UID, String trustId)
    {
        UUID = EastAngliaSignalMapServer.getNextUUID();

        this.UID = UID;
        TRUST_ID = trustId;
        this.headcode = headcode;
        start = new Date();

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
        history.add(0, "Start: " + sdf.format(start) + ": " + headcode + " (" + UUID + ")");
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

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
            history.set(0, "Start: " + sdf.format(start) + ", End: " + sdf.format(end) + ": " + headcode + " (" + UUID + ")");
        }
        else
        {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
            history.add(1, sdf.format(new Date()) + ": " + currentBerth.getBerthDescription() + (currentBerth.getName().equals("") ? "" : " (" + currentBerth.getName() + ")"));

            HashMap<String, Object> historyMap = new HashMap<>();

            historyMap.put("start",    start);
            historyMap.put("end",      end);
            historyMap.put("berth",    currentBerth);
            historyMap.put("headcode", headcode);
            historyMap.put("history",  history);
            historyMap.put("changed",  new Date());
            Berths.trainHistory(UUID,  historyMap);
        }
    }

    public String getUID() { return UID; }

    public String getTrustId() { return TRUST_ID; }

    public String getHeadcode() { return headcode; }

    public Berth getCurrentBerth() { return currentBerth; }

    public List<String> getTrainsHistory()
    {
        return history;
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
            return this.headcode == train.getHeadcode();// && this.UUID == train.UUID;
        }

        return false;
    }
}