package eastangliamapserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Berths
{
    private static final Map<String, Map<String, Object>> trainMap = new HashMap<>();
    private static final Map<String, Berth> berthMap = new HashMap<>();
    private static final List<String>       missingBerths = new ArrayList<>();
    private static final Map<String, Date>  berthChangeTimes = new HashMap<>();
    public  static boolean ready = false;

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

            EastAngliaSignalMapServer.updateServerGUIs();
        }
    }

    public static Berth getBerth(String berthId)
    {
        return berthMap.getOrDefault(berthId, null);
    }

    public static String[] getAsArray()
    {
        List<String> list = new ArrayList<>();

        berthMap.entrySet().stream().forEach((pairs) -> list.addAll(Arrays.asList(pairs.getValue().getId())));

        return list.toArray(new String[0]);
    }

    public static List<Berth> getBerths()
    {
        List<Berth> list = new ArrayList<>();

        berthMap.entrySet().stream().forEach((pairs) -> list.add(pairs.getValue()));

        return list;
    }

    public static Set<Map.Entry<String, Berth>> getEntrySet()
    {
        return berthMap.entrySet();
    }

    /*public static Set<String> getKeySet()
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

        EastAngliaSignalMapServer.updateServerGUIs();
    }*/

    public static void addMissingBerth(String berthId)
    {
        addMissingBerth(berthId, new Date());
    }

    public static void setBerthModifiedDate(String berthId, Date date)
    {
        berthChangeTimes.put(berthId, date);
    }

    public static void addMissingBerth(String berthId, Date date)
    {
        if (getBerth(berthId) == null)
        {
            if (!missingBerths.contains(berthId))
                missingBerths.add(berthId);
            berthChangeTimes.put(berthId, date);
        }
    }

    public static Date getBerthLastModifiedTime(String berthId)
    {
        if (berthChangeTimes.containsKey(berthId))
            return berthChangeTimes.get(berthId) != null ? berthChangeTimes.get(berthId) : new Date(0);
        else
            return new Date(0);
    }

    public static void addTrainHistory(String trainId, Map<String, Object> historyMap)
    {
        if (historyMap.size() > 2 && ready)
        {
            trainMap.put(trainId, historyMap);
        }
            /*try
            {
                Map<String, Map<String, Object>> newMap = new HashMap<>();
                newMap.put(trainId, historyMap);

                if (trainMap != null && trainMap.entrySet() != null)
                {
                    for (Map.Entry<String, Map<String, Object>> pairs : trainMap.entrySet())
                    {
                        if (historyMap.size() > 2)
                        {
                            Date change = (Date) pairs.getValue().get("change");
                            if (change != null && change.before(new Date(System.currentTimeMillis() - 180000000)))
                                break;

                            Date end = (Date) pairs.getValue().get("end");
                            if (end != null && end.before(new Date(System.currentTimeMillis() - 180000000)))
                                continue;

                            newMap.put(pairs.getKey(), pairs.getValue());
                        }
                    }
                }

                trainMap.clear();
                trainMap.putAll(newMap);
            }
            catch (ConcurrentModificationException e) { EastAngliaSignalMapServer.printThrowable(e, "TrainHistory"); }
        }*/
    }

    public static Map<String, Object> getTrain(String uuid)
    {
        Map<String, Object> train = trainMap.get(uuid);

        if (train != null)
            if ((train.get("berth") == null || !((Berth) train.get("berth")).getHeadcode().equals((String) train.get("headcode"))) && train.get("end") == null)
            {
                train.put("end", new Date());

                List<String> hist = (List<String>) train.get("history");
                hist.set(0, "Start: " + EastAngliaSignalMapServer.sdfDateTimeShort.format(train.get("start")) + ", End (approx): " + EastAngliaSignalMapServer.sdfDateTimeShort.format(train.get("end")) + ": " + train.get("headcode") + " (" + uuid + ")");
                train.put("history", hist);

                trainMap.put(uuid, train);
            }

        return train;
    }

    public static Map<String, Map<String, Object>> getTrainHistory()
    {
        return new HashMap<>(trainMap);
    }

    public static List<String> getCClassData(boolean includeBlanks, boolean includeMissing)
    {
        List<String> CClassMapList = new ArrayList<>();

        if (EastAngliaSignalMapServer.CClassMap.isEmpty() && missingBerths.isEmpty())
        {
            CClassMapList.add("No C-Class data has been received");
            return CClassMapList;
        }

        if (!missingBerths.isEmpty() && includeMissing)
        {
            String[] berths = missingBerths.toArray(new String[0]);
            Arrays.sort(berths);
            CClassMapList.add("Berth ids missing from the map");

            for (String berthId : berths)
            {
                if (getBerth(berthId) == null)
                {
                    String hc = String.valueOf(EastAngliaSignalMapServer.CClassMap.get(berthId)).replace("null", "    ");
                    hc = hc.equals("") ? "    " : hc;
                    if (berthChangeTimes.containsKey(berthId) && berthChangeTimes.get(berthId) != null)
                        CClassMapList.add(berthId + ": '" + hc + "' (" + EastAngliaSignalMapServer.sdfDateTimeShort.format(berthChangeTimes.get(berthId)) + ")");
                    else
                        CClassMapList.add(berthId + ": '" + hc + "' (??/?? ??:??:??)");
                }
                else
                    missingBerths.remove(berthId);
            }
        }

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty() && !missingBerths.isEmpty() && includeMissing)
            CClassMapList.add(" ");

        if (!EastAngliaSignalMapServer.CClassMap.isEmpty())
        {
            String[] berthIds = EastAngliaSignalMapServer.CClassMap.keySet().toArray(new String[0]);
            Arrays.sort(berthIds);

            CClassMapList.add("Full C-Class Data Map (# Berths: " + berthMap.size() + ")");
            CClassMapList.add("ID,      map,  berth,  modified time");

            for (String berthId : berthIds)
            {
                if ((EastAngliaSignalMapServer.CClassMap.get(berthId) != null && !EastAngliaSignalMapServer.CClassMap.get(berthId).trim().isEmpty()) ||
                        (Berths.containsBerth(berthId) && !Berths.getBerth(berthId).getHeadcode().trim().isEmpty()) || includeBlanks)
                {
                    String hcMap = EastAngliaSignalMapServer.CClassMap.get(berthId);
                    String hcBerth = Berths.containsBerth(berthId) ? Berths.getBerth(berthId).getHeadcode() : "";
                    if (berthChangeTimes.containsKey(berthId) && berthChangeTimes.get(berthId) != null)
                        CClassMapList.add(String.format("%s: '%s'|%s (%s)", berthId, hcMap.isEmpty() ? "    " : hcMap, Berths.containsBerth(berthId) ? "'" + (hcBerth.isEmpty() ? "    " : hcBerth) + "'" : "      ", EastAngliaSignalMapServer.sdfDateTimeShort.format(berthChangeTimes.get(berthId))));
                    else
                        CClassMapList.add(String.format("%s: '%s'|%s (??/?? ??:??:??)", berthId, hcMap.isEmpty() ? "    " : hcMap, Berths.containsBerth(berthId) ? "'" + (hcBerth.isEmpty() ? "    " : hcBerth) + "'" : "      "));
                }
            }
        }

        return CClassMapList;
    }

    public static void cleanMaps()
    {
        getBerths().stream()
                .filter((berth) -> (berth.hasTrain()))
                .forEach((berth) ->
        {
            berth.clean();
            berth.getTrain().clean();
        });

        Map<String, Map<String, Object>> trainMapNew = new HashMap<>(trainMap);
        try
        {
            trainMapNew.entrySet().parallelStream().forEach((pairs) ->
            {
                Date change = (Date) pairs.getValue().get("change");
                if (change != null && change.before(new Date(System.currentTimeMillis() - 86400000)))
                    return;

                Date end = (Date) pairs.getValue().get("end");
                if (end != null && end.before(new Date(System.currentTimeMillis() - 86400000)))
                    return;

                trainMapNew.put(pairs.getKey(), pairs.getValue());
            });

            trainMap.clear();
            trainMap.putAll(trainMapNew);
        }
        catch (ConcurrentModificationException e) { EastAngliaSignalMapServer.printThrowable(e, "TrainHistory"); }
    }

    public static void purgeHistories()
    {
        trainMap.clear();
    }
}