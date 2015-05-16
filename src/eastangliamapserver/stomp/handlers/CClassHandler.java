package eastangliamapserver.stomp.handlers;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.Train;
import static eastangliamapserver.stomp.StompConnectionHandler.printCClass;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CClassHandler
{
    public static synchronized Map<String, String> parseMessage(List<Map<String, Map<String, String>>> messageList)
    {
        List<Map<String, String>> interposeList = new ArrayList<>();
        List<Map<String, String>> stepList      = new ArrayList<>();
        List<Map<String, String>> cancelList    = new ArrayList<>();

        try
        {
            String areas = "LS SE SI CC CA EN WG SO SX";
            for (Map<String, Map<String, String>> map : messageList)
            {
                String msgType = map.keySet().toArray(new String[]{})[0];
                Map<String, String> indvMsg = map.get(msgType);

                switch (msgType)
                {
                    case "CA_MSG":
                        if (areas.contains(String.valueOf(indvMsg.get("area_id"))))
                            stepList.add(map.get(msgType));
                        break;
                    case "CB_MSG":
                        if (areas.contains(String.valueOf(indvMsg.get("area_id"))))
                            cancelList.add(map.get(msgType));
                        break;
                    case "CC_MSG":
                        if (areas.contains(String.valueOf(indvMsg.get("area_id"))))
                            interposeList.add(map.get(msgType));
                        break;
                }
            }
        }
        catch (Exception e)
        {
            printCClass("Exception in C-Class handler:", true);
            EastAngliaSignalMapServer.printThrowable(e, "C-Class");
        }

        Map<String, String> updateMap = new HashMap<>();

        try
        {
            //Needs to be done in this order
            //<editor-fold defaultstate="collapsed" desc="Interpose">
            for (Map<String, String> bodyMap : interposeList)
            {
                try
                {
                    bodyMap.put("a_to", bodyMap.get("area_id") + bodyMap.get("to"));

                    Berth toBerth = Berths.getBerth(bodyMap.get("a_to"));
                    Train newTrain = new Train(bodyMap.get("descr"), toBerth);

                    if (toBerth != null)
                    {
                        List<Train> adjTrains = toBerth.getAdjacentTrains();

                        for (Train train : adjTrains)
                            if (train.getHeadcode().equals(toBerth.getHeadcode()))
                                newTrain = train;

                        try
                        {
                            newTrain.getCurrentBerth().cancel(newTrain.getHeadcode(), null);
                        }
                        catch (NullPointerException e) {}

                        if (!toBerth.getHeadcode().equals(bodyMap.get("descr")))
                            toBerth.interpose(newTrain);


                        newTrain.setBerth(newTrain.getCurrentBerth());
                    }
                    else
                        Berths.addMissingBerth(bodyMap.get("a_to"));

                    Date modified = new Date(Long.parseLong(bodyMap.get("time")));
                    Berths.setBerthModifiedDate(bodyMap.get("a_to"), modified);

                    printCClass(String.format("[%s] Interpose %s to %s", EastAngliaSignalMapServer.sdfTime.format(modified), bodyMap.get("descr"), bodyMap.get("a_to")), false);

                    updateMap.put(bodyMap.get("a_to"), bodyMap.get("descr"));
                }
                catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "C-Class"); }
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="Step">
            for (Map<String, String> bodyMap : stepList)
            {
                try
                {
                    bodyMap.put("a_to",   bodyMap.get("area_id") + bodyMap.get("to"));
                    bodyMap.put("a_from", bodyMap.get("area_id") + bodyMap.get("from"));

                    Berth fromBerth = Berths.getBerth(bodyMap.get("a_from"));
                    Berth toBerth   = Berths.getBerth(bodyMap.get("a_to"));
                    Train newTrain  = new Train(bodyMap.get("descr"), toBerth);

                    if (fromBerth != null && toBerth == null)
                    {
                        if (fromBerth.hasTrain())
                            newTrain = fromBerth.getTrain();

                        if (fromBerth.hasAdjacentBerths())
                        {
                            for (Train train : fromBerth.getAdjacentTrains())
                            {
                                if (train.getHeadcode().equals(fromBerth.getHeadcode()))
                                {
                                    toBerth = train.getCurrentBerth();

                                    if (!toBerth.getHeadcode().equals(bodyMap.get("descr")))
                                        toBerth.interpose(newTrain);

                                    fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));
                                }
                            }
                        }
                        else
                            fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));

                        if (fromBerth.canStepToBerth(bodyMap.get("a_to"), bodyMap.get("a_from")))
                            for (String berthId : fromBerth.getStepToBerthsFor(bodyMap.get("a_to"), bodyMap.get("a_from")))
                                Berths.getBerth(berthId).suggestTrain(newTrain, fromBerth.getTypeFor(berthId, bodyMap.get("a_from")));

                        Berths.addMissingBerth(bodyMap.get("a_to"));
                    }
                    else if (fromBerth == null && toBerth != null)
                    {
                        if (toBerth.hasAdjacentBerths())
                        {
                            for (Train train : toBerth.getAdjacentTrains())
                            {
                                if (train.getHeadcode().equals(newTrain.getHeadcode()))
                                {
                                    fromBerth = train.getCurrentBerth();
                                    toBerth.interpose(train);

                                    fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));
                                }
                            }
                        }
                        else
                            toBerth.interpose(newTrain);

                        Berths.addMissingBerth(bodyMap.get("a_from"));
                        newTrain.setBerth(newTrain.getCurrentBerth());
                    }
                    else if (fromBerth != null && toBerth != null)
                    {
                        newTrain = fromBerth.hasTrain() && fromBerth.getHeadcode().equals(newTrain.getHeadcode()) ? fromBerth.getTrain() : newTrain;

                        if (toBerth.hasTrain())
                            if (!toBerth.getHeadcode().equals(newTrain.getHeadcode()) && toBerth.hasAdjacentBerths())
                                toBerth.interpose(newTrain);

                        fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));
                        toBerth.interpose(newTrain);

                        if (fromBerth.canStepToBerth(bodyMap.get("a_to"), bodyMap.get("a_from")))
                            for (String berthId : fromBerth.getStepToBerthsFor(bodyMap.get("a_to"), bodyMap.get("a_from")))
                                Berths.getBerth(berthId).suggestTrain(newTrain, fromBerth.getTypeFor(berthId, bodyMap.get("a_from")));

                        newTrain.setBerth(newTrain.getCurrentBerth());
                    }
                    else
                    {
                        Berths.addMissingBerth(bodyMap.get("a_to"));
                        Berths.addMissingBerth(bodyMap.get("a_from"));
                    }

                    Date modified = new Date(Long.parseLong(bodyMap.get("time")));
                    Berths.setBerthModifiedDate(bodyMap.get("a_to"), modified);
                    Berths.setBerthModifiedDate(bodyMap.get("a_from"), modified);

                    printCClass(String.format("[%s] Step %s from %s to %s", EastAngliaSignalMapServer.sdfTime.format(modified), bodyMap.get("descr"), bodyMap.get("a_from"), bodyMap.get("a_to")), false);

                    updateMap.put(bodyMap.get("a_to"), bodyMap.get("descr"));
                    updateMap.put(bodyMap.get("a_from"), "");
                }
                catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "C-Class"); }
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="Cancel">
            for (Map<String, String> bodyMap : cancelList)
            {
                try
                {
                    bodyMap.put("a_from", bodyMap.get("area_id") + bodyMap.get("from"));

                    Berth fromBerth = Berths.getBerth(bodyMap.get("a_from"));
                    Train newTrain = null;

                    if (fromBerth != null)
                    {
                        newTrain = fromBerth.getTrain();
                        fromBerth.cancel(bodyMap.get("descr"), null);

                        if (fromBerth.canStepToBerth("cancel", bodyMap.get("a_from")))
                            for (String berthId : fromBerth.getStepToBerthsFor("cancel", bodyMap.get("a_from")))
                                Berths.getBerth(berthId).suggestTrain(newTrain, fromBerth.getTypeFor(berthId, bodyMap.get("a_from")));
                    }
                    else
                        Berths.addMissingBerth(bodyMap.get("a_from"));

                    if (newTrain != null)
                        newTrain.setBerth(newTrain.getCurrentBerth());


                    Date modified = new Date(Long.parseLong(bodyMap.get("time")));
                    Berths.setBerthModifiedDate(bodyMap.get("a_from"), modified);

                    printCClass(String.format("[%s] Cancel %s from %s", EastAngliaSignalMapServer.sdfTime.format(modified), bodyMap.get("descr"), bodyMap.get("a_from")), false);

                    updateMap.put(bodyMap.get("a_from"), "");
                }
                catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "C-Class"); }
            }
            //</editor-fold>
        }
        catch (Exception e)
        {
            printCClass("Exception in C-Class handler:", true);
            EastAngliaSignalMapServer.printThrowable(e, "C-Class");
        }

        return updateMap;
    }
}