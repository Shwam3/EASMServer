package eastangliamapserver.stomp;

import eastangliamapserver.*;
import eastangliamapserver.stomp.handlers.CClassHandler;
import java.io.*;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Listener;

public class StompConnectionHandler
{
    private static StompClient client;

    private static ScheduledFuture<?> timeoutHandler = null;
    private static int   timeoutWait = 10;
    private static int   wait = 0;
    public  static long  lastMessageTime = System.currentTimeMillis();

    public static boolean connect() throws LoginException, IOException
    {
        FileInputStream in = null;
        String username = "";
        String password = "";
        String appID = "";
        String subID = "";
        try
        {
            Properties loginProps = new Properties();
            in = new FileInputStream("NROD_Login.properties");
            loginProps.load(in);

            username = loginProps.getProperty("Username");
            password = loginProps.getProperty("Password");

            appID = username + "-EastAngliaSignalMapServer-v" + EastAngliaSignalMapServer.VERSION + "-" + System.getProperty("user.name");
            subID = appID + "-TD";
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

        if ((username != null && username.equals("")) || (password != null && password.equals("")))
        {
            printStomp("Error retreiving login details (usr: " + username + ", pwd: " + password + ")", true);
            return false;
        }

        client = new StompClient("datafeeds.networkrail.co.uk", 61618, username, password, appID);
        startTimeoutTimer();

        if (client.isConnected())
        {
            printStomp("Connected to \"datafeeds.networkrail.co.uk:" + 61618 + "\"", false);
            printStomp("    ID:       " + appID,    false);
            printStomp("    Username: " + username, false);
            printStomp("    Password: " + password, false);
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
                printCClass(String.format("Message \"%s\" (%s)", String.valueOf(headers.get("message-id")).substring(38), EastAngliaSignalMapServer.sdf.format(new Date(Long.parseLong(String.valueOf(headers.get("timestamp")))))), false);
                headers.put("nice-message-id", headers.get("message-id").toString().substring(38).replace(":", ""));
                HashMap<String, String> updateMap = CClassHandler.parseMessage(body);

                if (!updateMap.isEmpty())
                {
                    EastAngliaSignalMapServer.CClassMap.putAll(updateMap);
                    Clients.broadcastUpdate(updateMap);
                }
                else
                    printCClass("Empty map", false);

                lastMessageTime = System.currentTimeMillis();
                EastAngliaSignalMapServer.gui.updateDataList();

                StompConnectionHandler.client.ack(headers.get("message-id").toString());
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
        header.put("id", subID);
        header.put("activemq.subscriptionName", subID); // actual name unknown

        client.subscribe("/topic/TD_ANG_SIG_AREA", TDListener, subID, header);

        printStomp("Subscribed to \"/topic/TD_ANG_SIG_AREA\"", false);
        printStomp("Subscription id \"" + subID + "\"", false);

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
        catch (LoginException e)
        {
            printStomp("Login Exception. (Another) Server already is connected to NR Servers", true);
            printStomp(e.toString(), true);
            /*try { Thread.sleep(50); }
            catch (InterruptedException ex) { return false; }

            wrappedConnect();*/
        }
        catch (UnknownHostException e) { printStomp("Unable to resolve host (datafeeds.networkrail.co.uk)", true); }
        catch (IOException e) { printStomp("IO Exception:\n" + String.valueOf(e), true); }
        catch (Exception e) { printStomp("Exception:\n" + String.valueOf(e), true); }
        catch (Throwable t) { printStomp("INVESTIGATE FURTHER:\n" + String.valueOf(t), true); }
        finally
        {
            return false;
        }
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
                        catch (LoginException e) { printStomp("Login Exception", true); e.printStackTrace(); }
                        //catch (IOException ex) { printStomp("IOException reconnecting:\n" + String.valueOf(ex), true); }
                        catch (Exception ex) { printStomp("Exception reconnecting:\n" + String.valueOf(ex), true); }
                        catch (Error er)     { printStomp("Error reconnecting:\n" + String.valueOf(er), true); }
                        finally { return; }
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
}