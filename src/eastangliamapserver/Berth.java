package eastangliamapserver;

import eastangliamapserver.stomp.StompConnectionHandler;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class Berth implements Serializable
{
    private final String  BERTH_ID;
    private final String  BERTH_DESCRIPTION;
    private       String  name = "";
    private transient Train currentTrain;
  //private       boolean isProblematic  = false;
  //public        int     isMonitoring   = 0;
    private       Train   suggestedTrain = null;

    private final Map<String, Map<String, String>> possibleAddresses = new HashMap<>();
    private final List<Berth>                   adjacentBerths = new ArrayList<>();
    private       List<String>                  trainHistory   = new ArrayList<>();
    private final List<Map<String, String>> stepToBerths   = new ArrayList<>();

    public Berth(String berthId)
    {
        BERTH_ID = berthId;

        Berths.putBerth(berthId, this);
        BERTH_DESCRIPTION = berthId;
    }

    public void setName(String name) { this.name = name.trim(); }
    public String getName() { return name; }

    public void cancel(String headcode, String time)
    {
        if (headcode == null)
            headcode = "    ";

        if (currentTrain == null || currentTrain.getHeadcode().equals(headcode) || headcode.equals("    ") || headcode.equals("\\*\\*\\*\\*"))
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

        if (!BERTH_ID.startsWith("XX"))
        {
            if (suggestedTrain != null)
            {
                if (newTrain.getHeadcode().equals(suggestedTrain.getHeadcode()))
                {
                    printBerth(String.format("Using suggested: %s (%s%s)", suggestedTrain.getHeadcode(), suggestedTrain.UUID, suggestedTrain != newTrain ? " (vs " + newTrain.UUID + ")" : ""), false);
                    newTrain = suggestedTrain;
                }
                else
                    printBerth(String.format("Discarding suggested: %s (%s%s)", suggestedTrain.getHeadcode(), suggestedTrain.UUID, suggestedTrain != newTrain ? " (vs " + newTrain.UUID + ")" : ""), false);
            }
            else
                if (currentTrain != null && !currentTrain.equals(newTrain) && (currentTrain.getCurrentBerth() == this || currentTrain.getTrainsHistory().size() <= 2))
                    currentTrain.setBerth(null);

            suggestedTrain = null;
        }

        currentTrain = newTrain;

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");

        if (currentTrain != null && !currentTrain.getHeadcode().equals("") && (trainHistory.isEmpty() || !trainHistory.get(0).contains(currentTrain.getHeadcode())))
        {
            trainHistory.add(0, String.format("%s: %s (%s)", sdf.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));
            currentTrain.setBerth(this);
        }

        if (!BERTH_ID.startsWith("XX"))
            while (trainHistory.size() > 256)
                trainHistory.remove(trainHistory.size() - 1);
    }

    /*public void setProblematicBerth(boolean isProblematic)
    {
        this.isProblematic = isProblematic;
    }*/

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
        Map<String, String> hm = new HashMap<>();
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

        for (Map<String, String> hm : stepToBerths)
            if (hm.get("realToBerthId").equals(toBerthId) && hm.get("fromBerthId").equals(fromBerthId))
                stepToBerthList.add(hm.get("fakeToBerthId"));

        return stepToBerthList;
    }

    public String getTypeFor(String fakeToBerthId, String fromBerthId)
    {
        String type = "Suggest";

        for (Map<String, String> hm : stepToBerths)
            if (hm.get("fakeToBerthId").equals(fakeToBerthId))
            {
                type = hm.get("type");
                break;
            }

        return type;
    }

    public boolean canStepToBerth(String realToBerthId, String fromBerthId)
    {
        for (Map<String, String> hm : stepToBerths)
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
            if (getHeadcode().equals(train.getHeadcode()) || (getHeadcode().equals("") && type.equals("Interpose")))
            {
                if (currentTrain != null)
                    currentTrain.setBerth(null);

                printBerth(String.format("Using suggested train %s (%s)", train.getHeadcode(), train.UUID), false);
                suggestedTrain = null;
                currentTrain = train;

                if (trainHistory.isEmpty() || !trainHistory.get(0).contains(currentTrain.getHeadcode()))
                {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
                    trainHistory.add(0, String.format("%s: %s (%s)", sdf.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));
                }
                currentTrain.setBerth(this);
            }
            else
            {
                suggestedTrain = train;
                printBerth(String.format("%s %s (%s)", type, train.getHeadcode(), train.UUID), false);
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
                    berth.addAdjacentBerths(this.getId());
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

    /*public boolean isProperHeadcode()
    {
        return Berths.isProperHeadcode(getHeadcode());
    }*/

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

    public String getId()
    {
        return BERTH_ID;
    }

    public Train getTrain()
    {
        return currentTrain;
    }

    public String getBerthDescription()
    {
        return BERTH_DESCRIPTION;
    }

    /*boolean isProblematic()
    {
        return isProblematic;
    }*/

    private void startMonitor(final String time)
    {
        /*javax.swing.Timer timer = new javax.swing.Timer(30000, new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent evt)
            {
                try
                {
                    Date timeStart = new Date(Long.parseLong(time) - 45000); // 45 seconds before move
                    Date timeEnd   = new Date(Long.parseLong(time) + 75000); // 75 seconds after  move
                    Map<String, Map<String, String>> newAddressList = new HashMap<>(); // Addreses changed in this period

                    for (Map<String, String> map : new ArrayList<>(EastAngliaSignalMapServer.SClassLog))
                        if (map.get("area_id").equals(BERTH_DESCRIPTION.substring(0, 2))) // Is in area
                            if (new Date(Long.parseLong(map.get("time"))).after(timeStart))
                                if (new Date(Long.parseLong(map.get("time"))).before(timeEnd)) // Is within period
                                    newAddressList.put(map.get("address"), map); // Add to changes in period

                    for (String key : newAddressList.keySet())
                    {
                        Map<String, String> map = newAddressList.get(key);

                        if (!possibleAddresses.containsKey(key))
                            map.put("occurences", "1");
                        else
                            try
                            {
                                map.put("occurences", Integer.toString(Integer.parseInt(possibleAddresses.get(key).get("occurences")) + 1));
                            }
                            catch (NumberFormatException e) { map.put("occurences", "1"); }

                        possibleAddresses.put(key, map);
                    }
                }
                catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, BERTH_DESCRIPTION); }
            }
        });
        timer.setRepeats(false);
        timer.start();*/
    }

    public List<String> getBerthsHistory()
    {
        return trainHistory;
    }

    public void setHistory(List<String> history)
    {
        this.trainHistory = new ArrayList<>(history);
    }

    public List<String> getTrainsHistory()
    {
        if (currentTrain == null)
            return null;

        return currentTrain.getTrainsHistory();
    }

    public Map<String, Map<String, String>> getPossibleAddreses()
    {
        return possibleAddresses;
    }

    public void clean()
    {
        trainHistory = new ArrayList<>(trainHistory);
    }

    @Override
    public String toString()
    {
        return "eastangliamap.Berth=[description=" + BERTH_DESCRIPTION + ",berthId=" + BERTH_ID + ",train=" + String.valueOf(currentTrain) + "]";
    }

    public void printBerth(String message, boolean toErr)
    {
        StompConnectionHandler.printCClass("[" + BERTH_DESCRIPTION + "] " + message, toErr);
    }
}