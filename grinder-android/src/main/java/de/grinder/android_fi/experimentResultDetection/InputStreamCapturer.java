package de.grinder.android_fi.experimentResultDetection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * <p>
 * This class is thread safe, i.e., it should be safe to invoke its methods from multiple
 * threads.
 */
public class InputStreamCapturer {

  private final Logger LOGGER;
  private final static int DEFAULT_BUFFER_SIZE = 10 * 1024;
  private final static long WAIT_FOR_INPUT_DELAY = 200;

  private BufferedReader reader;
  private OutputCollector currentCollector;
  private Thread collectorThread;
  private final CircularStringBuffer consoleBuffer;
  private final List<PrintStream> forwardedWriters = new ArrayList<PrintStream>();

  public InputStreamCapturer(final String logId) {
    this(DEFAULT_BUFFER_SIZE, logId);
  }

  public InputStreamCapturer(final int bufferSize, final String logId) {
    LOGGER = LoggerFactory.getLogger(String.format("%s<%s>",
        InputStreamCapturer.class.getName(), logId));
    consoleBuffer = new CircularStringBuffer(bufferSize);
  }

  public void startCapturing(final InputStream input) {
    synchronized (this) {
      consoleBuffer.clear();
      reader = new BufferedReader(new InputStreamReader(input));
      currentCollector = new OutputCollector();
      collectorThread = new Thread(currentCollector);
      collectorThread.start();
    }
    LOGGER.info("Capturing input started");
  }

  public void stopCapturing() throws IOException {
    LOGGER.debug("Stopping input capturing");
    synchronized (this) {
      // add some wait in order not to miss too much output at the end
      try {
        Thread.sleep(3 * WAIT_FOR_INPUT_DELAY);
      } catch (final InterruptedException e) {
        // ignore interrupt, waiting is a hack anyway, but reset interrupt state of thread
        Thread.currentThread().interrupt();
      }
      reader.close();
      currentCollector.stop();
      boolean interrupted = false;
      while (true) {
        try {
          collectorThread.join();
          break;
        } catch (final InterruptedException e) {
          // ignore and try again since we want a clean shutdown, but remember interrupt
          interrupted = true;
        }
      }
      reader = null;
      if (interrupted) {
        // reset interrupt state
        Thread.currentThread().interrupt();
      }
    }
    LOGGER.debug("Capturing input stopped");
  }

  public String getOutput() {
    return consoleBuffer.toString();
  }

  public boolean contains(final String match) {
    return consoleBuffer.contains(match);
  }

  public void forwardOutputTo(final PrintStream pw) {
    synchronized (forwardedWriters) {
      forwardedWriters.add(pw);
    }
  }

  public void stopForwardingTo(final PrintStream pw) {
    synchronized (forwardedWriters) {
      forwardedWriters.remove(pw);
    }
  }

  public void stopForwarding() {
    synchronized (forwardedWriters) {
      forwardedWriters.clear();
    }
  }

  private void forwardLine(final String value) throws IOException {
    synchronized (forwardedWriters) {
      for (final PrintStream ps : forwardedWriters) {
        ps.println(value);
      }
    }
    LOGGER.debug("Forwarded: " + value);
  }

  private class OutputCollector implements Runnable {

    private boolean stopped = false;

    @Override
    public void run() {
      String line = null;
      while (!stopped) {
        try {
          // note that no further synchronization should be needed here due to the
          // (implicit) protocol used within this class
          while (!stopped && (line = reader.readLine()) != null) {
            forwardLine(line);
            consoleBuffer.put(line + "\n");
          }
          Thread.sleep(WAIT_FOR_INPUT_DELAY);
        } catch (IOException | InterruptedException e) {
          // keep silent here since this is used to escape readLine after emu
          // emulator (stream source) was killed
        }
      }
    }

    public void stop() {
      stopped = true;
    }
  }
}
