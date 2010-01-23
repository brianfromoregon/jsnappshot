package net.bcharris.jsnappshot;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;

public class Main {

    static final String ENTRY_POINT_PATH = "net/bcharris/jsnappshot/EntryPoint.ser";

    public static void main(String[] args) throws Exception {
        // Check to see if we were created by ourself.
        InputStream resource = Main.class.getClassLoader().getResourceAsStream(ENTRY_POINT_PATH);
        if (resource != null) {
            ((Runnable) new ObjectInputStream(resource).readObject()).run();
            return;
        }

        if (args.length == 0) {
            System.err.println("Pass a snappshot file name containing a Runnable as the first argument.");
            return;
        } else {
            Snappshot snappshot = (Snappshot) new ObjectInputStream(new FileInputStream(args[0])).readObject();
            Runnable r = (Runnable) JSnappshot.loadSnappshot(snappshot);
            r.run();
        }
    }
}
