import java.util.*;

public class Test {

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
        Collections.sort( list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static void main(String[] args) {
        Map<String, Integer[]> test = new HashMap<>();
        test.put("A", new Integer[] {3,3});
        test.put("B", new Integer[] {2,2});
        test.put("C", new Integer[] {1,9});

        /*
        test.put("A", new long[] {1, 4, 3});
        test.put("B", new long[] {2, 4, 2});
        test.put("C", new long[] {2, 4, 9});
        */

        test = sortByValue(test);
        for (String key: test.keySet()) {
            System.out.println(key);
        }

    }
}
