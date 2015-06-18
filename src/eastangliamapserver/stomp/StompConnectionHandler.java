package eastangliamapserver.stomp;

import eastangliamapserver.Berth;
import eastangliamapserver.Berths;
import eastangliamapserver.EastAngliaSignalMapServer;
import eastangliamapserver.SignalMap;
import eastangliamapserver.Train;
import eastangliamapserver.server.Clients;
import eastangliamapserver.stomp.handlers.CClassHandler;
import eastangliamapserver.stomp.handlers.SClassHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.security.auth.login.LoginException;
import jsonparser.JSONParser;
import net.ser1.stomp.Listener;
import net.ser1.stomp.Version;

public class StompConnectionHandler
{
    private static StompClient client;

    private static ScheduledFuture<?> timeoutHandler = null;
    private static int    maxTimeoutWait = 300;
    private static int    timeoutWait = 10;
    private static int    wait = 0;
    public  static long   lastMessageTime = System.currentTimeMillis();
    private static String appID = "";
    private static int    stompConnectionId = System.getProperty("args", "").contains("-id:") ? Integer.parseInt(System.getProperty("args", "-id:1").substring(System.getProperty("args").indexOf("-id:")+4, System.getProperty("args").indexOf("-id:")+5)) : 1;

    public static boolean connect() throws LoginException, IOException
    {
        if (!EastAngliaSignalMapServer.stompOffline)
        {
            printStomp(Version.VERSION, false);

            String username;
            String password;

            File loginFile = new File(EastAngliaSignalMapServer.storageDir, "NROD_Login.properties");
            try (FileInputStream in = new FileInputStream(loginFile))
            {
                Properties loginProps = new Properties();
                loginProps.load(in);

                username = loginProps.getProperty("Username", "");
                password = loginProps.getProperty("Password", "");
            }
            catch (FileNotFoundException e)
            {
                printStomp("Unable to find login properties file (" + loginFile + ")", true);
                return false;
            }

            appID = username + "-EastAngliaSignalMapServer-v" + EastAngliaSignalMapServer.VERSION + "-";

            if ((username != null && username.equals("")) || (password != null && password.equals("")))
            {
                printStomp("Error retreiving login details (usr: " + username + ", pwd: " + password + ")", true);
                return false;
            }

            startTimeoutTimer();
            client = new StompClient("datafeeds.networkrail.co.uk", 61618, username, password, appID + stompConnectionId);

            if (client.isConnected())
            {
                printStomp("Connected to \"datafeeds.networkrail.co.uk:61618\"", false);
                printStomp("    ID:       " + appID + stompConnectionId, false);
                printStomp("    Username: " + username, false);
                printStomp("    Password: " + password, false);
            }
            else
            {
                printStomp("Could not connect to network rails servers", true);
                return false;
            }

            SignalMap.createBerthObjects();

            Listener TDListener = (final Map<String, String> headers, final String body) ->
            {
                printStomp(
                        String.format("Message received (topic: %s, time: %s, expires: %s, id: %s, ack: %s, subscription: %s, persistent: %s%s)",
                                String.valueOf(headers.get("destination")).replace("\\c", ":"),
                                EastAngliaSignalMapServer.sdfTime.format(new Date(Long.parseLong(headers.get("timestamp")))),
                                EastAngliaSignalMapServer.sdfTime.format(new Date(Long.parseLong(headers.get("expires")))),
                                String.valueOf(headers.get("message-id")).replace("\\c", ":"),
                                String.valueOf(headers.get("ack")).replace("\\c", ":"),
                                String.valueOf(headers.get("subscription")).replace("\\c", ":"),
                                String.valueOf(headers.get("persistent")).replace("\\c", ":"),
                                headers.size() > 7 ? ", + " + (headers.size()-7) + " more" : ""
                        ), false);

                List<Map<String, Map<String, String>>> messageList = (List<Map<String, Map<String, String>>>) JSONParser.parseJSON("{\"TDMessage\":" + body + "}").get("TDMessage");

                Map<String, String> CClass = CClassHandler.parseMessage(messageList);
                Map<String, String> SClass = SClassHandler.parseMessage(messageList);

                Map<String, String> updateMap = new HashMap<>(CClass.size() + SClass.size());
                updateMap.putAll(CClass);
                updateMap.putAll(SClass);

                if (!CClass.isEmpty())
                    EastAngliaSignalMapServer.CClassMap.putAll(CClass);
                else if (!body.contains("\"CT\""))
                    printCClass("No messages", false);

                int heartbeats = (body.length() - body.replace("CT", "").length()) / 2;
                if (heartbeats > 0)
                    printCClass(heartbeats + " heartbeat(s)", false);

                if (SClass.isEmpty())
                    printSClass("No messages", false);

                if (!updateMap.isEmpty())
                    Clients.broadcastUpdate(updateMap);

                lastMessageTime = System.currentTimeMillis();

                EastAngliaSignalMapServer.updateServerGUIs();
                StompConnectionHandler.client.ack(headers.get("ack"));
            };

            client.addErrorListener((Map<String, String> headers, String body) ->
            {
                /*if (headers.get("message").contains(" already connected from "))
                incrementConnectionId();*/

                if (headers != null && headers.containsKey("message"))
                    printStomp(headers.get("message").trim(), true);

                if (body != null && !body.isEmpty())
                    printStomp(body.trim().replace("\n", "\n[Stomp]"), true);
            });

            client.subscribe("/topic/TD_ANG_SIG_AREA", "TD", TDListener);

            return true;
        }
        else
        {
            printStomp("Not connecting stomp, in offline mode (offline: " + EastAngliaSignalMapServer.stompOffline + ")", false);
            return false;
        }
    }

    public static void disconnect()
    {
        if (client != null && isConnected() && !isClosed())
            client.disconnect();
    }

    public static boolean isConnected()
    {
        if (client == null)
            return false;

        return client.isConnected();
    }

    public static boolean isClosed()
    {
        if (client == null)
            return false;

        return client.isClosed();
    }

    public static boolean isTimedOut()
    {
        return System.currentTimeMillis() - lastMessageTime >= 20000;
    }

    public static boolean wrappedConnect()
    {
        try
        {
            return connect();
        }
        catch (LoginException e)       { printStomp("Login Exception: " + e.getLocalizedMessage().split("\n")[0], true); }
        catch (UnknownHostException e) { printStomp("Unable to resolve host (datafeeds.networkrail.co.uk)", true); }
        catch (IOException e)          { printStomp("IO Exception:", true); EastAngliaSignalMapServer.printThrowable(e, "Stomp"); }
        catch (Exception e)            { printStomp("Exception:", true); EastAngliaSignalMapServer.printThrowable(e, "Stomp"); }

        return false;
    }

    private static void startTimeoutTimer()
    {
        if (timeoutHandler != null)
            timeoutHandler.cancel(false);

        timeoutHandler = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(() ->
        {
            if (wait >= timeoutWait && !EastAngliaSignalMapServer.stompOffline)
            {
                wait = 0;

                if (isTimedOut() || !isConnected())
                {
                    long time = System.currentTimeMillis() - lastMessageTime;
                    printStomp(String.format("Timeout: %02d:%02d:%02d", (time / (1000 * 60 * 60)) % 24, (time / (1000 * 60)) % 60, (time / 1000) % 60), isTimedOut() || !isConnected() || isClosed());

                    timeoutWait = Math.min(maxTimeoutWait, timeoutWait + 10);

                    printStomp((isTimedOut() ? "Timed Out" : "") + (isTimedOut() && isClosed() ? ", " : "") + (isClosed() ? "Closed" : "") + ((isTimedOut() || isClosed()) && !isConnected() ? " & " : "") + (!isConnected() ? "Disconnected" : "") + " (" + timeoutWait + "s)", true);

                    Berth berth = Berths.createOrGetBerth("XXMOTD");
                    if (berth != null)
                    {
                        String errorMessage = "Disconnected from Network Rail's servers";
                        String motd = berth.getHeadcode();

                        if (motd.contains("No problems"))
                            motd = motd.replace("No problems", errorMessage);

                        motd += errorMessage;

                        berth.interpose(new Train(motd, berth));

                        Map<String, String> motdMap = new HashMap<>();
                        motdMap.put("XXMOTD", motd);

                        Clients.broadcastUpdate(motdMap);
                        EastAngliaSignalMapServer.CClassMap.putAll(motdMap);

                        printStomp("MOTD Status: \"" + motd + "\"", false);
                        EastAngliaSignalMapServer.updateServerGUIs();
                    }

                    try
                    {
                        if (client != null)
                            client.disconnect();

                        connect();
                    }
                    catch (LoginException e) { printStomp("Login Exception: " + e.getLocalizedMessage().split("\n")[0], true);}
                    catch (IOException e)    { printStomp("IOException reconnecting", true); EastAngliaSignalMapServer.printThrowable(e, "Stomp"); }
                    catch (Exception e)      { printStomp("Exception reconnecting:", true);  EastAngliaSignalMapServer.printThrowable(e, "Stomp"); }
                    catch (Error e)          { printStomp("Error reconnecting:", true);      EastAngliaSignalMapServer.printThrowable(e, "Stomp"); }
                }
                else
                {
                    timeoutWait = 10;

                    Berth berth = Berths.createOrGetBerth("XXMOTD");
                    if (berth != null)
                    {
                        String motd = berth.getHeadcode();

                        if (motd.contains("Disconnected from Network Rail's servers"))
                        {
                            motd = motd.replace("Disconnected from Network Rail's servers", "No problems");

                            berth.interpose(new Train(motd, berth));

                            Map<String, String> motdMap = new HashMap<>();
                            motdMap.put("XXMOTD", motd);

                            Clients.broadcastUpdate(motdMap);
                            EastAngliaSignalMapServer.CClassMap.putAll(motdMap);

                            printStomp("MOTD Status: \"" + motd + "\"", false);
                            EastAngliaSignalMapServer.updateServerGUIs();
                        }
                    }
                }
            }
            else
                wait += 10;

            System.gc();
        }, 10, 10, TimeUnit.SECONDS);
    }

    public static void setMaxTimeoutWait(int maxTimeoutWait)
    {
        StompConnectionHandler.maxTimeoutWait = Math.max(60, maxTimeoutWait);
    }

    //<editor-fold defaultstate="collapsed" desc="Print methods">
    public static void printStomp(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Stomp] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Stomp] " + message);
    }
    public static void printSClass(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[S-Class] " + message);
        else
            EastAngliaSignalMapServer.printOut("[S-Class] " + message);
    }
    public static void printCClass(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[C-Class] " + message);
        else
            EastAngliaSignalMapServer.printOut("[C-Class] " + message);
    }
    public static void printRTPPM(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[RTPPM] " + message);
        else
            EastAngliaSignalMapServer.printOut("[RTPPM] " + message);
    }
    public static void printMovement(String message, boolean toErr)
    {
        if (toErr)
            EastAngliaSignalMapServer.printErr("[Movement] " + message);
        else
            EastAngliaSignalMapServer.printOut("[Movement] " + message);
    }
    //</editor-fold>

    public static void incrementConnectionId()
    {
        stompConnectionId++;
        printStomp("Incrementing connection Id (" + stompConnectionId + ")", false);
    }
}