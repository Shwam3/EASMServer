/*package eastangliamapserver.stomp.handlers;

import eastangliamapserver.EastAngliaSignalMapServer;
import static eastangliamapserver.stomp.StompConnectionHandler.printSClass;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SClassHandler extends Thread
{
    private final Map<String, String>    header;
    private final String body;

    SimpleDateFormat sdf  = new SimpleDateFormat("HH:mm:ss");

    public SClassHandler(Map header, String body)
    {
        super("S-Class handler");

        this.header = header;
        this.body   = body;
    }

    @Override
    public void run()
    {
        try
        {
            String[] splitBody = body.replace("},{", ";").replace("[", "").replace("]", "").replace("\"", "").replace("{", "").replace("}", "").split(";");

            for (String bodyBit : splitBody)
            {
                if (bodyBit.contains("area_id:AW,") || bodyBit.contains("area_id:UR,") || bodyBit.contains("area_id:U2,") || bodyBit.contains("area_id:U3,"))
                    continue;

                bodyBit = bodyBit.substring(7);
                HashMap<String, String> bodyMap = new HashMap<>();

                for (String bit : bodyBit.split(","))
                {
                    String[] bits = bit.replace(":", ",").split(",");
                    bodyMap.put(bits[0], bits[1]);
                }

                if (bodyMap.get("msg_type").equals("SF") || bodyMap.get("msg_type").equals("SG") || bodyMap.get("msg_type").equals("SH"))
                {
                    bodyMap.put("time_formatted", sdf.format(new Date(Long.parseLong(bodyMap.get("time")))));
                    bodyMap.put("address", bodyMap.get("area_id") + bodyMap.get("address"));
                    bodyMap.put("old_data", "--");

                    String xAddr = bodyMap.get("address").substring(0, 3);
                    HashMap<String, Integer> yMap;

                    if (EastAngliaSignalMapServer.SClassMap.containsKey(xAddr))
                    {
                        yMap = EastAngliaSignalMapServer.SClassMap.get(xAddr);

                        if (yMap.containsKey(bodyMap.get("address").substring(3, 4)))
                        {
                            String oldBit = Integer.toHexString(yMap.get(bodyMap.get("address").substring(3, 4))).toUpperCase();

                            if (oldBit.length() % 2 != 0)
                                oldBit = "0" + oldBit;

                            bodyMap.put("old_data", oldBit);
                        }
                    }
                    else
                    {
                        yMap = new HashMap<>();
                    }

                    if (bodyMap.get("data").length() <= 2)
                    {
                        String yAddr = bodyMap.get("address").substring(3, 4);
                        yMap.put(yAddr, Integer.parseInt(bodyMap.get("data"), 16));

                        printSClass(String.format("(%s) %s: Set %s to %s from %s (%s)", bodyMap.get("time_formatted"), bodyMap.get("msg_type"), bodyMap.get("address"), bodyMap.get("data"), bodyMap.get("old_data"), header.get("nice-message-id")), false);
                    }
                    else
                    {
                        int index = Integer.parseInt(bodyMap.get("address").substring(3, 4), 16);
                        for (int i = 0; i < bodyMap.get("data").length() / 2; i++)
                        {
                            String addr = Integer.toHexString(index + i);
                            int    data = Integer.parseInt(bodyMap.get("data").substring(i * 2, (i * 2) + 2), 16);
                            bodyMap.put("old_data", (bodyMap.get("old_data") + EastAngliaSignalMapServer.SClassMap.get(bodyMap.get("address").substring(0, 3) + addr)).replace("null", ""));
                            yMap.put(addr, data);

                        }

                        printSClass(String.format("(%s) %s: Set %s to %s from %s (%s)", bodyMap.get("time_formatted"), bodyMap.get("msg_type"), bodyMap.get("address"), bodyMap.get("data"), bodyMap.get("old_data"), header.get("nice-message-id")), false);
                    }

                    EastAngliaSignalMapServer.SClassMap.put(xAddr, yMap);
                    EastAngliaSignalMapServer.SClassLog.add(bodyMap);
                }
            }
        }
        catch (Exception e)
        {
            printSClass("Exception in S-Class handler:", true);
            printSClass(e.toString(), true);
        }
    }
}
*/