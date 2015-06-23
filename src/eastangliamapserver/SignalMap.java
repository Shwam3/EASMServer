package eastangliamapserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import jsonparser.JSONParser;

public class SignalMap
{
    protected static boolean isCreated = false;

    public static void createBerthObjects()
    {
        File berthsFile = new File(EastAngliaSignalMapServer.storageDir, "berths.json");

        if (!berthsFile.exists())
        {
            EastAngliaSignalMapServer.printMain("\"" + berthsFile.toString() + "\" doesnt exist, unable to load berth data", true);
            JOptionPane.showMessageDialog(null, "Unable to locate berths.json file\nUnable to load berth data", "SignalMap", JOptionPane.ERROR_MESSAGE);
        }
        else
        {
            try (BufferedReader br = new BufferedReader(new FileReader(berthsFile)))
            {
                String json = "";

                String line;
                while ((line = br.readLine()) != null)
                    json += line.trim();

                List<Map<String, Object>> berths = (List<Map<String, Object>>) JSONParser.parseJSON(json).get("berths");
                for (Map<String, Object> map : berths)
                {
                    Berth berth = Berths.createOrGetBerth(String.valueOf(map.get("berthId")));
                    if (map.containsKey("name"))
                        berth.setName(String.valueOf(map.get("name")));

                    if (map.containsKey("stepInstructions"))
                    {
                        List<String> instructions = (List<String>) map.get("stepInstructions");
                        for (int i = 0; i < instructions.size(); i += 4)
                            berth.addStepToBerth(instructions.get(i), instructions.get(i+1), instructions.get(i+2), instructions.get(i+3));
                    }

                    if (map.containsKey("defaultValue"))
                    {
                        berth.interpose(new Train(String.valueOf(map.get("defaultValue")), berth));
                        Berths.setBerthModifiedDate(berth.getId(), new Date());
                    }
                }
                isCreated = true;
            }
            catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "Berths"); }
        }

        Berth motd = Berths.createOrGetBerth("XXMOTD");

        File motdFile = new File(EastAngliaSignalMapServer.storageDir, "MOTD.txt");
        if (motdFile.exists())
        {
            try (BufferedReader br = new BufferedReader(new FileReader(motdFile)))
            {
                String motdStr = "";

                String line;
                while ((line = br.readLine()) != null)
                    motdStr += line;

                /*String errorMessage = "Disconnected from Network Rail's servers";

                if (motdStr.contains("No problems"))
                    motdStr = motdStr.replace("No problems", errorMessage);
                else
                    motdStr += errorMessage;*/

                if (!motdStr.isEmpty())
                    motd.interpose(new Train(motdStr.trim(), motd));
            }
            catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "SignalMap"); }
        }
    }

    public SignalMap readFromMap()
    {
        for (Map.Entry<String, Berth> pairs : Berths.getEntrySet())
            if (EastAngliaSignalMapServer.CClassMap.containsKey(pairs.getKey()))
                pairs.getValue().interpose(new Train(EastAngliaSignalMapServer.CClassMap.get(pairs.getKey()), pairs.getValue()));

        return this;
    }
}