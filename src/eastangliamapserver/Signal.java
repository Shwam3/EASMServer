package eastangliamapserver;

public class Signal
{
    private final String SIGNAL_ID;
    private final String SIGNAL_ADDRESS;

    private int state = -1; // 0 = red, 1 = green, -1 = unknown

    public Signal(String signalId, String signalAddress)
    {
        SIGNAL_ID = signalId;
        SIGNAL_ADDRESS = signalAddress;

        Signals.putSignal(signalId, signalAddress, this);
    }

    public void setState(int state)
    {
        this.state = state;
    }

    public int getState()
    {
        return state;
    }

    public String getAddress()
    {
        return SIGNAL_ADDRESS;
    }

    public String getId()
    {
        return SIGNAL_ID;
    }
}