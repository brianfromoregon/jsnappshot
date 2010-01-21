package net.bcharris.jsnappshot;

import java.io.Serializable;
import java.util.HashMap;

/**
 * A container for an object's serialized form, the bytecode for all classes it will require for deserialization, and
 * the data for all resources it will require for deserialization.
 */
public class Snappshot implements Serializable {

    private static final long serialVersionUID = 0L;
    public final byte[] object;
    public final HashMap<String, byte[]> requiredClasses;
    public final HashMap<String, byte[]> requiredResources;

    /**
     * @param object The object's serialized form
     * @param requiredClasses Mapping class names (@see Class.getName()) to the class bytecode.
     * @param requiredResources Mapping resource paths to the resource bytes.
     */
    public Snappshot(byte[] object, HashMap<String, byte[]> requiredClasses, HashMap<String, byte[]> requiredResources) {
        this.object = object;
        this.requiredClasses = requiredClasses;
        this.requiredResources = requiredResources;
    }
}
