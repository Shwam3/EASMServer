package eastangliamapserver;

import static eastangliamapserver.stomp.StompConnectionHandler.printCClass;
import java.text.SimpleDateFormat;
import java.util.*;

public class Berth
{
    private final String[] BERTH_IDs;
    private final String   BERTH_DESCRIPTION;
    private       String   name = "";
    private       Train    currentTrain;
    private       boolean  isProblematic  = false;
    public        int      isMonitoring   = 0;
    private       Train    suggestedTrain = null;

    private volatile boolean addressMapLocked = false;

    private final HashMap<String, HashMap<String, String>> possibleAddresses = new HashMap<>();
    private final List<Berth>                   adjacentBerths = new ArrayList<>();
    private final List<String>                  trainHistory   = new ArrayList<>();
    private final List<HashMap<String, String>> stepToBerths   = new ArrayList<>();

    public Berth(String... berthIds)
    {
        this.BERTH_IDs = berthIds;

        StringBuilder desc = new StringBuilder();

        for (int i = 0; i < BERTH_IDs.length; i++)
        {
            if (i == 0)
                desc.append(BERTH_IDs[i]);
            else if (i == BERTH_IDs.length - 1)
                desc.append(" & " + BERTH_IDs[i]);
            else
                desc.append(", " + BERTH_IDs[i]);

            Berths.putBerth(BERTH_IDs[i], this);
        }

        BERTH_DESCRIPTION = desc.toString();
    }

    public void setName(String name) { this.name = name.trim(); }
    public String getName() { return name; }

    public void cancel(String headcode, String time)
    {
        if (headcode == null)
            headcode = "    ";

        if (currentTrain == null || currentTrain.getHeadcode().equals(headcode) || headcode.equals("    ") || headcode.equals("****"))
        {
            currentTrain = null;

            /*if (time != null)
                startMonitor(time);*/
        }
    }

    public void interpose(Train newTrain)
    {
        if (newTrain.getHeadcode().equals(""))
            return;

        if (currentTrain != null && newTrain.getHeadcode().equals(currentTrain.getHeadcode()))
            if (newTrain.getTrainsHistory().size() < currentTrain.getTrainsHistory().size())
                return;

        if (suggestedTrain != null)
        {
            if (newTrain.getHeadcode().equals(suggestedTrain.getHeadcode()))
            {
                printCClass(String.format("    Using suggested train for %s, %s (%s%s)", BERTH_DESCRIPTION, suggestedTrain.getHeadcode(), suggestedTrain.UUID, suggestedTrain != newTrain ? " (vs " + newTrain.UUID + ")" : ""), false);
                newTrain = suggestedTrain;
            }
            else
                printCClass(String.format("    Discarding suggested train for %s, %s (%s%s)", BERTH_DESCRIPTION, suggestedTrain.getHeadcode(), suggestedTrain.UUID, suggestedTrain != newTrain ? " (vs " + newTrain.UUID + ")" : ""), false);
        }
        else
            if (currentTrain != null && !currentTrain.equals(newTrain) && (currentTrain.getCurrentBerth() == this || currentTrain.getTrainsHistory().size() <= 2))
                currentTrain.setBerth(null);

        suggestedTrain = null;
        currentTrain = newTrain;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");

        if (currentTrain != null && !currentTrain.getHeadcode().equals(""))
        {
            trainHistory.add(0, String.format("%s: %s (%s)", sdf.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));
            currentTrain.setBerth(this);
        }

        while (trainHistory.size() > 256)
            trainHistory.remove(trainHistory.size() - 1);
    }

    public void setProblematicBerth(boolean isProblematic)
    {
        this.isProblematic = isProblematic;
    }

    /**
     * When this berth is stepped from or cancelled the specified berth will be interposed with the moving train
     *
     * @param fromBerthId   The berth stepped from
     * @param fakeToBerthId The berth to be stepped (copied) into
     * @param realToBerthId The berth that must be stepped into
     * @param type          The step type (suggest / interpose)
     */
    public Berth addStepToBerth(String fromBerthId, String fakeToBerthId, String realToBerthId, String type)
    {
        HashMap<String, String> hm = new HashMap<>();
        hm.put("fromBerthId",   fromBerthId);
        hm.put("fakeToBerthId", fakeToBerthId);
        hm.put("realToBerthId", realToBerthId);
        hm.put("type",          type);

        if (!stepToBerths.contains(hm))
            stepToBerths.add(hm);

        return this;
    }

    /**
     * @param toBerthId   The berth stepped to
     * @param fromBerthId The berth stepped from
     *
     * @return Berths that can be stepped into using the given toBerth
     */
    public List<String> getStepToBerthsFor(String toBerthId, String fromBerthId)
    {
        List<String> stepToBerthList = new ArrayList<>();

        for (HashMap<String, String> hm : stepToBerths)
            if (hm.get("realToBerthId").equals(toBerthId) && hm.get("fromBerthId").equals(fromBerthId))
                stepToBerthList.add(hm.get("fakeToBerthId"));

        return stepToBerthList;
    }

    public String getTypeFor(String fakeToBerthId, String fromBerthId)
    {
        String type = "suggest";

        for (HashMap<String, String> hm : stepToBerths)
            if (hm.get("fakeToBerthId").equals(fakeToBerthId))
            {
                type = hm.get("type");
                break;
            }

        return type;
    }

    public boolean canStepToBerth(String realToBerthId, String fromBerthId)
    {
        for (HashMap<String, String> hm : stepToBerths)
            if (hm.get("fromBerthId").equals(fromBerthId) && hm.get("realToBerthId").equals(realToBerthId))
                return true;

        return false;
    }

    public boolean hasStepToEvent()
    {
        return !stepToBerths.isEmpty();
    }

    public void suggestTrain(Train train, String type)
    {
        if (train != null)
        {
            if (getHeadcode().equals(train.getHeadcode()) || (getHeadcode().equals("") && type.equals("interpose")))
            {
                if (currentTrain != null)
                    currentTrain.setBerth(null);

                printCClass(String.format("    Using suggested train for %s, %s (%s)", BERTH_DESCRIPTION, train.getHeadcode(), train.UUID), false);
                suggestedTrain = null;
                currentTrain = train;

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
                trainHistory.add(0, String.format("%s: %s (%s)", sdf.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));
                currentTrain.setBerth(this);
            }
            else
            {
                suggestedTrain = train;
                printCClass(String.format("    %s %s (%s) to %s", type, train.getHeadcode(), train.UUID, BERTH_DESCRIPTION), false);
            }
        }
    }

    public Berth addAdjacentBerths(String... berthIds)
    {
        for (String berthId : berthIds)
        {
            Berth berth = Berths.getBerth(berthId);
            if (berth != null)
                if (!adjacentBerths.contains(berth))
                {
                    adjacentBerths.add(berth);
                    berth.addAdjacentBerths(this.getIds()[0]);
                }
        }

        return this;
    }

    public List<Berth> getAdjacentBerths()
    {
        return adjacentBerths;
    }

    public boolean hasTrain()
    {
        return currentTrain != null;
    }

    public boolean isProperHeadcode()
    {
        return Berths.isProperHeadcode(getHeadcode());
    }

    public boolean hasAdjacentBerths()
    {
        return adjacentBerths.size() > 0;
    }

    public List<Train> getAdjacentTrains()
    {
        List<Train> trainList = new ArrayList<>();

        for (Berth berth : getAdjacentBerths())
            if (berth.hasTrain())
                trainList.add(berth.getTrain());

        return trainList;
    }

    public String getHeadcode()
    {
        if (currentTrain == null)
            return "";

        return currentTrain.getHeadcode();
    }

    public String[] getIds()
    {
        return BERTH_IDs;
    }

    public Train getTrain()
    {
        return currentTrain;
    }

    public String getBerthDescription()
    {
        return BERTH_DESCRIPTION;
    }

    boolean isProblematic()
    {
        return isProblematic;
    }

    /*private void startMonitor(final String time)
    {
        isMonitoring++;
        javax.swing.Timer timer = new javax.swing.Timer(30000, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent evt)
            {
                Date timeStart = new Date(Long.parseLong(time) - 30000);
                Date timeEnd   = new Date(Long.parseLong(time) + 60000);
                HashMap<String, HashMap<String, String>> newAddressList = new HashMap<>();

                for (int i = 0; i < SocketServer.SClassLog.size(); i++)
                {
                    HashMap<String, String> map = SocketServer.SClassLog.get(i);
                    if (map.get("area_id").equals(BERTH_DESCRIPTION.substring(0, 2)))
                        if (new Date(Long.parseLong(map.get("time"))).after(timeStart))
                            if (new Date(Long.parseLong(map.get("time"))).before(timeEnd))
                                newAddressList.put(map.get("address"), map);
                }

                int interruptCount = 0;
                while (addressMapLocked)
                {
                    try
                    {
                        Thread.sleep(100);
                        if (++interruptCount > 10)
                            break;
                    }
                    catch (InterruptedException e)
                    {
                        if (++interruptCount > 10)
                            break;
                    }
                }

                addressMapLocked = true;
                try
                {
                    for (String key : possibleAddresses.keySet())
                        if (!newAddressList.containsKey(key))
                            possibleAddresses.remove(key);

                    for (String key : newAddressList.keySet())
                        if (!possibleAddresses.containsKey(key))
                            possibleAddresses.put(key, newAddressList.get(key));
                }
                catch (ConcurrentModificationException e) {}
                finally
                {
                    addressMapLocked = false;
                    isMonitoring--;
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }*/

    public boolean hasPossibleAddresses()
    {
        return !possibleAddresses.isEmpty();
    }

    public List<String> getBerthsHistory()
    {
        return trainHistory;
    }

    public List<String> getTrainsHistory()
    {
        if (currentTrain == null)
            return null;

        return currentTrain.getTrainsHistory();
    }

    @Override
    public String toString()
    {
        return "eastangliamap.Berth=[description=" + BERTH_DESCRIPTION + ",berthIds=" + Arrays.deepToString(BERTH_IDs) + (currentTrain != null ? ",train=" + currentTrain.toString(): ",train=    ") + "]";
    }
}