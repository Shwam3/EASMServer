package eastangliamapserver;

import static eastangliamapserver.Signals.getSignal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Signals
{
    private static Map<String, Signal> signalMap = new HashMap<>();
    private static List<String>  missingSignals = new ArrayList<>();

    public static Signal createOrGetSignal(String signalId, String signalAddress)
    {
        Signal signal = getSignal(signalAddress);

        if (signal != null)
            return signal;
        else
            return new Signal(signalId, signalAddress);
    }

    public static boolean containsSignal(String signalId)
    {
        return signalMap.containsKey(signalId);
    }

    public static void putSignal(String signalId, String signalAddress, Signal signal)
    {
        if (!containsSignal(signalId))
        {
            signalMap.put(signalAddress, signal);
            missingSignals.remove(signalAddress);

            EastAngliaSignalMapServer.updateServerGUI();
        }
    }

    public static Signal getSignal(String signalAddress)
    {
        if (signalMap.containsKey(signalAddress))
            return signalMap.get(signalAddress);

        return null;
    }

    public String getAddress(String signalId)
    {
        for (Map.Entry<String, Signal> pairs : signalMap.entrySet())
            if (pairs.getValue().getId().equals(signalId))
                return pairs.getKey();

        return "";
    }
}