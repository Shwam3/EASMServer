package eastangliamapserver;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.Timer;
import java.util.TimerTask;

public class TimerMethods
{
    private static boolean allowSleep = false;
    private static Timer sleepTimer;

    public static boolean sleep(boolean sleep)
    {
        createTimerEvent();

        allowSleep = sleep;
        return allowSleep;
    }

    public static boolean sleep()
    {
        createTimerEvent();

        allowSleep = !allowSleep;
        return allowSleep;
    }

    private static void createTimerEvent()
    {
        if (sleepTimer == null)
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
                        catch (Exception e) { SocketServer.printErr("[Timer] Exception:\n" + e); }
                }
            }, 30000, 30000);
        }
    }
}