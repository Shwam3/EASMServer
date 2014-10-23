package eastangliamapserver.stomp.handlers;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.Train;
import static eastangliamapserver.stomp.StompConnectionHandler.printCClass;
import java.util.*;

public class CClassHandler
{
    public static synchronized HashMap<String, String> parseMessage(String body)
    {
        HashMap<String, String> updateMap = new HashMap<>();

        try
        {
            List<String> splitBody = Arrays.asList(body.replace("},{", ";").replace("[", "").replace("]", "").replace("\"", "").replace("{", "").replace("}", "").split(";"));
            //Needs to be done in this order
            //<editor-fold defaultstate="collapsed" desc="Interpose">
            for (String bodyBit : splitBody)
            {
                if (bodyBit.contains("area_id:AW,") || bodyBit.contains("area_id:UR,") || bodyBit.contains("area_id:U2,") || bodyBit.contains("area_id:U3,"))
                    continue;

                if (!bodyBit.contains("msg_type:CC,"))
                    continue;

                bodyBit = bodyBit.substring(7);
                HashMap<String, String> bodyMap = new HashMap<>();

                for (String bit : bodyBit.split(","))
                {
                    String[] bits = bit.replace(":", ",").split(",");
                    bodyMap.put(bits[0], bits[1]);
                }

                // Interpose
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
                    finally
                    {
                        if (!toBerth.getHeadcode().equals(bodyMap.get("descr")))
                            toBerth.interpose(newTrain);
                    }

                    newTrain.setBerth(newTrain.getCurrentBerth());
                }
                else
                    Berths.addMissingBerths(bodyMap.get("a_to"));

                printCClass(String.format(/*"%s: */"Interpose %s to %s", /*bodyMap.get("msg_type"), */bodyMap.get("descr"), bodyMap.get("a_to")), false);

                updateMap.put(bodyMap.get("a_to"), bodyMap.get("descr"));
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="Step">
            for (String bodyBit : splitBody)
            {
                if (bodyBit.contains("area_id:AW,") || bodyBit.contains("area_id:UR,") || bodyBit.contains("area_id:U2,") || bodyBit.contains("area_id:U3,"))
                    continue;

                if (!bodyBit.contains("msg_type:CA,"))
                    continue;

                bodyBit = bodyBit.substring(7);
                HashMap<String, String> bodyMap = new HashMap<>();

                for (String bit : bodyBit.split(","))
                {
                    String[] bits = bit.replace(":", ",").split(",");
                    bodyMap.put(bits[0], bits[1]);
                }

                //Step
                bodyMap.put("a_to",   bodyMap.get("area_id") + bodyMap.get("to"));
                bodyMap.put("a_from", bodyMap.get("area_id") + bodyMap.get("from"));

                Berth fromBerth = Berths.getBerth(bodyMap.get("a_from"));
                Berth toBerth   = Berths.getBerth(bodyMap.get("a_to"));
                Train newTrain  = new Train(bodyMap.get("descr"), toBerth);
                String stepType = "(eror)";

                if (fromBerth != null && toBerth == null)
                {
                    if (fromBerth.hasTrain())
                        newTrain = fromBerth.getTrain();

                    stepType = "(noTo)";
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
                                stepType = "(fkTo)";
                            }
                        }
                    }
                    else
                        fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));

                    if (fromBerth.canStepToBerth(bodyMap.get("a_to"), bodyMap.get("a_from")))
                        for (String berthId : fromBerth.getStepToBerthsFor(bodyMap.get("a_to"), bodyMap.get("a_from")))
                            Berths.getBerth(berthId).suggestTrain(newTrain, fromBerth.getTypeFor(berthId, bodyMap.get("a_from")));

                    Berths.addMissingBerths(bodyMap.get("a_to"));
                }
                else if (fromBerth == null && toBerth != null)
                {
                    stepType = "(noFr)";
                    if (toBerth.hasAdjacentBerths())
                    {
                        for (Train train : toBerth.getAdjacentTrains())
                        {
                            if (train.getHeadcode().equals(newTrain.getHeadcode()))
                            {
                                fromBerth = train.getCurrentBerth();
                                toBerth.interpose(train);

                                fromBerth.cancel(bodyMap.get("descr"), bodyMap.get("time"));
                                stepType = "(fkFr)";
                            }
                        }
                    }
                    else
                        toBerth.interpose(newTrain);

                    Berths.addMissingBerths(bodyMap.get("a_from"));
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

                    stepType = "(norm)";

                    newTrain.setBerth(newTrain.getCurrentBerth());
                }
                else
                {
                    stepType = "(none)";
                    Berths.addMissingBerths(bodyMap.get("a_to"));
                    Berths.addMissingBerths(bodyMap.get("a_from"));
                }

                printCClass(String.format(/*"%s: */"Step %s from %s to %s %s", /*bodyMap.get("msg_type"), */bodyMap.get("descr"), bodyMap.get("a_from"), bodyMap.get("a_to"), stepType), false);

                updateMap.put(bodyMap.get("a_to"), bodyMap.get("descr"));
                updateMap.put(bodyMap.get("a_from"), "");
            }
            //</editor-fold>

            //<editor-fold defaultstate="collapsed" desc="Cancel">
            for (String bodyBit : splitBody)
            {
                if (bodyBit.contains("area_id:AW,") || bodyBit.contains("area_id:UR,") || bodyBit.contains("area_id:U2,") || bodyBit.contains("area_id:U3,"))
                    continue;

                if (!bodyBit.contains("msg_type:CB,"))
                    continue;

                bodyBit = bodyBit.substring(7);
                HashMap<String, String> bodyMap = new HashMap<>();

                for (String bit : bodyBit.split(","))
                {
                    String[] bits = bit.replace(":", ",").split(",");
                    bodyMap.put(bits[0], bits[1]);
                }

                //Cancel
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
                    Berths.addMissingBerths(bodyMap.get("a_from"));

                if (newTrain != null)
                    newTrain.setBerth(newTrain.getCurrentBerth());

                printCClass(String.format(/*"%s: */"Cancel %s from %s", /*bodyMap.get("msg_type"), */bodyMap.get("descr"), bodyMap.get("a_from")), false);

                updateMap.put(bodyMap.get("a_from"), "");
            }
            //</editor-fold>
        }
        catch (Exception e)
        {
            printCClass("Exception in C-Class handler:\n" + String.valueOf(e), true);
        }
        finally
        {
            return updateMap;
        }
    }
}