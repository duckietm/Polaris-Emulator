package gnu.trove.map.hash;

import java.util.HashMap;
import java.util.Map;

public class THashMap<K, V> extends HashMap<K, V> {

    public THashMap() {
        super();
    }

    public THashMap(int initialCapacity) {
        super(initialCapacity);
    }

    public THashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public THashMap(Map<? extends K, ? extends V> map) {
        super(map);
    }
}
