package eastangliamapserver;

import static eastangliamapserver.stomp.StompConnectionHandler.printCClass;
import java.text.SimpleDateFormat;
import java.util.*;

public class Berths
{
    private static Map<String, Map<String, Object>> trainMap = new HashMap<>();
    private static Map<String, Berth> berthMap = new HashMap<>();
  //private static List<String> missingBerths = new ArrayList<>();
    private static Map<String, Date> missingBerths = new HashMap<>();

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

            EastAngliaSignalMapServer.updateServerGUI();
        }
    }

    public static Berth getBerth(String berthId)
    {
        if (berthMap.containsKey(berthId))
            return berthMap.get(berthId);

        return null;
    }

    public static String[] getAsArray()
    {
        List<String> list = new ArrayList<>();

        for (Map.Entry<String, Berth> pairs : berthMap.entrySet())
            list.addAll(Arrays.asList(pairs.getValue().getId()));

        return list.toArray(new String[0]);
    }

    public static List<Berth> getBerths()
    {
        List<Berth> list = new ArrayList<>();

        for (Map.Entry<String, Berth> pairs : berthMap.entrySet())
            list.add(pairs.getValue());

        return list;
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

        List<String> berthIds = new ArrayList<>();

        for (String berthId : missingBerths.keySet().toArray(new String[0]))
            if (getBerth(berthId) != null || berthId.charAt(4) == ':')
                missingBerths.remove(berthId);
            else
                berthIds.add(berthId);

        Collections.sort(berthIds);

        for (String id : berthIds)
            printCClass(id, false);

        EastAngliaSignalMapServer.updateServerGUI();
    }

    public static void addMissingBerth(String berthId)
    {
        addMissingBerth(berthId, new Date());
    }

    public static void addMissingBerth(String berthId, Date date)
    {
        if (getBerth(berthId) == null)
            missingBerths.put(berthId, date);
    }

    public static Date getMissingBerthFoundDate(String berthId)
    {
        if (missingBerths.containsKey(berthId))
            return missingBerths.get(berthId) != null ? missingBerths.get(berthId) : new Date();
        else
            return new Date(0);
    }

    public static void addTrainHistory(String trainId, Map<String, Object> historyMap)
    {
        try
        {
            Map<String, Map<String, Object>> newMap = new HashMap<>();
            newMap.put(trainId, historyMap);

            if (trainMap != null && trainMap.entrySet() != null)
            {
                for (Map.Entry<String, Map<String, Object>> pairs : trainMap.entrySet())
                {
                    Date change = (Date) pairs.getValue().get("change");
                    if (change != null && change.before(new Date(System.currentTimeMillis() - 86400000)))
                        break;

                    Date end = (Date) pairs.getValue().get("end");
                    if (end != null && end.before(new Date(System.currentTimeMillis() - 86400000)))
                        continue;

                    newMap.put(pairs.getKey(), pairs.getValue());
                }
            }

            trainMap = new HashMap<>(newMap);
        }
        catch (ConcurrentModificationException e) {}
    }

    public static Map<String, Object> getTrain(String uuid)
    {
        Map<String, Object> train = trainMap.get(uuid);

        if (train != null)
            if ((train.get("berth") == null || !((Berth) train.get("berth")).getHeadcode().equals((String) train.get("headcode"))) && train.get("end") == null)
            {
                train.put("end", new Date());

                List<String> hist = (List<String>) train.get("history");
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");
                hist.set(0, "Start: " + sdf.format(train.get("start")) + ", End (approx): " + sdf.format(train.get("end")) + ": " + train.get("headcode") + " (" + uuid + ")");
                train.put("history", hist);

                trainMap.put(uuid, train);
            }

        return train;
    }

    public static Map<String, Map<String, Object>> getTrainHistory()
    {
        Map<String, Map<String, Object>> map = new HashMap<>(trainMap);

        // Remove the "berth" element (NotSerialiZable)
        for (Iterator<Map.Entry<String, Map<String, Object>>> it = map.entrySet().iterator(); it.hasNext();)
            for (Iterator<Map.Entry<String, Object>> it2 = it.next().getValue().entrySet().iterator(); it2.hasNext();)
                if (it2.next().getKey().equalsIgnoreCase("berth"))
                    it2.remove();

        return map;
    }

    public static List<String> getCClassData(boolean skipBlanks)
    {
        List<String> CClassMapList = new ArrayList<>();

        if (EastAngliaSignalMapServer.CClassMap.isEmpty() && missingBerths.isEmpty())
        {
            CClassMapList.add("No C-Class data has been received");
            return CClassMapList;
        }

        if (!missingBerths.isEmpty())
        {
            String[] keys = missingBerths.keySet().toArray(new String[0]);
            Arrays.sort(keys);
            CClassMapList.add("Berth ids missing from the map");
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm:ss");

            for (String missingBerthId : keys)
                if (getBerth(missingBerthId) == null)
                {
                    String hc = String.valueOf(EastAngliaSignalMapServer.CClassMap.get(missingBerthId)).replace("null", "    ");
                    hc = hc.equals("") ? "    " : hc;
                    if (missingBerths.get(missingBerthId) == null)
                        CClassMapList.add(missingBerthId + " (??/?? ??:??:??) (" + hc + ")");
                    else
                        CClassMapList.add(missingBerthId + " (" + sdf.format(missingBerths.get(missingBerthId)) + ") (" + hc + ")");
                }
                else
                    missingBerths.remove(missingBerthId);
        }

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty() && !missingBerths.isEmpty())
            CClassMapList.add(" ");

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty())
        {
            String[] keys = EastAngliaSignalMapServer.CClassMap.keySet().toArray(new String[0]);
            Arrays.sort(keys);

            CClassMapList.add("Full C-Class Data Map (# Berths: " + berthMap.size() + ")");

            for (String key : keys)
                if (EastAngliaSignalMapServer.CClassMap.get(key) != null && !EastAngliaSignalMapServer.CClassMap.get(key).trim().equals("") || !skipBlanks)
                    CClassMapList.add(key + ": " + EastAngliaSignalMapServer.CClassMap.get(key));
        }

        return CClassMapList;
    }

    public static void cleanMaps()
    {
        for (Berth berth : getBerths())
        {
            berth.clean();
            if (berth.hasTrain())
                berth.getTrain().clean();
        }
    }
}