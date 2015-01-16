package eastangliamapserver.stomp;

import eastangliamapserver.*;
import eastangliamapserver.stomp.handlers.CClassHandler;
import eastangliamapserver.stomp.handlers.RTPPMHandler;
import eastangliamapserver.stomp.handlers.SClassHandler;
import java.io.*;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import javax.security.auth.login.LoginException;
import jsonparser.JSONParser;
import net.ser1.stomp.Listener;
import net.ser1.stomp.Version;

public class StompConnectionHandler
{
    private static StompClient client;

    private static ScheduledFuture<?> timeoutHandler = null;
    private static int    timeoutWait = 10;
    private static int    wait = 0;
    public  static long   lastMessageTime = System.currentTimeMillis();
    private static String appID = "";

    public static boolean connect() throws LoginException, IOException
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

        appID = username + "-EastAngliaSignalMapServer-v" + EastAngliaSignalMapServer.VERSION;

        if ((username != null && username.equals("")) || (password != null && password.equals("")))
        {
            printStomp("Error retreiving login details (usr: " + username + ", pwd: " + password + ")", true);
            return false;
        }

        startTimeoutTimer();
        client = new StompClient("datafeeds.networkrail.co.uk", 61618, username, password, appID);

        if (client.isConnected())
        {
            printStomp("Connected to \"datafeeds.networkrail.co.uk:61618\"", false);
            printStomp("    ID:       " + appID,    false);
            printStomp("    Username: " + username, false);
            printStomp("    Password: " + password, false);
        }
        else
        {
            printStomp("Could not connect to network rails servers", true);
            return false;
        }

        SignalMap.createBerthObjects();

        Listener TDListener = new Listener()
        {
            @Override
            public void message(final Map<String, String> headers, final String body)
            {
                printStomp(String.format("Message received (topic: %s, time: %s, expires: %s, id: %s)", headers.get("destination").substring(7), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("timestamp")))), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("expires")))), headers.get("message-id").replace("\\c", ":").substring(38)), false);

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
                EastAngliaSignalMapServer.gui.updateDataList();

                StompConnectionHandler.client.ack(headers.get("ack")/*, "TD"*/);
            }
        };

        Listener PPMListener = new Listener()
        {
            @Override
            public void message(Map<String, String> headers, String body)
            {
                printStomp(String.format("Message received (topic: %s, time: %s, expires: %s, id: %s)", headers.get("destination").substring(7), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("timestamp")))), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("expires")))), headers.get("message-id").replace("\\c", ":").substring(38)), false);
                RTPPMHandler.parseMessage(body);

                lastMessageTime = System.currentTimeMillis();
                EastAngliaSignalMapServer.gui.updateDataList();

                StompConnectionHandler.client.ack(headers.get("ack")/*, "RTPPM"*/);
            }
        };

        Listener GenericListener = new Listener()
        {
            @Override
            public void message(Map<String, String> headers, String body)
            {
                printStomp(String.format("Message received (topic: %s, time: %s, expires: %s, id: %s)", headers.get("destination").substring(7), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("timestamp")))), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(headers.get("expires")))), headers.get("message-id").substring(38)), false);
                if (!headers.get("destination").substring(7).contains("VSTP"))
                    printStomp(body, false);

                StompConnectionHandler.client.ack(headers.get("ack")/*, "VSTP"*/);
            }
        };

        client.addErrorListener(new Listener()
        {
            @Override
            public void message(Map<String, String> headers, String body)
            {
                printStomp(headers.get("message"), true);

                if (body != null && !body.isEmpty())
                    printStomp(body.replace("\n", "\n[Stomp]"), true);
            }
        });

        client.subscribe("/topic/TD_ANG_SIG_AREA", "TD",    TDListener);
        client.subscribe("/topic/RTPPM_ALL",       "RTPPM", PPMListener);
        client.subscribe("/topic/TSR_ANG_ROUTE",   "TSR",   GenericListener);
        client.subscribe("/topic/VSTP_ALL",        "VSTP",  GenericListener);

        RTPPMHandler.initPPM();

        return true;
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
        catch (Throwable t)            { printStomp("INVESTIGATE FURTHER:", true); EastAngliaSignalMapServer.printThrowable(t, "Stomp"); }

        return false;
    }

    private static void startTimeoutTimer()
    {
        if (timeoutHandler != null)
            timeoutHandler.cancel(false);

        timeoutHandler = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                if (wait >= timeoutWait)
                {
                    wait = 0;

                    long time = System.currentTimeMillis() - lastMessageTime;

                    printStomp(String.format("Timeout: %02d:%02d:%02d", (time / (1000 * 60 * 60)) % 24, (time / (1000 * 60)) % 60, (time / 1000) % 60), isTimedOut() || !isConnected() || isClosed());

                    if (isTimedOut() || !isConnected())
                    {
                        timeoutWait = Math.min(60, timeoutWait + 10);

                        printStomp((isTimedOut() ? "Timed Out" : "") + (isTimedOut() && isClosed() ? ", " : "") + (isClosed() ? "Closed" : "") + ((isTimedOut() || isClosed()) && !isConnected() ? " & " : "") + (!isConnected() ? "Disconnected" : "") + " (" + timeoutWait + "s)", true);

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
                        printStomp("No problems", false);
                    }
                }
                else
                    wait += 10;

                System.gc();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

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
}