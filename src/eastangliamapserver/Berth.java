package eastangliamapserver;

import eastangliamapserver.server.Clients;
import eastangliamapserver.stomp.StompConnectionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Berth
{
    private final     String BERTH_ID;
    private final     String BERTH_DESCRIPTION;
    private           String name = "";
    private transient Train  currentTrain;
    private           Train  suggestedTrain = null;

    private final List<Berth>               adjacentBerths = new ArrayList<>();
    private       List<String>              trainHistory   = new ArrayList<>();
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
        }
    }

    public void interpose(Train newTrain)
    {
        EastAngliaSignalMapServer.CClassMap.put(getId(), newTrain.getHeadcode());

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


        if (currentTrain != null && !currentTrain.getHeadcode().equals("") && (trainHistory.isEmpty() || !trainHistory.get(0).contains(currentTrain.getHeadcode())))
        {
            trainHistory.add(0, String.format("%s: %s (%s)", EastAngliaSignalMapServer.sdfDateTimeShort.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));
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

        stepToBerths.stream()
                .filter(hm -> hm.get("realToBerthId").equals(toBerthId) && hm.get("fromBerthId").equals(fromBerthId))
                .forEach(hm -> stepToBerthList.add(hm.get("fakeToBerthId")));

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
        return stepToBerths.stream()
                .anyMatch(hm -> hm.get("fromBerthId").equals(fromBerthId) &&
                        hm.get("realToBerthId").equals(realToBerthId));
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

                EastAngliaSignalMapServer.CClassMap.put(getId(), train.getHeadcode());
                Clients.scheduleForNextUpdate(getId(), train.getHeadcode());

                if (trainHistory.isEmpty() || !trainHistory.get(0).contains(currentTrain.getHeadcode()))
                    trainHistory.add(0, String.format("%s: %s (%s)", EastAngliaSignalMapServer.sdfDateTimeShort.format(new Date()), currentTrain.getHeadcode(), currentTrain.UUID));

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

    public boolean hasAdjacentBerths()
    {
        return adjacentBerths.size() > 0;
    }

    public List<Train> getAdjacentTrains()
    {
        List<Train> trainList = new ArrayList<>();

        getAdjacentBerths().stream()
                .filter(berth -> berth.hasTrain())
                .forEach(berth -> trainList.add(berth.getTrain()));

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
            return new ArrayList<>(0);

        return currentTrain.getTrainsHistory();
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