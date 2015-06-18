package eastangliamapserver;

import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Robot;
import java.util.Timer;
import java.util.TimerTask;

public class TimerMethods
{
    private static boolean allowSleep = false;
    private static Timer sleepTimer;
    //private static Timer statusTimer;
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
                            PointerInfo mouseInfo = MouseInfo.getPointerInfo();
                            if (mouseInfo != null)
                                new Robot(mouseInfo.getDevice()).mouseMove(mouseInfo.getLocation().x, mouseInfo.getLocation().y);
                        }
                        catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "Sleep-Timer"); }
                }
            }, 30000, 30000);
        }

        //if (statusTimer == null || statusTimer.purge() != 0)
        //{
        //    statusTimer = new Timer("statusTimer", true);
        //    statusTimer.scheduleAtFixedRate(new TimerTask()
        //    {
        //        @Override
        //        public void run()
        //        {
        //            try
        //            {
        //                String status = null;
        //                try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://easignalmap.altervista.org/status/status.txt").openStream())))
        //                {
        //                    status = br.readLine();
        //                }
        //                catch (ConnectException e) { EastAngliaSignalMapServer.printErr("[MOTD] Unable to connect to altervista server"); }
        //                catch (IOException e) { EastAngliaSignalMapServer.printThrowable(e, "MOTD"); }
        //
        //                if (status != null && !status.trim().isEmpty())
        //                {
        //                    Berth motdBerth = Berths.getBerth("XXMOTD");
        //                    if (motdBerth != null && !motdBerth.getHeadcode().equals(status.trim()))
        //                    {
        //                        String errorMessage = "Disconnected from Network Rail's servers";
        //
        //                        if (status.contains("No problems"))
        //                            status = status.replace("No problems", errorMessage);
        //
        //                        status = status.trim();
        //
        //                        motdBerth.interpose(new Train(status, motdBerth));
        //
        //                        Map<String, String> motdMap = new HashMap<>();
        //                        motdMap.put("XXMOTD", status.trim());
        //                        Clients.broadcastUpdate(motdMap);
        //
        //                        File motdFile = new File(EastAngliaSignalMapServer.storageDir, "MOTD.txt");
        //                        if (!motdFile.exists())
        //                            motdFile.getParentFile().mkdirs();
        //
        //                        try (BufferedWriter out = new BufferedWriter(new FileWriter(motdFile)))
        //                        {
        //                            out.write(status);
        //                        }
        //                        catch (ConnectException e) { EastAngliaSignalMapServer.printErr("[MOTD] Unable to connect to FTP server"); }
        //                        catch (IOException e)
        //                        {
        //                            EastAngliaSignalMapServer.printErr("[MOTD] IOException writing to \"" + motdFile.getAbsolutePath() + "\"");
        //                            EastAngliaSignalMapServer.printThrowable(e, "MOTD");
        //                        }
        //                    }
        //                }
        //            }
        //            catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "MOTD-Timer"); }
        //        }
        //    }, 30000, 30000);
        //}

        if (IPUpdater == null || IPUpdater.purge() != 0)
        {
            IPUpdater = new Timer("IPUpdater/MapAutosave", true);
            IPUpdater.scheduleAtFixedRate(new TimerTask()
            {
                @Override
                public void run()
                {
                    if (!EastAngliaSignalMapServer.serverOffline)
                        try { EastAngliaSignalMapServer.updateIP(); }
                        catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "IPUpdater"); }

                    if (!EastAngliaSignalMapServer.stompOffline)
                        try { EastAngliaSignalMapServer.saveMap(); }
                        catch (Exception e) { EastAngliaSignalMapServer.printThrowable(e, "MapAutosave"); }
                }
            }, 300000, 300000);
        }
    }
}