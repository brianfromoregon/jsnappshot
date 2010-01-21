package net.bcharris.jsnappshot;

import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Pass a snappshot file name containing a Runnable as the first argument.");
            return;
        }
        Snappshot snappshot = (Snappshot) new ObjectInputStream(new FileInputStream(args[0])).readObject();
        Runnable r = (Runnable) JSnappshot.loadSnappshot(snappshot);
        r.run();
    }
}
