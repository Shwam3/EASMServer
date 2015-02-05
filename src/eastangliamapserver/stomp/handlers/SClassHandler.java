package eastangliamapserver.stomp.handlers;

import eastangliamapserver.EastAngliaSignalMapServer;
import static eastangliamapserver.EastAngliaSignalMapServer.SClassMap;
import static eastangliamapserver.stomp.StompConnectionHandler.printSClass;
import java.util.*;

public class SClassHandler
{
    public static Map<String, String> parseMessage(List<Map<String, Map<String, String>>> messageList)
    {
        Map<String, String> updateMap = new HashMap<>(messageList.size());

        try
        {
            String areas = "LS SE SI CC CA EN WG SO SX";
            for (Map<String, Map<String, String>> map : messageList)
            {
                String msgType = map.keySet().toArray(new String[0])[0];
                Map<String, String> indvMsg = map.get(msgType);

                if (!areas.contains(indvMsg.get("area_id")))
                    continue;

                indvMsg.put("address", indvMsg.get("area_id") + indvMsg.get("address"));

                switch (msgType.toUpperCase())
                {
                    case "SF_MSG":
                        /*String shortAddress = indvMsg.get("address");
                        if (SClassMap.containsKey(shortAddress))
                            indvMsg.put("old_data", SClassMap.get(shortAddress));
                        else
                            indvMsg.put("old_data", "-1");*/

                        try
                        {
                            char[] data = toBinaryString(Integer.parseInt(indvMsg.get("data"), 16)).toCharArray();

                            for (int i = 0; i < data.length; i++)
                            {
                                String changedBit = Integer.toString(8 - i);
                                String address = indvMsg.get("address") + ":" + changedBit;

                                if (!SClassMap.containsKey(address) || !SClassMap.get(address).equals(String.valueOf(data[i])))
                                {
                                    printSClass(String.format("[%s] Change %s from %s to %s",
                                            EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(indvMsg.get("time")))),
                                            indvMsg.get("address") + ":" + changedBit,
                                            SClassMap.containsKey(address) ? SClassMap.get(address) : "0",
                                            data[i]),
                                            false);

                                    updateMap.put(address, String.valueOf(data[i]));
                                    SClassMap.put(address, String.valueOf(data[i]));
                                }
                            }

                            SClassMap.put(indvMsg.get("address"), indvMsg.get("data"));
                        }
                        catch (Exception e) { printSClass(e.toString(), true); }

                        break;

                    case "SG_MSG":
                    case "SH_MSG":
                        printSClass(indvMsg.get("msg_type") + " message: " + indvMsg.toString(), false);

                        try
                        {
                            String addrStart = indvMsg.get("address").substring(0, 3);
                            String addrEnd = indvMsg.get("address").substring(3);
                            int data[] = {Integer.parseInt(indvMsg.get("data").substring(0, 2), 16), Integer.parseInt(indvMsg.get("data").substring(2, 4), 16), Integer.parseInt(indvMsg.get("data").substring(4, 6), 16), Integer.parseInt(indvMsg.get("data").substring(6, 8), 16)};
                            String[] addresses = {indvMsg.get("address"),
                                addrStart + (addrEnd.equals("0") ? "1" : addrEnd.equals("4") ? "5" : addrEnd.equals("8") ? "9" : "D"),
                                addrStart + (addrEnd.equals("0") ? "2" : addrEnd.equals("4") ? "6" : addrEnd.equals("8") ? "A" : "E"),
                                addrStart + (addrEnd.equals("0") ? "3" : addrEnd.equals("4") ? "7" : addrEnd.equals("8") ? "B" : "F")};

                            for (int i = 0; i < data.length; i++)
                                SClassMap.put(addresses[i], Integer.toString(data[i]));
                        } catch(Exception e) { printSClass(e.toString(), true); }
                        break;
                }
            }
        }
        catch (Exception e)
        {
            printSClass("Exception in S-Class handler:", true);
            EastAngliaSignalMapServer.printThrowable(e, "S-Class");
        }

        return new HashMap<>(updateMap);
    }

    public static String toBinaryString(int i)
    {
        return String.format("%" + ((int) Math.ceil(Integer.toBinaryString(i).length() / 8f) * 8) + "s", Integer.toBinaryString(i)).replace(" ", "0");
    }
}