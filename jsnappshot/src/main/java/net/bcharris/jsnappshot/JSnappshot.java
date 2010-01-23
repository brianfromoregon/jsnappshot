package net.bcharris.jsnappshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Reads and writes Snappshots of objects.
 */
public class JSnappshot {

    /**
     * Produce a Snappshot from the supplied object.
     * @param o The object from which to create a Snappshot.
     * @return The created snappshot
     * @throws NotSerializableException Some object to be serialized does not implement the java.io.Serializable interface.
     */
    public static Snappshot createSnappshot(final Object o) throws NotSerializableException {
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

        final HashMap<String, byte[]> allClasses = new HashMap<String, byte[]>();
        final HashMap<String, byte[]> allResources = new HashMap<String, byte[]>();
        new ClasspathWalker(Pattern.compile(".*\\.(class|properties)$", Pattern.CASE_INSENSITIVE), new ClasspathWalker.Visitor() {

            @Override
            public void visit(File location, String path, final byte[] data) {
                if (path.toLowerCase().endsWith("class")) {
                    allClasses.put(path.replace('/', '.').substring(0, path.lastIndexOf('.')), data);
                } else if (path.toLowerCase().endsWith("properties")) {
                    allResources.put(path, data);
                }
            }
        }).walk();
        return new Snappshot(serializedForm, allClasses, allResources);
    }

    /**
     * Read the source object within a Snappshot.
     * @param snappshot The snappshot to read.
     * @return The extracted object.
     */
    public static Object loadSnappshot(final Snappshot snappshot) {
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

    /**
     * Write an executable jar to the specified OutputStream which when run, invokes the specified Runnable.
     * @param entryPoint The entry point of the created executable jar.
     * @param output The executable jar's bytes will be written to this stream, then it will be closed.
     * @throws NotSerializableException Some object to be serialized does not implement the java.io.Serializable interface.
     * @throws IOException An exeception occurred writing to the specified OutputStream.
     */
    public static void createExecutableJar(Runnable entryPoint, OutputStream output) throws NotSerializableException, IOException {
        Snappshot snappshot = createSnappshot(entryPoint);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Main.class.getName());
        JarOutputStream jarOutputStream = new JarOutputStream(output, manifest);
        for (Entry<String, byte[]> entry : snappshot.requiredClasses.entrySet()) {
            add(entry.getKey().replace('.', '/') + ".class", entry.getValue(), jarOutputStream);
        }
        for (Entry<String, byte[]> entry : snappshot.requiredResources.entrySet()) {
            add(entry.getKey(), entry.getValue(), jarOutputStream);
        }
        if (!snappshot.requiredClasses.containsKey(Main.class.getName()))
        {
            String mainPath = Main.class.getName().replace('.', '/') + ".class";
            add(mainPath, ByteStreams.toByteArray(Main.class.getClassLoader().getResourceAsStream(mainPath)), jarOutputStream);
        }
        add(Main.ENTRY_POINT_PATH, snappshot.object, jarOutputStream);
        jarOutputStream.close();
    }

    private static void add(String path, byte[] data, JarOutputStream target) throws IOException {
        JarEntry entry = new JarEntry(path);
        target.putNextEntry(entry);
        target.write(data);
        target.closeEntry();
    }
}
