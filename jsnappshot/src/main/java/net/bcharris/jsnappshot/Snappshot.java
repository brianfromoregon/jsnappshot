package net.bcharris.jsnappshot;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A container for an object's serialized form, the bytecode for all classes it will require for deserialization, and
 * the data for all resources it will require for deserialization.
 */
public class Snappshot implements Serializable {

    private static final long serialVersionUID = 0L;
    public final byte[] object;
    public final Map<String, byte[]> requiredClasses;
    public final Map<String, byte[]> requiredResources;

    /**
     * @param object The object's serialized form
     * @param requiredClasses Mapping class names (@see Class.getName()) to the class bytecode.
     * @param requiredResources Mapping resource paths to the resource bytes.
     */
    public Snappshot(byte[] object, Map<String, byte[]> requiredClasses, Map<String, byte[]> requiredResources) {
        this.object = object;
        this.requiredClasses = requiredClasses;
        this.requiredResources = requiredResources;
    }

    private Object writeReplace() throws ObjectStreamException {
        return new SerializationProxy(this);
    }

    private static class SerializationProxy implements Serializable {

        private final byte[] object;
        private final Object[][] requiredClasses;
        private final Object[][] requiredResources;

        public SerializationProxy(Snappshot s) {
            this.object = s.object;
            this.requiredClasses = mapToArray(s.requiredClasses);
            this.requiredResources = mapToArray(s.requiredResources);
        }

        private Object readResolve() throws ObjectStreamException {
            return new Snappshot(object, arrayToMap(requiredClasses), arrayToMap(requiredResources));
        }

        private static Object[][] mapToArray(Map<String, byte[]> map) {
            Object[][] array = new Object[map.size()][2];
            int i = 0;
            for (Entry entry : map.entrySet()) {
                array[i][0] = entry.getKey();
                array[i][1] = entry.getValue();
                i++;
            }
            return array;
        }

        private static HashMap<String, byte[]> arrayToMap(Object[][] array) {
            HashMap map = new HashMap();
            for (int i = 0; i < array.length; i++) {
                Object[] entry = array[i];
                map.put(entry[0], entry[1]);
            }
            return map;
        }
    }
}
