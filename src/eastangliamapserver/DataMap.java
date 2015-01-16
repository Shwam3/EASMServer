package eastangliamapserver;

import java.util.HashMap;
import java.util.Map;

public class DataMap
{
    private final Map<String, Map<Integer, DataBit>> map = new HashMap<>();

    public DataMap(String... areas)
    {
        for (String area : areas)
            map.put(area.substring(0, 2), genEmptyMap());
    }

    private Map<Integer, DataBit> genEmptyMap()
    {
        Map<Integer, DataBit> area = new HashMap<>();
        for (int i = 0; i < 0xFF; i++)
            area.put(i, new DataBit());

        return area;
    }

    public void setData(String address, int data)
    {
        String area = address.substring(0, 2);
        int addr = Integer.parseInt(address.substring(2, 4), 16);
        map.get(area).get(addr).setData(data);
    }

    public void setData(String address, int bit, int data)
    {
        String area = address.substring(0, 2);
        int addr = Integer.parseInt(address.substring(2, 4), 16);
        map.get(area).get(addr).setData(bit, data);
    }

    public int getData(String address)
    {
        String area = address.substring(0, 2);
        int addr = Integer.parseInt(address.substring(2, 4), 16);
        return map.get(area).get(addr).getData();
    }

    public int getData(String address, int bit)
    {
        String area = address.substring(0, 2);
        int addr = Integer.parseInt(address.substring(2, 4), 16);
        return map.get(area).get(addr).getData(bit);
    }

    public class DataBit
    {
        private int data = 0;

        public DataBit() {}
        public DataBit(int data) { this.data = data; }

        public void setData(int data)
        {
            if (data < 0 || data > 0xFF)
                throw new IllegalArgumentException("data cannot be less than 0 or greater than 255");

            this.data = data;
        }

        public int getData()
        {
            return data;
        }

        // bit 8 - 1
        public void setData(int bit, int data) // throws NumberFormatException
        {
            if (data < 0 || data > 1)
                throw new IllegalArgumentException("data cannot be less than 0 or greater than 1");

            if (bit < 0 || bit > 8)
                throw new IllegalArgumentException("bit cannot be less than 0 or greater than 8");

            char[] dataStr = toBinaryString(data).toCharArray();
            dataStr[bit - 1] = Integer.toString(data).charAt(0);

            this.data = Integer.parseInt(String.valueOf(dataStr));
        }

        public int getData(int bit) // throws NumberFormatException
        {
            return Integer.parseInt(String.valueOf(toBinaryString(data).charAt(bit - 1)));
        }
    }

    private String toBinaryString(int integer)
    {
        String bite = Integer.toBinaryString(integer);
        return "00000000".substring(bite.length()) + bite;
    }
}