package net.bcharris.jsnappshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Reads and writes Snappshots of objects.
 */
class JSnappshotOld {

    /**
     * Produce a Snappshot from the supplied object.  Class bytecodes will be looked up via
     * ClassLoader.getResourceAsStream().
     * @param o The object from which to create a Snappshot.
     * @return The created snappshot
     * @throws NotSerializableException Some object to be serialized does not implement the java.io.Serializable interface.
     */
    public static Snappshot writeSnappshot(final Object o) throws NotSerializableException {
        return writeSnappshot(o, new BytecodeAccessor() {

            @Override
            public byte[] bytecodeForClass(String className) {
                // This 'realClassLoader' business might turn out to be useful?
//                ClassLoader realClassLoader;
//                try {
//                    Class realClass = o.getClass().getClassLoader().loadClass(className);
//                    realClassLoader = realClass.getClassLoader();
//                } catch (ClassNotFoundException ex) {
//                    realClassLoader = o.getClass().getClassLoader();
//                }
                InputStream is = o.getClass().getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class");
                if (is == null) {
                    return null;
                }

                try {
                    return ByteStreams.toByteArray(is);
                } catch (IOException ex) {
                    throw new RuntimeException("When reading bytecode for class: " + className, ex);
                }
            }
        });
    }

    /**
     * Read the source object within a Snappshot.
     * @param snappshot The snappshot to read.
     * @return The extracted object.
     */
    public static Object readSnappshot(final Snappshot snappshot) {
        final ClassLoader myCL = new ClassLoader(null) {

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytecode = snappshot.requiredClasses.get(name);
                if (bytecode == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytecode, 0, bytecode.length);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                byte[] resource = snappshot.requiredResources.get(name);
                if (resource == null) {
                    return null;
                }
                return new ByteArrayInputStream(resource);
            }
        };
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(snappshot.object)) {

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return Class.forName(desc.getName(), false, myCL);
                }
            };
            return ois.readObject();
        } catch (IOException ex) {
            throw new RuntimeException("We're reading from memory, so an IOException should not occur.", ex);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("The Snappshot is missing bytecode for a required class.", ex);
        }
    }

    // It might be useful to expose this in the future
    private interface BytecodeAccessor {

        /**
         * Return the bytecode for a class name.
         * @param className The class name, as given by Class.getName()
         * @return A byte array containing the bytecode, or null if it could not be found.
         */
        byte[] bytecodeForClass(String className);
    }

    // It might be useful to expose this in the future
    private static Snappshot writeSnappshot(final Object o, final BytecodeAccessor bytecodeAccessor) throws NotSerializableException {
        // Write the object to memory.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(o);
            oos.close();
        } catch (NotSerializableException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new RuntimeException("We're writing to memory, so an IOException should not occur.", ex);
        }
        byte[] serializedForm = baos.toByteArray();

        // Read the object back from memory, but using a custom ClassLoader that always reloads the classes from
        // scratch.  This way, we avoid the system's Class cache thereby discovering which classes are actually
        // required for deserialization.
        final HashMap<String, byte[]> requiredClasses = new HashMap<String, byte[]>();
        final HashMap<String, byte[]> requiredResources = new HashMap<String, byte[]>();
        final ClassLoader clientClassLoader = o.getClass().getClassLoader();
        final ClassLoader myCL = new ClassLoader(null) {

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytecode = bytecodeAccessor.bytecodeForClass(name);
                if (bytecode == null) {
                    throw new ClassNotFoundException(name);
                }
                requiredClasses.put(name, bytecode);
                return defineClass(name, bytecode, 0, bytecode.length);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                InputStream is = clientClassLoader.getResourceAsStream(name);
                byte[] resource;
                try {
                    resource = ByteStreams.toByteArray(is);
                } catch (IOException ex) {
                    return null;
                }
                requiredResources.put(name, resource);
                return new ByteArrayInputStream(resource);
            }
        };
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedForm)) {

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return Class.forName(desc.getName(), false, myCL);
                }
            };
            ois.readObject();
        } catch (IOException ex) {
            throw new RuntimeException("We're reading from memory, so an IOException should not occur.", ex);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("We're deserializing an object that we just serialized, so a ClassNotFoundException should not occur.", ex);
        }

        return new Snappshot(serializedForm, requiredClasses, requiredResources);
    }

    // From guava-libraries, it's not released yet.
    private static class ByteStreams {

        private static final int BUF_SIZE = 0x1000; // 4K

        /**
         * Copies all bytes from the input stream to the output stream.
         * Does not close or flush either stream.
         *
         * @param from the input stream to read from
         * @param to the output stream to write to
         * @return the number of bytes copied
         * @throws IOException if an I/O error occurs
         */
        public static long copy(InputStream from, OutputStream to)
                throws IOException {
            byte[] buf = new byte[BUF_SIZE];
            long total = 0;
            while (true) {
                int r = from.read(buf);
                if (r == -1) {
                    break;
                }
                to.write(buf, 0, r);
                total += r;
            }
            return total;
        }

        /**
         * Reads all bytes from an input stream into a byte array.
         * Does not close the stream.
         *
         * @param in the input stream to read from
         * @return a byte array containing all the bytes from the stream
         * @throws IOException if an I/O error occurs
         */
        public static byte[] toByteArray(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        }
    }
}
