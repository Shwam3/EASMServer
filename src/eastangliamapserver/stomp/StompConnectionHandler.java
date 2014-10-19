package eastangliamapserver.stomp;

import eastangliamapserver.*;
import eastangliamapserver.stomp.handlers.CClassHandler;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Listener;

public class StompConnectionHandler
{
    private static final String SERVER    = "datafeeds.networkrail.co.uk";
    private static final int    PORT      = 61618;
    private static       String USERNAME  = "";
    private static       String PASSWORD  = "";
    private static       String APP_ID    = "";
    public  static       String TD_SUB_ID = "";
    public  static final String TD_TOPIC  = "/topic/TD_ANG_SIG_AREA";

    public  static StompClient client;

    private static ScheduledFuture<?> timeoutHandler = null;
    private static int   timeoutWait = 10;
    private static int   wait = 0;
    public  static long  lastMessageTime = System.currentTimeMillis();

    public static boolean connect() throws LoginException, IOException
    {
        FileInputStream in = null;
        try
        {
            Properties loginProps = new Properties();
            in = new FileInputStream("NROD_Login.properties");
            loginProps.load(in);

            USERNAME = loginProps.getProperty("Username");
            PASSWORD = loginProps.getProperty("Password");

            APP_ID = USERNAME + "-EastAngliaSignalMapServer-v" + EastAngliaSignalMapServer.BUILD + "-" + System.getProperty("user.name");
            TD_SUB_ID = APP_ID + "-TD";
        }
        catch (FileNotFoundException e)
        {
            printStomp("Unable to find login properties file: (" + System.getProperty("user.dir") + "\\NROD_Login.properties)\n" + e, true);
            return false;
        }
        finally
        {
            if (in != null)
                in.close();
        }

        if ((USERNAME != null && USERNAME.equals("")) || (PASSWORD != null && PASSWORD.equals("")))
        {
            printStomp("Error retreiving login details (u: " + USERNAME + ", p: " + PASSWORD + ")", true);
            return false;
        }

        startTimeoutTimer();

        client = new StompClient(SERVER, PORT, USERNAME, PASSWORD, APP_ID);

        if (client.isConnected())
        {
            printStomp("Connected to \"" + SERVER + ":" + PORT + "\"", false);
            printStomp("    ID:       " + APP_ID,   false);
            printStomp("    Username: " + USERNAME, false);
            printStomp("    Password: " + PASSWORD, false);
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

                EastAngliaSignalMapServer.CClassMap.putAll(updateMap);
                Clients.broadcastUpdate(updateMap);

                lastMessageTime = System.currentTimeMillis();
                EastAngliaSignalMapServer.gui.updateDataList();

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

        header.put("id", TD_SUB_ID);
        header.put("activemq.subscriptionName", TD_SUB_ID); // actual name unknown
        client.subscribe(TD_TOPIC, TDListener, header);

        printStomp("Subscribed to \"" + TD_TOPIC + "\"", false);
        printStomp("Subscription id \"" + TD_SUB_ID + "\"", false);

        return true;
    }

    public static boolean isConnected()
    {
        if (client == null)
            return false;

        return client.isConnected() && !client.isClosed();
    }

    /*public static boolean isClosed()
    {
        if (client == null)
            return false;

        return client.isClosed();
    }*/

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
        catch (Exception e) { printStomp("Exception:\n" + e, true); }
        catch (Throwable t) { printStomp("INVESTIGATE FURTHER:\n" + t, true); }
    }

    private static void startTimeoutTimer()
    {
        if (timeoutHandler != null)
            timeoutHandler.cancel(false);

        timeoutHandler = Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                if (wait >= timeoutWait)
                {
                    wait = 0;

                    long time = System.currentTimeMillis() - lastMessageTime;

                    printStomp(String.format("Timeout: %02d:%02d:%02d", (time / (1000 * 60 * 60)) % 24, (time / (1000 * 60)) % 60, (time / 1000) % 60), isTimedOut() || !isConnected());

                    if (isTimedOut() || !isConnected())
                    {
                        timeoutWait = Math.min(120, timeoutWait + 10);

                        printStomp((isTimedOut() ? "Timed Out" : "") + (!isConnected() && isTimedOut() ? " & " : "") + (!isConnected() ? "Disconnected" : "") + " (" + timeoutWait + "s)", true);

                        try
                        {
                            reconnect();
                        }
                        catch (Exception ex) { printStomp("Exception reconnecting:\n" + ex, true); }
                        catch (Error er)     { printStomp("Error reconnecting:\n" + er, true); }
                    }
                    else
                    {
                        timeoutWait = 10;
                        printStomp("No problems", false);
                    }
                }
                else
                    wait += 10;
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
}