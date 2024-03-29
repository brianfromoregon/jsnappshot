package net.bcharris.jsnappshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

/**
 * Traverses files on the system classpath
 * Walking strategy taken from ClassFinder: http://www.clapper.org/software/java/util/javadocs/util/api/org/clapper/util/classutil/ClassFinder.html
 * I've considered patching ClassFinder to expose the functionality I want because at the moment much of the good stuff 
 * is in private methods.  The idea would be to split it into a walking half and an analying half, and I'd just use the
 * walking half.  Not even convinced of this approach yet, though.
 */
class ClasspathWalker {

    interface Visitor {

        /**
         * @param location Where the file was found.  Ex. a jar or zip file, or a directory.
         * @param fileName The file name relative to location
         * @param data The content of the file
         */
        void visit(File location, String fileName, byte[] data);
    }
    private final Set<File> placesToSearch = new HashSet<File>();
    private final Visitor visitor;
    private final Pattern fileNamePattern;

    public ClasspathWalker(Pattern fileNamePattern, Visitor visitor) {
        this.visitor = visitor;
        this.fileNamePattern = fileNamePattern;
        addClassPath();
    }

    public void walk() {
        for (File f : placesToSearch) {
            if (isJar(f)) {
                processJar(f);
            } else if (isZip(f)) {
                processZip(f);
            } else if (f.isDirectory()) {
                processDirectory(f);
            }
        }
    }

    private void addClassPath() {
        String path = null;

        try {
            path = System.getProperty("java.class.path");
        } catch (Exception ex) {
            path = "";
        }

        StringTokenizer tok = new StringTokenizer(path, File.pathSeparator);

        while (tok.hasMoreTokens()) {
            add(new File(tok.nextToken()));
        }
    }

    private void add(File f) {
        if (canContainClasses(f)) {
            placesToSearch.add(f);
            if (isJar(f)) {
                loadJarClassPathEntries(f);
            }
        }
    }

    private boolean isJar(File f) {
        return f.getName().toLowerCase().endsWith(".jar");
    }

    private boolean isZip(File f) {
        return f.getName().toLowerCase().endsWith(".zip");
    }

    private boolean canContainClasses(File f) {
        return f.exists() && (f.isDirectory() || isJar(f) || isZip(f));
    }

    private void processJar(File jarFile) {
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            processOpenZip(jar, jarFile);
        } catch (IOException ex) {
        } finally {
            try {
                jar.close();
            } catch (IOException ex) {
            }

            jar = null;
        }
    }

    private void processZip(File zipFile) {
        ZipFile zip = null;

        try {
            zip = new ZipFile(zipFile);
            processOpenZip(zip, zipFile);
        } catch (IOException ex) {
        } finally {
            try {
                zip.close();
            } catch (IOException ex) {
            }

            zip = null;
        }
    }

    private void processOpenZip(ZipFile zip, File zipFile) {
        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
            ZipEntry entry = e.nextElement();

            if (!entry.isDirectory() && fileNamePattern.matcher(entry.getName()).matches()) {
                try {
                    visitor.visit(zipFile, entry.getName(), ByteStreams.toByteArray(zip.getInputStream(entry)));
                } catch (IOException ex) {
                }
            }
        }
    }

    private void processDirectory(File dir) {
        IOFileFilter fileFilter = new AndFileFilter(FileFileFilter.FILE, new RegexFileFilter(fileNamePattern));
        Collection<File> files = FileUtils.listFiles(dir, fileFilter, TrueFileFilter.TRUE);


        for (File f : files) {
            InputStream is = null;
            try {
                is = new FileInputStream(f);
                String relPath = dir.toURI().relativize(f.toURI()).getPath();
                visitor.visit(dir, relPath, ByteStreams.toByteArray(is));
            } catch (IOException ex) {
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }
    }

    private void loadJarClassPathEntries(File jarFile) {
        try {
            JarFile jar = new JarFile(jarFile);
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return;
            }

            Attributes attrs = manifest.getMainAttributes();
            Set<Object> keys = attrs.keySet();

            for (Object key : keys) {
                String value = (String) attrs.get(key);

                if (key.toString().equals("Class-Path")) {
                    StringBuilder buf = new StringBuilder();
                    StringTokenizer tok = new StringTokenizer(value);
                    while (tok.hasMoreTokens()) {
                        buf.setLength(0);
                        String element = tok.nextToken();
                        String parent = jarFile.getParent();
                        if (parent != null) {
                            buf.append(parent);
                            buf.append(File.separator);
                        }

                        buf.append(element);
                    }

                    String element = buf.toString();

                    add(new File(element));
                }
            }
        } catch (IOException ex) {
        }
    }
}
