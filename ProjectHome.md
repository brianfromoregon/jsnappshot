# Five second introduction #
Create an executable jar from an arbitrary Runnable:
```
JSnappshot.createExecutableJar(myRunnable, new FileOutputStream("runme.jar"));
```
Email the jar to a friend, no ClassNotFoundException or NoClassDefFoundError when they run it!

# Demo #
Download and run the [JFreeChart demo](http://jsnappshot.googlecode.com/files/JFreeChart-SaveAs-Demo.jar).  The Save As... right click option has been hijacked to create an executable jar which pops up a JFrame containing the same chart as the original.  Sort of anticlimactic, but just imagine how much easier it could be to share a chart with a friend without them having to run your app that generated the chart.


# One minute introduction #

Serializing objects in Java writes out Object data, but not Class data.  This means that to deserialize an object, one must have the correct versions of all the necessary classes available to their ClassLoader.  Otherwise, they encounter the lovely  ClassNotFoundException or NoClassDefFoundError.

This library allows the serializer to include the necessary classes along with the objects so that the consumer doesn't need to worry about it.  I'm calling that bundle of goodness a Snappshot.

### Example ###
Share a JFreeChart chart with a client that doesn't have the JFreeChart jar on their classpath.

Producer (has JFreeChart on classpath):
```
double[][] data = new double[][]{{1, 2, 3, 4, 5, 6}, {1, 1, 2, 3, 5, 8}};
DefaultXYDataset dataset = new DefaultXYDataset();
dataset.addSeries("first", data);
final XYPlot plot = new XYPlot(dataset, new NumberAxis(), new NumberAxis(), new DefaultXYItemRenderer());
abstract class SerializableRunnable implements Serializable, Runnable {};
Runnable r = new SerializableRunnable() {
    @Override
    public void run() {
	ChartFrame frame = new ChartFrame("Chart", new JFreeChart(plot));
	frame.pack();
	frame.setVisible(true);
	frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
};

// Write the Snappshot to a file for the consumer
Snappshot snappshot = JSnappshot.createSnappshot(r);
ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
oos.writeObject(snappshot);
oos.close();

// Or simpler yet, create an executable jar which runs the Runnable.  No consumer necessary!
JSnappshot.createExecutableJar(r, new FileOutputStream("runme.jar"));
```

Consumer (does not have JFreeChart on classpath):
```
Runnable r = (Runnable) JSnappshot.loadSnappshot((Snappshot) new ObjectInputStream(new FileInputStream(file)).readObject());
r.run();
```

When it runs, it pops up an interactive JFreeChart.