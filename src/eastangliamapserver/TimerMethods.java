package eastangliamapserver;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.io.*;
import java.net.ConnectException;
import java.net.URL;
import java.util.*;

public class TimerMethods
{
    private static boolean allowSleep = false;
    private static Timer sleepTimer;
    private static Timer statusTimer;
    private static Timer IPUpdater;

    public static boolean sleep(boolean sleep)
    {
        createTimerEvents();

        allowSleep = sleep;
        return allowSleep;
    }

    public static boolean sleep()
    {
        createTimerEvents();

        allowSleep = !allowSleep;
        return allowSleep;
    }

    private static void createTimerEvents()
    {
        if (sleepTimer == null || sleepTimer.purge() != 0)
        {
            sleepTimer = new Timer("sleepTimer", true);
            sleepTimer.scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    if (!allowSleep)
                        try
                        {
                            Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
                            Robot rob = new Robot();
                            rob.mouseMove(mouseLoc.x, mouseLoc.y);
                        }
                        catch (Exception e) { EastAngliaSignalMapServer.printErr("[Timer] Exception:\n" + e); }
                }
            }, 30000, 30000);
        }

        if (statusTimer == null || statusTimer.purge() != 0)
        {
            statusTimer = new Timer("statusTimer", true);
            statusTimer.scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    try
                    {
                        String status = "";
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://easignalmap.altervista.org/status/status.txt").openStream())))
                        {
                            status = br.readLine();
                        }
                        catch (ConnectException e) { EastAngliaSignalMapServer.printErr("[MOTD] Unable to connect to altervista server"); }
                        catch (IOException e)
                        {
                            EastAngliaSignalMapServer.printThrowable(e, "MOTD");
                        }

                        Berth berth = Berths.getBerth("XXMOTD");
                        if (berth != null && !berth.getHeadcode().equals(status.trim()))
                        {
                            status = status.trim();

                            berth.interpose(new Train(status, berth));

                            Map<String, String> motdMap = new HashMap<>();
                            motdMap.put("XXMOTD", status.trim());
                            Clients.broadcastUpdate(motdMap);

                            File motdFile = new File(EastAngliaSignalMapServer.storageDir, "MOTD.txt");
                            if (!motdFile.exists())
                            {
                                motdFile.getParentFile().mkdirs();
                            }

                            try (BufferedWriter out = new BufferedWriter(new FileWriter(motdFile)))
                            {
                                out.write(status);
                            }
                            catch (ConnectException e) { EastAngliaSignalMapServer.printErr("[MOTD] Unable to connect to FTP server"); }
                            catch (IOException e)
                            {
                                EastAngliaSignalMapServer.printErr("[MOTD] IOException writing to \"" + motdFile.getAbsolutePath() + "\"");
                                EastAngliaSignalMapServer.printThrowable(e, "MOTD");
                            }
                        }
                    }
                    catch (Exception e) {}
                }
            }, 30000, 30000);
        }

        if (IPUpdater == null || IPUpdater.purge() != 0)
        {
            IPUpdater = new Timer("IPUpdater&MapAutosave", true);
            IPUpdater.scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    try { EastAngliaSignalMapServer.updateIP(); }
                    catch (Throwable t) { EastAngliaSignalMapServer.printThrowable(t, "IPUpdater"); }

                    try { EastAngliaSignalMapServer.saveMap(); }
                    catch (Throwable t) { EastAngliaSignalMapServer.printThrowable(t, "MapAutosave"); }

                    /*int len = EastAngliaSignalMapServer.SClassLog.size() - 75000;
                    for (int i = 0; i < len; i++)
                        EastAngliaSignalMapServer.SClassLog.remove(0);*/
                }
            }, 300000, 300000);
        }
    }
}