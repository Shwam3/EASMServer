package eastangliamapserver.stomp;

import eastangliamapserver.*;
import eastangliamapserver.stomp.handlers.CClassHandler;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Listener;

public class StompConnectionHandler
{
    private static final String SERVER   = "datafeeds.networkrail.co.uk";
    private static final int    PORT     = 61618;
    private static final String USERNAME = "";
    private static final String PASSWORD = "";
    private static final String APP_ID   = ""; //USERNAME + "-EastAngliaSignalMapServer-v" + SocketServer.BUILD + "-" + System.getProperty("user.name");

    public  static final String TD_TOPIC  = "/topic/TD_ANG_SIG_AREA";
    public  static final String TD_SUB_ID = "cambird3@gmail.com-EastAngliaSignalMapServer-v" + SocketServer.BUILD + "-" + System.getProperty("user.name") + "-TD"; //APP_ID + "-TD";

    public  static StompClient client;
    private static Timer timeoutTimer;
    private static ScheduledFuture<?> beeperHandle = null;
    private static int   timeoutWait = 10;
    private static int   wait = 0;
    public  static long  lastMessageTime = System.currentTimeMillis();

    //private static boolean init = false;

    public static boolean connect() throws LoginException, IOException
    {
        String username = "cambird3@gmail.com";
        String password = "Cameron98!";
        String appId = username + "-EastAngliaSignalMapServer-v" + SocketServer.BUILD + "-" + System.getProperty("user.name");
        String subId = appId + "-TD";

        /*FileInputStream in = null;
        try
        {
            Properties loginProps = new Properties();
            in = new FileInputStream("NROD_Login.properties");
            loginProps.load(in);

            username = loginProps.getProperty("Username");
            password = loginProps.getProperty("Password");

            appId = username + "-EastAngliaSignalMapServer-v" + SocketServer.BUILD + "-" + System.getProperty("user.name");
            subId = appId + "-TD";

            printStomp("Username: " + username, false);
            printStomp("Password: " + password, false);
            printStomp("App Id: " + appId, false);
        }
        catch (FileNotFoundException e)
        {
            printStomp("Unable to find login properties file", true);
        }
        finally
        {
            if (in != null)
                in.close();
        }*/

        startTimeoutTimer();

        client = new StompClient(SERVER, PORT, username, password, appId);

        if (client.isConnected())
        {
            printStomp(String.format("Connected to %s:%s, id: %s, Username: %s, Password: %s,", SERVER, PORT, appId, username, password), false);
        }
        else
        {
            printStomp("Could not connect to server", true);
            return false;
        }

        SignalMap.createBerthObjects();

        Listener TDListener = new Listener()
        {
            @Override
            public void message(final Map headers, final String body)
            {
                headers.put("nice-message-id", headers.get("message-id").toString().substring(38).replace(":", ""));
                HashMap<String, String> updateMap = new CClassHandler(headers, body).parseMessage();

                SocketServer.CClassMap.putAll(updateMap);
                Clients.broadcastUpdate(updateMap);

                lastMessageTime = System.currentTimeMillis();
                SocketServer.gui.updateDataList();
                SocketServer.trimLogs();

                StompConnectionHandler.client.ack(StompConnectionHandler.TD_SUB_ID, headers.get("message-id").toString());
            }
        };

        client.addErrorListener(new Listener()
        {
            @Override
            public void message(Map headers, String body)
            {
                printStomp("Stomp message error\n" + headers.get("message"), true);
            }
        });

        HashMap header = new HashMap();
        header.put("ack", "client-individual");

        header.put("id", subId);
        header.put("activemq.subscriptionName", subId); // actual name unknown
        client.subscribe(TD_TOPIC, TDListener, header);
        printStomp("Subscribed to '" + TD_TOPIC + "' with subscription id '" + subId + "'", false);

        return true;
    }

    public static boolean isConnected()
    {
        if (client == null)
            return false;

        return client.isConnected() && !client.isClosed();
    }

    public static boolean isTimedOut()
    {
        return System.currentTimeMillis() - lastMessageTime >= 20000;
    }

    public static void reconnect()
    {
        if (client != null)
            client.disconnect();

        try
        {
            connect();
            printStomp("Reconnected", false);
        }
        catch (LoginException e) { printStomp("Login Exception. Server already connected to NR Servers", true); }
        catch (IOException e) { printStomp("IO Exception:\n" + e, true); }
        catch (Exception e) { printStomp("" + e, true); }
    }

    private static void startTimeoutTimer()
    {
        if (beeperHandle == null || beeperHandle.isCancelled() || beeperHandle.isDone())
            beeperHandle = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
            {
                @Override
                public void run()
                {
                    if (wait >= timeoutWait)
                        try
                        {
                            wait = 0;

                            long time = System.currentTimeMillis() - lastMessageTime;

                            printStomp(String.format("Timeout: %02d:%02d:%02d", (time / (1000 * 60 * 60)) % 24, (time / (1000 * 60)) % 60, (time / 1000) % 60), isTimedOut() || !isConnected());

                            if (isTimedOut() || !isConnected())
                            {
                                timeoutWait = Math.min(60, timeoutWait + 10);

                                printStomp((isTimedOut() ? "Timed Out" : "") + (!isConnected() && isTimedOut() ? " & " : "") + (!isConnected() ? "Disconnected" : "") + " (" + timeoutWait + "s)", true);

                                reconnect();
                            }
                            else
                            {
                                timeoutWait = 10;
                                printStomp("No problems", false);
                            }
                        }
                        catch (Exception e) { printStomp("Exception in timeout timer:\n" + e, true); }
                        catch (Error e) { printStomp("Error in timeout timer:\n" + e, true); }
                    else
                        wait += 10;
                }
            }, 10, 10, TimeUnit.SECONDS);
    }

    public static void printStomp(String message, boolean toErr)
    {
        if (toErr)
            SocketServer.printErr("[Stomp] " + message);
        else
            SocketServer.printOut("[Stomp] " + message);
    }

    public static void printSClass(String message, boolean toErr)
    {
        if (toErr)
            SocketServer.printErr("[S-Class] " + message);
        else
            SocketServer.printOut("[S-Class] " + message);
    }

    public static void printCClass(String message, boolean toErr)
    {
        if (toErr)
            SocketServer.printErr("[C-Class] " + message);
        else
            SocketServer.printOut("[C-Class] " + message);
    }
}