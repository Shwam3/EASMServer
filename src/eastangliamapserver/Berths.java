package eastangliamapserver;

import eastangliamapserver.stomp.StompConnectionHandler;
import static eastangliamapserver.stomp.StompConnectionHandler.printCClass;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.*;

public class Berths
{
    private static HashMap<String, HashMap<String, Object>> trainMap = new HashMap<>();
    private static HashMap<String, Berth> berthMap = new HashMap<>();
    private static ArrayList<String> missingBerths = new ArrayList<>();

    public static Berth createOrGetBerth(String berthId)
    {
        Berth berth = getBerth(berthId);

        if (berth != null)
            return berth;
        else
            return new Berth(berthId);
    }

    public static boolean containsBerth(String berthId)
    {
        return berthMap.containsKey(berthId);
    }

    public static void putBerth(String berthId, Berth berth)
    {
        if (!containsBerth(berthId))
        {
            berthMap.put(berthId, berth);
            missingBerths.remove(berthId);

            try { EastAngliaSignalMapServer.gui.updateDataList(); }
            catch (NullPointerException e) {}
        }
    }

    public static Berth getBerth(String berthId)
    {
        if (berthMap.containsKey(berthId))
            return berthMap.get(berthId);

        return null;
    }

    public static Object[] getAsArray()
    {
        ArrayList<String> list = new ArrayList<>();

        for (Map.Entry pairs : berthMap.entrySet())
        {
            Berth berth = (Berth) pairs.getValue();

            list.addAll(Arrays.asList(berth.getId()));
        }

        return list.toArray();
    }

    public static Set<Map.Entry<String, Berth>> getEntrySet()
    {
        return berthMap.entrySet();
    }

    public static Set<String> getKeySet()
    {
        return berthMap.keySet();
    }

    public static void printIds()
    {
        printCClass("Missing Berth Ids:", false);

        ArrayList<String> berthIds = new ArrayList<>();

        for (String berthId : missingBerths)
            if (getBerth(berthId) != null)
                missingBerths.remove(berthId);
            else
                berthIds.add(berthId);

        Collections.sort(berthIds);

        for (String id : berthIds)
            printCClass(id, false);
    }

    public static void addMissingBerths(String berthId)
    {
        if (getBerth(berthId) == null)
        {
            for (String missingBerth : missingBerths)
                if (missingBerth.equals(berthId))
                    return;

            missingBerths.add(berthId);
        }
    }

    public static void clearMaps()
    {
        berthMap = new HashMap<>();
    }

    public static void trainHistory(String trainId, HashMap<String, Object> historyMap)
    {
        trainMap.put(trainId, historyMap);

        for (Map.Entry pairs : trainMap.entrySet())
        {
            HashMap histMap = (HashMap) pairs.getValue();

            Date change = (Date) histMap.get("change");
            if (change != null && change.before(new Date(System.currentTimeMillis() - 86400000)))
            {
                trainMap.remove((String) pairs.getKey());
                return;
            }

            Date end = (Date) histMap.get("end");
            if (end != null && end.before(new Date(System.currentTimeMillis() - 86400000)))
                trainMap.remove((String) pairs.getKey());
        }
    }

    public static HashMap<String, Object> getTrain(String uuid)
    {
        HashMap<String, Object> train = trainMap.get(uuid);

        if ((train.get("berth") == null || !((Berth) train.get("berth")).getHeadcode().equals((String) train.get("headcode"))) && train.get("end") == null)
        {
            train.put("end", new Date());

            ArrayList hist = (ArrayList) train.get("history");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
            hist.set(0, "Start: " + sdf.format(train.get("start")) + ", End (approx): " + sdf.format(train.get("end")) + ": " + train.get("headcode") + " (" + uuid + ")");
            train.put("history", hist);

            trainMap.put(uuid, train);
        }

        return train;
    }

    public static ArrayList<String> getCClassData(boolean skipBlanks)
    {
        ArrayList<String> CClassMapList = new ArrayList<>();

        long time = System.currentTimeMillis() - StompConnectionHandler.lastMessageTime;
        CClassMapList.add("Current Time:  " + EastAngliaSignalMapServer.sdf.format(new Date()));
        CClassMapList.add(String.format("Last Message:  %s (%02d:%02d:%02d)",
                        EastAngliaSignalMapServer.sdf.format(new Date(StompConnectionHandler.lastMessageTime)),
                        (time / (3600000)) % 24,
                        (time / (60000)) % 60,
                        (time / 1000) % 60)
                    + (!StompConnectionHandler.isConnected() ? " - disconnected" : "")
                    + (StompConnectionHandler.isClosed()? " - closed" : "")
                    + (StompConnectionHandler.isTimedOut() ? " - timed out" : ""));
        time = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        CClassMapList.add(String.format("Server Uptime: %02dd %02dh %02dm %02ds (%s)",
                        (time / (86400000)),
                        (time / (3600000)) % 24,
                        (time / (60000)) % 60,
                        (time / 1000) % 60,
                        new SimpleDateFormat("dd/MM HH:mm:ss").format(ManagementFactory.getRuntimeMXBean().getStartTime()))
                    + (EastAngliaSignalMapServer.server == null || EastAngliaSignalMapServer.server.isClosed() ? " - closed" : ""));
        CClassMapList.add(String.format("Memory use:    %s mb"/* (f %s, t %s, m %s)"*/, (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576/*, Runtime.getRuntime().freeMemory(), Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory()*/));

        Berth motdBerth = Berths.getBerth("XXMOTD");
        if (motdBerth != null && !motdBerth.getHeadcode().trim().equals(""))
            CClassMapList.add("<html><pre>MOTD:          &quot;" + motdBerth.getHeadcode().trim() + "&quot;</pre></html>");

        CClassMapList.add(" ");

        if (EastAngliaSignalMapServer.CClassMap.isEmpty() && missingBerths.isEmpty())
        {
            CClassMapList.add("No C-Class data has been received");
            return CClassMapList;
        }

        if (!missingBerths.isEmpty())
        {
            Collections.sort(missingBerths);
            CClassMapList.add("Berth ids missing from the map");

            for (String missingBerth : missingBerths)
                if (getBerth(missingBerth) == null)
                    CClassMapList.add(missingBerth);
                else
                    missingBerths.remove(missingBerth);
        }

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty() && !missingBerths.isEmpty())
            CClassMapList.add(" ");

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty())
        {
            ArrayList<String> mapKeys = new ArrayList<>(EastAngliaSignalMapServer.CClassMap.keySet());
            Collections.sort(mapKeys);

            CClassMapList.add("Full C-Class Data Map (No. Berths: " + berthMap.size() + ")");

            for (String key : mapKeys)
                if (!key.startsWith("XX"))
                    if (skipBlanks)
                        if (EastAngliaSignalMapServer.CClassMap.get(key) != null && !EastAngliaSignalMapServer.CClassMap.get(key).trim().equals(""))
                        { CClassMapList.add(key + ": " + EastAngliaSignalMapServer.CClassMap.get(key)); }
                    else
                        CClassMapList.add(key + ": " + EastAngliaSignalMapServer.CClassMap.get(key));
        }

        return CClassMapList;
    }
}