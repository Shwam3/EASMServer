package eastangliamapserver.stomp.handlers;

import eastangliamapserver.EastAngliaSignalMapServer;
import static eastangliamapserver.stomp.StompConnectionHandler.printRTPPM;
import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jsonparser.JSONParser;

public class RTPPMHandler
{
    public final static HashMap<String, Operator> operators = new HashMap<>();
    private static boolean initialised = false;

    public static void initPPM()
    {
        if (!initialised)
        {
            File saveFile = new File(EastAngliaSignalMapServer.storageDir, "RTPPM.save");
            if (saveFile.exists())
            {
                try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(saveFile))))
                {
                    Object obj = ois.readObject();

                    if (obj instanceof String)
                        readData((HashMap<String, HashMap<String, Object>>) obj);
                }
                catch (ClassNotFoundException e) {}
                catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "RTPPM"); }
            }

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            int mins = calendar.get(Calendar.MINUTE);
            calendar.add(Calendar.MINUTE, 15 - (mins % 15));
            calendar.set(Calendar.SECOND, 20);
            calendar.set(Calendar.MILLISECOND, 500);

            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
            {
                @Override
                public void run()
                {
                    uploadHTML();
                }
            }, calendar.getTimeInMillis() - System.currentTimeMillis(), 900000, TimeUnit.MILLISECONDS);

            initialised = true;
        }
    }

    public static synchronized void parseMessage(String body)
    {
        HashMap<String, Object> ppmMap = (HashMap<String, Object>) JSONParser.parseJSON(body).get("RTPPMDataMsgV1");
        String incidentMessages = String.valueOf(((Map) ((Map) ppmMap.get("RTPPMData")).get("NationalPage")).get("WebMsgOfMoment")).trim();

        try
        {
            List<HashMap<String, Object>> operatorsPPM = (List<HashMap<String, Object>>) ((HashMap) ppmMap.get("RTPPMData")).get("OperatorPage");
            for (HashMap<String, Object> map : operatorsPPM)
            {
                Operator operator;
                if (operators.containsKey(String.valueOf(((Map) map.get("Operator")).get("name"))))
                    operator = operators.get(String.valueOf(((Map) map.get("Operator")).get("name")));
                else
                {
                    operator = new Operator(String.valueOf(((Map) map.get("Operator")).get("name")), Integer.parseInt(String.valueOf(((Map) map.get("Operator")).get("code"))));
                    operators.put(String.valueOf(((Map) map.get("Operator")).get("name")), operator);
                }

                operator.putService("Total", (HashMap<String, Object>) map.get("Operator"));

                if (map.get("OprServiceGrp") instanceof HashMap)
                    operator.putService(String.valueOf(((HashMap) map.get("OprServiceGrp")).get("name")), (HashMap<String, Object>) map.get("OprServiceGrp"));
                else if (map.get("OprServiceGrp") instanceof List)
                    for (Object serviceObj : (List) map.get("OprServiceGrp"))
                        operator.putService(String.valueOf(((HashMap) serviceObj).get("name")), ((HashMap) serviceObj));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            File saveFile = new File(EastAngliaSignalMapServer.storageDir, "RTPPM.save");
            if (!saveFile.exists())
                saveFile.getParentFile().mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(saveFile)))
            {
                oos.writeObject(getPPMData());
            }

            printRTPPM("Saved file (" + (saveFile.length() / 1024L) + "kb)", false);
        }
        catch (IOException e) { e.printStackTrace(); }

        StringBuilder html = new StringBuilder("<!DOCTYPE html>");
        html.append("<html>");
        html.append("  <head>");
        html.append("    <title>Real-Time PPM</title>");
        html.append("    <meta charset=\"utf-8\">");
        html.append("    <meta content=\"width=device-width,initial-scale=1.0\" name=\"viewport\">");
        html.append("    <meta http-equiv=\"refresh\" content=\"600\">");
        html.append("    <meta name=\"description\" content=\"Real-Time PPM\">");
        html.append("    <meta name=\"author\" content=\"Cameron Bird\">");
        html.append("    <link rel=\"icon\" type=\"image/x-icon\" href=\"/favicon.ico\">");
        html.append("    <link rel=\"stylesheet\" type=\"text/css\" href=\"/default.css\">");
        html.append("  </head>");
        html.append("  <body>");
        html.append("    <div class=\"ppmMain\">");
        html.append("    <p id=\"title\"><abbr title=\"Real-Time (15 min intervals) Public Performance Measure\">Real-Time PPM</abbr>&nbsp;<span class=\"small\">").append(new SimpleDateFormat("(dd/mm HH:mm)").format(new Date())).append("</span></p>");
        //html.append("    <p id=\"title\"><img id=\"logo\" src=\"/logo.png\"><abbr title=\"Real-Time (15 min intervals) Public Performance Measure\">Real-Time PPM</abbr>&nbsp;<span class=\"small\">").append(new SimpleDateFormat("(dd/mm HH:mm)").format(new Date())).append("</span></p>");

        String[] keys = operators.keySet().toArray(new String[0]);
        Arrays.sort(keys, String.CASE_INSENSITIVE_ORDER);
        for (String key : keys)
            html.append(operators.get(key).htmlString());

        if (incidentMessages.trim().equals("null") || incidentMessages.trim().equals(""))
            incidentMessages = "No messages";

        html.append("      <div id=\"ppmIncidents\">");
        html.append("        <p><b>Incident Messages:</b></p>");

        for (String message : incidentMessages.split("\\n"))
        html.append("        <p>").append(message.trim()).append("</p>)");

        html.append("      </div>");
        html.append("    </div>");
        html.append("    <script type=\"text/javascript\">setInterval(function() { document.reload(true) }, 600000);</script>");
        html.append("  </body>");

        try
        {
            File htmlFile = new File(EastAngliaSignalMapServer.storageDir, "ppm.html");
            if (!htmlFile.exists())
            {
                htmlFile.getParentFile().mkdirs();
                htmlFile.createNewFile();
            }

            try (BufferedWriter out = new BufferedWriter(new FileWriter(htmlFile)))
            {
                out.write(html.toString().replace("  ", "").replace("\n", ""));
            }
            catch (FileNotFoundException e) {}
            catch (IOException e)  { EastAngliaSignalMapServer.printThrowable(e, "RTPPM HTML"); }

            printRTPPM("Saved html (" + (htmlFile.length() / 1024L) + "kb)", false);
        }
        catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "RTPPM HTML"); }
    }

    public static class Operator
    {
        public  final String NAME;
        public  final int    CODE;
        private       HashMap<String, HashMap<String, String>> ppmMap     = new HashMap<>();
        private       HashMap<String, HashMap<String, Object>> serviceMap = new HashMap<>();
        private       String keySymbol = "";

        public Operator(String name, int code)
        {
            NAME = name;
            CODE = code;
        }

        public void addPPMData(HashMap<String, HashMap<String, String>> map)
        {
            ppmMap.putAll(map);
        }

        public HashMap<String, String> getService(String serviceName)
        {
            return ppmMap.get(serviceName);
        }

        public void setKeySymbol(String keySymbol) { this.keySymbol = keySymbol; }

        public String getKeySymbol() { return keySymbol; }

        public void putService(String serviceName, HashMap<String, Object> fullIndividualServiceMap)
        {
            HashMap<String, Object> trimmedServiceMap = new HashMap<>();
            if (serviceMap.containsKey(serviceName))
                trimmedServiceMap = serviceMap.get(serviceName);

            keySymbol = String.valueOf(fullIndividualServiceMap.get("keySymbol"));
            keySymbol = keySymbol.replace("null", " ");

            trimmedServiceMap.put("CancelVeryLate", (String)  fullIndividualServiceMap.get("CancelVeryLate"));
            trimmedServiceMap.put("Late",           (String)  fullIndividualServiceMap.get("Late"));
            trimmedServiceMap.put("OnTime",         (String)  fullIndividualServiceMap.get("OnTime"));
            trimmedServiceMap.put("Total",          (String)  fullIndividualServiceMap.get("Total"));
            trimmedServiceMap.put("PPM",            (HashMap) fullIndividualServiceMap.get("PPM"));
            trimmedServiceMap.put("RollingPPM",     (HashMap) fullIndividualServiceMap.get("RollingPPM"));

            serviceMap.put(serviceName, trimmedServiceMap);
        }

        public String prettyString()
        {
            String formattedMap  = "    Name: " + NAME + " (" + CODE + ")";
                   formattedMap += "\n    Services:";

            Object[] keys = serviceMap.keySet().toArray();
            Arrays.sort(keys);

            int length = 0;
            for (Object key : keys)
                if (String.valueOf(key).length() > length)
                    length = String.valueOf(key).length();

            for (Object key : keys)
            {
                if (key.equals("Total"))
                    continue;

                HashMap<String, Object> map = serviceMap.get(String.valueOf(key));
                if (map != null)
                {
                    formattedMap += "\n      " + lengthen(String.valueOf(key + ": "), length + 2);

                    formattedMap += "PPM: " + lengthen(((Map) map.get("PPM")).get("text") + "%, ", 6);
                    if (!String.valueOf(((Map) map.get("RollingPPM")).get("text")).equals("-1"))
                        formattedMap += "Rolling PPM: " + lengthen(((Map) map.get("RollingPPM")).get("text") + "%", 4) + " (" + String.valueOf(((Map) map.get("RollingPPM")).get("trendInd")) + "), ";
                    else
                        formattedMap += "Rolling PPM: --------, ";
                    formattedMap += "(Total: " + lengthen(String.valueOf(map.get("Total")) + ",", 5) + " On Time: " + lengthen(String.valueOf(map.get("OnTime")) + ",", 5) + " Late: " + lengthen(String.valueOf(map.get("Late")) + ",", 4) + " Very Late/Cancelled: " + String.valueOf(map.get("CancelVeryLate")) + ")";
                }
            }

            HashMap<String, Object> map = serviceMap.get("Total");
            if (map != null)
            {
                formattedMap += "\n      " + lengthen("Total: ", length + 2);

                formattedMap += "PPM: " + lengthen(((Map) map.get("PPM")).get("text") + "%, ", 6);
                if (!String.valueOf(((Map) map.get("RollingPPM")).get("text")).equals("-1"))
                    formattedMap += "Rolling PPM: " + lengthen(((Map) map.get("RollingPPM")).get("text") + "%", 4) + " (" + String.valueOf(((Map) map.get("RollingPPM")).get("trendInd")) + "), ";
                else
                    formattedMap += "Rolling PPM: --------, ";
                formattedMap += "(Total: " + lengthen(String.valueOf(map.get("Total")) + ",", 5) + " On Time: " + lengthen(String.valueOf(map.get("OnTime")) + ",", 5) + " Late: " + lengthen(String.valueOf(map.get("Late")) + ",", 4) + " Very Late/Cancelled: " + String.valueOf(map.get("CancelVeryLate")) + ")";
            }

            return formattedMap;
        }

        public String htmlString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append("    <h3 class=\"ppmTableTitle\">").append(NAME).append(" (").append(CODE).append(")").append(keySymbol.trim().replace("*", " (10 mins)").replace("^", " (5 mins)")).append("<br/></h3>");
            sb.append("    <table class=\"ppmTable\" sortable>");
            sb.append("      <tr>");
            sb.append("        <th class=\"ppmTable\" rowspan=\"2\">Service Name</th>");
            sb.append("        <th class=\"ppmTable\" rowspan=\"2\"><abbr title=\"Public Performance Measure\">PPM</abbr></th>");
            sb.append("        <th class=\"ppmTable\" colspan=\"2\"><abbr title=\"Rolling Public Performance Measure\">Rolling PPM</abbr></th>");
            sb.append("        <th class=\"ppmTable\" colspan=\"4\"><abbr title=\"Public Performance Measure Breakdown\">PPM Breakdown</th>");
            sb.append("      </tr>");
            sb.append("      <tr>");
            sb.append("        <th class=\"ppmTable\">%</th>");
          //sb.append("        <th class=\"ppmTable\">▲▼</th>");
            sb.append("        <th class=\"ppmTable\">&#x25B2;&#x25BC;</th>");
            sb.append("        <th class=\"ppmTable\">Total</th>");
            sb.append("        <th class=\"ppmTable\"><abbr title=\"On Time\">OT</abbr></th>");
            sb.append("        <th class=\"ppmTable\"><abbr title=\"Late\">L</abbr></th>");
            sb.append("        <th class=\"ppmTable\"><abbr title=\"Cancelled/Significant Lateness\">C/SL</abbr></th>");
            sb.append("      </tr>");

            String[] keys = serviceMap.keySet().toArray(new String[0]);
            Arrays.sort(keys, String.CASE_INSENSITIVE_ORDER);

            for (String key : keys)
            {
                if (key.equals("Total"))
                    continue;

                sb.append("      <tr>");

                HashMap<String, Object> map = serviceMap.get(key);
                if (map != null)
                {
                    sb.append("         <td class=\"ppmTable\">").append(key.replace("&", "&amp;")).append("</td>");

                    String ppm = String.valueOf(((Map) map.get("PPM")).get("text"));
                    if (!ppm.equals("-1"))
                        sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(String.valueOf(((Map) map.get("PPM")).get("rag")))).append("\">").append(ppm).append("%</td>");
                    else
                        sb.append("        <td class=\"ppmTable\" style=\"color:black\">N/A</td>");

                    String rollPPM = String.valueOf(((Map) map.get("RollingPPM")).get("text"));
                    if (!rollPPM.equals("-1"))
                    {
                        sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(String.valueOf(((Map) map.get("RollingPPM")).get("rag")))).append("\">").append(rollPPM).append("%").append("</td>");
                        sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(getTrendArrow(String.valueOf(((Map) map.get("RollingPPM")).get("trendInd"))))).append("\">").append(getTrendArrow(String.valueOf(((Map) map.get("RollingPPM")).get("trendInd")))).append("</td>");
                    }
                    else
                    {
                        sb.append("        <td class=\"ppmTable\" style=\"color:black\">N/A</td>");
                        sb.append("        <td class=\"ppmTable\" style=\"color:black\">N/A</td>");
                    }

                    sb.append("        <td class=\"ppmTable\">").append(map.get("Total")).append("</td>");
                    sb.append("        <td class=\"ppmTable\">").append(map.get("OnTime")).append("</td>");
                    sb.append("        <td class=\"ppmTable\">").append(map.get("Late")).append("</td>");
                    sb.append("        <td class=\"ppmTable\">").append(map.get("CancelVeryLate")).append("</td>");
                }

                sb.append("      </tr>");
            }

            HashMap<String, Object> map = serviceMap.get("Total");
            if (map != null)
            {
                sb.append("      <tr>");

                sb.append("        <td class=\"ppmTable\">Total</td>");
                sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(String.valueOf(((Map) map.get("PPM")).get("rag")))).append("\">").append(((Map) map.get("PPM")).get("text")).append("%").append("</td>");

                String rollPPM = String.valueOf(((Map) map.get("RollingPPM")).get("text"));
                if (!rollPPM.equals("-1"))
                {
                    sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(String.valueOf(((Map) map.get("RollingPPM")).get("rag")))).append("\">").append(rollPPM).append("%").append("</td>");
                    sb.append("        <td class=\"ppmTable\" style=\"color:").append(getColour(String.valueOf(((Map) map.get("RollingPPM")).get("rag")))).append("\">").append(getTrendArrow(String.valueOf(((Map) map.get("RollingPPM")).get("trendInd")))).append("</td>");
                }
                else
                {
                    sb.append("        <td class=\"ppmTable\" style=\"color:black\">N/A</td>");
                    sb.append("        <td class=\"ppmTable\" style=\"color:black\">N/A</td>");
                }

                sb.append("        <td class=\"ppmTable\">").append(map.get("Total")).append("</td>");
                sb.append("        <td class=\"ppmTable\">").append(map.get("OnTime")).append("</td>");
                sb.append("        <td class=\"ppmTable\">").append(map.get("Late")).append("</td>");
                sb.append("        <td class=\"ppmTable\">").append(map.get("CancelVeryLate")).append("</td>");

                sb.append("      </tr>");
            }

            sb.append("    </table>");

            return sb.toString().replace("▲", "&#x25B2;").replace("▬", "&#9644;").replace("▼", "&#x25BC;");
        }

        public HashMap<String, Object> getMap()
        {
            HashMap<String, Object> map = new HashMap<>();

            map.put("name",       NAME);
            map.put("code",       CODE);
            map.put("keySymbol",  keySymbol);
            map.put("ppmMap",     ppmMap);
            map.put("serviceMap", serviceMap);

            return map;
        }

        public void readMap(HashMap<String, Object> map)
        {
            if (map.containsKey("keySymbol")  && map.get("keySymbol")  != null) keySymbol  = (String) map.get("keySymbol");
            if (map.containsKey("ppmMap")     && map.get("ppmMap")     != null) ppmMap     = (HashMap<String, HashMap<String, String>>) map.get("ppmMap");
            if (map.containsKey("serviceMap") && map.get("serviceMap") != null) serviceMap = (HashMap<String, HashMap<String, Object>>) map.get("serviceMap");
        }

        private String getTrendArrow(String trendChar)
        {
            switch (trendChar)
            {
                case "+":
                    return "▲";
                case "=":
                    return "▬";
                case "-":
                    return "▼";
                default:
                    return "";
            }
        }

        private String getColour(String rag)
        {
            switch (rag)
            {
                case "▲":
                case "G":
                    return "#25B225";

                case "▬":
                case "A":
                    return "#D0A526";

                case "▼":
                case "R":
                    return "#C50000";

                default:
                    return "#FFFFFF";
            }
        }

        private String lengthen(String string, int length)
        {
                while (string.length() < length)
                    string += " ";

                return string.substring(0, length);
        }
    }

    public static HashMap<String, HashMap<String, Object>> getPPMData()
    {
        HashMap<String, HashMap<String, Object>> map = new HashMap<>();

        for (Map.Entry<String, Operator> pairs : operators.entrySet())
            map.put(pairs.getKey(), pairs.getValue().getMap());

        return map;
    }

    public static void readData(HashMap<String, HashMap<String, Object>> dataMap)
    {
        for (Map.Entry<String, HashMap<String, Object>> pairs : dataMap.entrySet())
        {
            if (!operators.containsKey(pairs.getKey()))
                operators.put(pairs.getKey(), new Operator((String) pairs.getValue().get("name"), (int) pairs.getValue().get("code")));

            operators.get(pairs.getKey()).readMap(pairs.getValue());
        }
    }

    public static void uploadHTML()
    {
        try
        {
            File htmlFile = new File(EastAngliaSignalMapServer.storageDir, "ppm.html");
            if (htmlFile.exists())
            {
                String html = "<html>Upload failed</html>";
                try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(htmlFile))))
                {
                    html = "";
                    String line;
                    while ((line = in.readLine()) != null)
                        html += line + "\n";
                }
                catch (FileNotFoundException e) {}
                catch (IOException e) {}

                URLConnection con = new URL(EastAngliaSignalMapServer.ftpBaseUrl + "PPM/index.html;type=i").openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(con.getOutputStream())))
                {
                    out.write(html);
                    out.flush();

                    printRTPPM("Uploaded HTML", false);
                }
                catch (SocketTimeoutException e) { printRTPPM("HTML upload Timeout", true); }
                catch (IOException e) {}
            }
        }
        catch (MalformedURLException e) {}
        catch (IOException e) { e.printStackTrace(); }
    }
}