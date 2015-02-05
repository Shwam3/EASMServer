package eastangliamapserver.stomp.handlers;

import eastangliamapserver.EastAngliaSignalMapServer;
import static eastangliamapserver.stomp.StompConnectionHandler.printMovement;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jsonparser.JSONParser;

public class MVTHandler
{
    public static Map<String, Map<String, String>> parseMessage(String message)
    {
        List<Map> messageList = (List<Map>) JSONParser.parseJSON("{\"MVTMessage\":" + message + "}").get("MVTMessage");

        for (Map<String, Map<String, Object>> map : messageList)
        {
            Map<String, Object> header = map.get("header");
            Map<String, Object> body   = map.get("body");

            switch (String.valueOf(header.get("msg_type")))
            {
                case "0001": // Activation
                    printMovement(String.format("Train %s / %s / %s activated %s at %s (%s shedule from %s, Starts at %s, TOPS address: %s)",
                            String.valueOf(body.get("train_uid")).replace(" ", "O"),
                            String.valueOf(body.get("train_id")),
                            String.valueOf(body.get("schedule_wtt_id")),
                            String.valueOf(body.get("train_call_type")).replace("AUTOMATIC", "automatically").replace("MANUAL", "manually"),
                            EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(String.valueOf(body.get("creation_timestamp"))))),
                            String.valueOf(body.get("schedule_type")).replace("O", "VAR").replace("N", "STP").replace("P", "WTT").replace("C", "CAN"),
                            String.valueOf(body.get("schedule_source")).replace("C", "CIF/ITPS").replace("V", "VSTP/TOPS"),
                            String.valueOf(body.get("train_file_address")),
                            String.valueOf(body.get("origin_dep_timestamp"))
                        ), false);
                    break;

                case "0002": // Cancellation
                    printMovement("Cancellation: " + body.toString(), false);
                    break;

                case "0003": // Movement
                    try
                    {
                    printMovement(String.format("Train %s %s %s %s%s at %s %s(plan %s, GBTT %s)",
                            String.valueOf(body.get("train_id")),
                            String.valueOf(body.get("event_type")).replace("ARRIVAL", "arrived at").replace("DEPARTURE", "departed from"),
                            String.valueOf(body.get("loc_stanox")),
                            String.valueOf(body.get("timetable_variation")).equals("0") ? "" : String.valueOf(body.get("timetable_variation")) + " mins ",
                            String.valueOf(body.get("variation_status")).toLowerCase(),
                            String.valueOf(body.get("actual_timestamp")).isEmpty() ? "n/a" : EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(String.valueOf(body.get("actual_timestamp"))))),
                            String.valueOf(body.get("train_terminated")).replace("true", "and terminated ").replace("false", ""),
                            String.valueOf(body.get("planned_timestamp")).isEmpty() ? "n/a" : EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(String.valueOf(body.get("planned_timestamp"))))),
                            String.valueOf(body.get("gbtt_timestamp")).isEmpty() ? "n/a" : EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(String.valueOf(body.get("gbtt_timestamp")))))
                            ), false);
                    } catch (Exception e) { printMovement(e.toString(), true); }
                    break;

                case "0005": // Reinstatement (De-Cancellation)
                    printMovement("Reinstatement: " + body.toString(), false);
                    break;

                case "0006": // Change Origin
                    printMovement("Origin Change: " + body.toString(), false);
                    break;

                case "0007": // Change Identity
                    printMovement("Identity Change: " + body.toString(), false);
                    break;

                case "0004": // UID Train
                case "0008": // Change Loaction
                default:     // Other (e.g. null)
                    printMovement("Erronous message received (" + String.valueOf(header.get("msg_type")) + ")", true);
                    break;
            }
        }

        return new HashMap<>(0);
    }
}