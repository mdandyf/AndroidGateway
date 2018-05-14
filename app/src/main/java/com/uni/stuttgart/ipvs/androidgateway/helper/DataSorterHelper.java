package com.uni.stuttgart.ipvs.androidgateway.helper;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DataSorterHelper<T> {

    private T t;

    public DataSorterHelper() { }

    public Map<T, Integer> sortMapByComparator(Map<T, Integer> unsortMap, final boolean order)
    {

        List<Map.Entry<T, Integer>> list = new LinkedList<Map.Entry<T, Integer>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<T, Integer>>()
        {
            public int compare(Map.Entry<T, Integer> o1,
                               Map.Entry<T, Integer> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());
                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<T, Integer> sortedMap = new LinkedHashMap<T, Integer>();
        for (Map.Entry<T, Integer> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    public Map<T, Long> sortMapByComparatorLong(Map<T, Long> unsortMap, final boolean order)
    {

        List<Map.Entry<T, Long>> list = new LinkedList<Map.Entry<T, Long>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<T, Long>>()
        {
            public int compare(Map.Entry<T, Long> o1,
                               Map.Entry<T, Long> o2)
            {
                if (order)
                {
                    return o1.getValue().compareTo(o2.getValue());
                }
                else
                {
                    return o2.getValue().compareTo(o1.getValue());
                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<T, Long> sortedMap = new LinkedHashMap<T, Long>();
        for (Map.Entry<T, Long> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

}
