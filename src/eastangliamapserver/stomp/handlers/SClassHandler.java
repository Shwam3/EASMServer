package eastangliamapserver.stomp.handlers;

import eastangliamapserver.EastAngliaSignalMapServer;
import static eastangliamapserver.stomp.StompConnectionHandler.printSClass;
import java.text.SimpleDateFormat;
import java.util.*;

public class SClassHandler
{
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    public static Map<String, String> parseMessage(List<Map<String, Map<String, String>>> messageList)
    {
        //int messageCount = 0;
        Map<String, String> updateMap = new HashMap<>();

        try
        {
            //List<Map<String, Map<String, String>>> messageList = (List<Map<String, Map<String, String>>>) JSONParser.parseJSON("{\"TDMessage\":" + body + "}").get("TDMessage");
            //List<HashMap<String, String>> messages = new ArrayList<>();

            String areas = "LS SE SI CC CA EN WG SO SX";
            for (Map<String, Map<String, String>> map : messageList)
            {
                //messageCount++;

                String msgType = map.keySet().toArray(new String[0])[0];
                Map<String, String> indvMsg = map.get(msgType);

                if (!areas.contains(indvMsg.get("area_id")))
                    continue;

                indvMsg.put("address", indvMsg.get("area_id") + indvMsg.get("address"));

                switch (msgType.toUpperCase())
                {
                    case "SF_MSG":
                        indvMsg.put("old_data", "0");

                        String xAddr = indvMsg.get("address").substring(0, 3);
                        Map<String, Integer> yMap;

                        if (EastAngliaSignalMapServer.SClassMap.containsKey(xAddr))
                        {
                            yMap = EastAngliaSignalMapServer.SClassMap.get(xAddr);

                            if (yMap.containsKey(indvMsg.get("address").substring(3, 4)))
                            {
                                String oldBit = Integer.toHexString(yMap.get(indvMsg.get("address").substring(3, 4))).toUpperCase();

                                if (oldBit.length() % 2 != 0)
                                    oldBit = "0" + oldBit;

                                indvMsg.put("old_data", oldBit);
                            }
                        }
                        else
                            yMap = new HashMap<>();

                        yMap.put(indvMsg.get("address").substring(3, 4), Integer.parseInt(indvMsg.get("data"), 16));

                        try
                        {
                            char[] old  = toBinaryString(Integer.parseInt(indvMsg.get("old_data"), 16)).toCharArray();
                            char[] data = toBinaryString(Integer.parseInt(indvMsg.get("data"),     16)).toCharArray();

                            for (int i = 0; i < old.length; i++)
                            {
                                if (old[i] != data[i])
                                {
                                    String changedBit = Integer.toString(8 - i);
                                    indvMsg.put("bit", changedBit);

                                    printSClass(String.format("[%s] %s: %s -> %s (bit %s > %s)", EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(indvMsg.get("time")))), indvMsg.get("address"), indvMsg.get("old_data"), indvMsg.get("data"), changedBit, data[i]), false);

                                    EastAngliaSignalMapServer.SClassMap.put(xAddr, yMap);
                                    updateMap.put(indvMsg.get("address") + ":" + changedBit, String.valueOf(data[i]));
                                }
                            }
                        }
                        catch (Exception e) { printSClass(e.toString(), true); }

                        break;

                    case "SG_MSG":
                    case "SH_MSG":
                        break;
                }
            }
        }
        catch (Exception e)
        {
            printSClass("Exception in S-Class handler:", true);
            EastAngliaSignalMapServer.printThrowable(e, "S-Class");
        }

        return updateMap;
    }

    private static String toBinaryString(int i)
    {
        return String.format("%" + ((int) Math.ceil(Integer.toBinaryString(i).length() / 8f) * 8) + "s", Integer.toBinaryString(i)).replace(" ", "0");
    }
}