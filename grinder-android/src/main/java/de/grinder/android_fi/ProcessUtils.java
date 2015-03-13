package de.grinder.android_fi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * Utilities for controlling processes through operating system facilities.
 * <p>
 * Note that this class uses unofficial APIs and hacking techniques that are specific to
 * the Java and JVM version. <b>This class may stop working correctly if OS, Java version
 * or JVM are changed!</b>Currently, only support Unix processes, i.e., Unix/Linux
 * operating systems, are supported.
 */
public class ProcessUtils {

  private static final String UNIX_PROCESS_CLASS_NAME = "java.lang.UNIXProcess";

  public static final int SIG_KILL = 9;
  public static final int SIG_TERM = 15;

  /**
   * Retrieves the PID of the OS process that is represented by the given Java
   * {@link Process} object. Note that this method only works for UNIX processes and uses
   * unsafe techniques that may stop working in the future.
   * 
   * @param proc
   *          The Java Process object that represents a running OS process.
   * @return The PID of the given process.
   * @throws ProcUtilException
   *           If the PID could not be retrieved, e.g. due to an unsupported platform or
   *           Java reflection issues.
   */
  public static int getPid(final Process proc) throws ProcUtilException {
    final String procClassName = proc.getClass().getName();

    // are we dealing with a UNIX process? we only support UNIX here
    if (procClassName.equals(UNIX_PROCESS_CLASS_NAME)) {
      try {
        final Field pidField = proc.getClass().getDeclaredField("pid");
        pidField.setAccessible(true);
        return pidField.getInt(proc);
      } catch (final Exception e) {
        throw new ProcUtilException(String.format(
            "Failed to access PID field of UNIX process class instance: %s",
            e.getMessage()), e);
      }
    } else {
      throw new ProcUtilException(String.format(
          "Unsupported Java process class implementation: %s", proc.getClass().getName()));
    }
  }

  /**
   * Retrieves the output (stdout) of the process represented by the given {@link Process}
   * object. This method blocks until the process is about to terminate and returns the
   * complete output as a string. The returned string contains newlines after each output
   * line. This is also true for the last line and for processes that do not output line
   * endings, i.e., the string always has a trailing newline except for empty outputs.
   * Note that the process is likely to have not terminated yet after this method returns.
   * 
   * @param proc
   *          The process object.
   * @return The stdout output of the process. May be an empty string. The string always
   *         has a trailing newline if it is not empty.
   * @throws IOException
   *           If the process output cannot be read.
   */
  public static String getProcessOutput(final Process proc) throws IOException {
    try (final InputStreamReader isr = new InputStreamReader(proc.getInputStream());
        final BufferedReader r = new BufferedReader(isr)) {
      final StringBuilder sb = new StringBuilder();
      String line;
      while ((line = r.readLine()) != null) {
        sb.append(line);
        sb.append("\n");
      }
      return sb.toString();
    } catch (final IOException e) {
      throw e;
    }
  }

  /**
   * Retrieves the process name/command for a given PID from the OS. This method is almost
   * identical to {@link ProcessUtilsTest#getProcNameNull(int)} but it throws an exception
   * instead of returning {@code null}.
   * 
   * @param pid
   *          The PID to find the process name for.
   * @return The process name.
   * @throws ProcUtilException
   *           If something went wrong or the process with the given PID does not exist.
   */
  public static String getProcName(final int pid) throws ProcUtilException {
    final String name = getProcNameNull(pid);
    if (name == null) {
      throw new ProcUtilException(String.format(
          "Failed to retrieve process name for PID %d. Process not existing?", pid));
    }
    return name;
  }

  /**
   * Retrieves the process name/command for a given PID from the OS.
   * <p>
   * Note that this method invokes an external OS command and parses its output. This
   * method may not work correctly on all platforms. On Unix-like platforms, the
   * {@code ps} command is invoked.
   * 
   * @param pid
   *          The PID to find the process name for.
   * @return The process name or {@code null} if no process with the given PID exists.
   * @throws ProcUtilException
   *           If the external process could not be started or if its output could not be
   *           read.
   */
  public static String getProcNameNull(final int pid) throws ProcUtilException {
    // Unix command: ps -o cmd --no-headers -p <pid>
    final Process p = startProcessPipe("ps", "-o", "cmd", "--no-headers", "-p",
        String.valueOf(pid));
    String pOut;
    int pRet;
    try {
      pOut = getProcessOutput(p);
      pRet = p.waitFor();
    } catch (IOException | InterruptedException e) {
      p.destroy();
      throw new ProcUtilException(String.format(
          "Failed to retrieve process name for PID %d: %s", pid, e.getMessage()), e);
    }
    if (pRet != 0 || pOut.isEmpty()) {
      return null;
    }
    return pOut.trim();
  }

  /**
   * Checks whether a process with the given PID is running. Note that this method checks
   * the existence of the process in the system, it does not check process properties such
   * as aliveness or similar.
   * <p>
   * Note that this method invokes an external OS command and parses its output. This
   * method may not work correctly on all platforms. On Unix-like platforms, the
   * {@code ps} command is invoked.
   * 
   * @param pid
   *          The PID to check.
   * @return {@code True} if the PID belongs to an existing/running process, {@code false}
   *         otherwise.
   * @throws ProcUtilException
   *           If something went wrong.
   */
  public static boolean isRunning(final int pid) throws ProcUtilException {
    return getProcNameNull(pid) != null;
  }

  /**
   * Checks whether the process represented by the given {@link Process} object is
   * running. This method does not invoke external commands. Note that this method checks
   * the existence of the process in the system, it does not check process properties such
   * as aliveness or similar.
   * 
   * @param proc
   *          The process object to be checked.
   * @return {@code True} if the process is running, {@code false} otherwise.
   */
  public static boolean isRunning(final Process proc) {
    try {
      proc.exitValue();
    } catch (final IllegalThreadStateException e) {
      return true;
    }
    return false;
  }

  /**
   * Kill the process with the given PID using the TERM signal (graceful kill). This
   * method is almost the same as {@link ProcessUtilsTest#kill(int, int)}.
   * <p>
   * Note that this method invokes an external OS command and parses its output. This
   * method may not work correctly on all platforms. On Unix-like platforms, the
   * {@code kill} and {@code ps} commands are invoked.
   * 
   * @param pid
   *          The PID of the process to kill.
   * @param signal
   *          The signal to send.
   * @throws ProcUtilException
   *           If something with the external commands goes wrong.
   */
  public static void kill(final int pid) throws ProcUtilException {
    kill(pid, SIG_TERM);
  }

  /**
   * Kill the process with the given PID using the specified signal. This method should
   * not be used to send other signals than SIG_TERM and SIG_KILL to a process as the
   * method may check if the process was successfully terminated. If the process to kill
   * does not exist (or has already terminated), the method considers the killing an
   * success and does not signal any error.
   * <p>
   * Note that this method invokes an external OS command. This method may not work
   * correctly on all platforms. On Unix-like platforms, the {@code kill} and {@code ps}
   * commands are invoked.
   * <p>
   * Note that this method is not interruptible.
   * 
   * @param pid
   *          The PID of the process to kill.
   * @param signal
   *          The signal to send.
   * @throws ProcUtilException
   *           If something with the external commands goes wrong or the process was not
   *           killed.
   */
  public static void kill(final int pid, final int signal) throws ProcUtilException {
    // Unix command: kill -s <signal> <pid>
    final Process p = startProcessInhIo("kill", "-s", String.valueOf(signal),
        String.valueOf(pid));

    boolean interrupted = false;
    while (true) {
      try {
        if (p.waitFor() != 0 && isRunning(pid)) {
          throw new ProcUtilException(String.format(
              "Failed to kill process with PID %d using signal %d.", pid, signal));
        }
        break;
      } catch (final InterruptedException e) {
        interrupted = true;
        // ignore, killing is too important and usually quick to allow interrupting
      }
    }
    if (interrupted) {
      // restore interrupt state if need be
      Thread.currentThread().interrupt();
    }
  }

  public static void kill(final Process proc) throws ProcUtilException {
    kill(getPid(proc));
  }

  public static void kill(final Process proc, final int signal) throws ProcUtilException {
    kill(getPid(proc), signal);
  }

  /**
   * Starts executing the given command in a new process and returns its {@link Process}
   * object. Depending on the {@code pipeIo} parameter, the I/O streams of the created
   * process are either inherited from the parent process or piped to own streams. Note
   * that the I/O streams are only accessible via the {@link Process} objects's interface
   * if the I/O is piped.
   * 
   * @param pipeIo
   *          If {@code true}, the I/O streams are piped in order to be accessible via the
   *          returned {@link Process} object. IF {@code false}, the I/O streams are
   *          inherited from the parent process.
   * @param cmd
   *          The command to execute.
   * @return The process object for the created and running process.
   * @throws ProcUtilException
   *           If the process could not be started.
   */
  public static Process startProcess(final boolean pipeIo, final String... cmd)
      throws ProcUtilException {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    if (!pipeIo) {
      pb = pb.inheritIO();
    } else {
      pb.redirectErrorStream(true);
    }
    try {
      return pb.start();
    } catch (final IOException e) {
      throw new ProcUtilException(String.format(
          "Failed to start process for command %s: %s", Arrays.toString(cmd),
          e.getMessage()), e);
    }
  }

  /**
   * Starts executing the given command in a new process with inherited I/O streams. This
   * is a convenience wrapper for {@link #startProcess(boolean, String...)}.
   * 
   * @param cmd
   *          The command to execute.
   * @return The process object for the created and running process.
   * @throws ProcUtilException
   *           If the process could not be started.
   */
  public static Process startProcessInhIo(final String... cmd) throws ProcUtilException {
    return startProcess(false, cmd);
  }

  /**
   * Starts executing the given command in a new process with piped I/O streams. This is a
   * convenience wrapper for {@link #startProcess(boolean, String...)}.
   * 
   * @param cmd
   *          The command to execute.
   * @return The process object for the created and running process.
   * @throws ProcUtilException
   *           If the process could not be started.
   */
  public static Process startProcessPipe(final String... cmd) throws ProcUtilException {
    return startProcess(true, cmd);
  }

  /**
   * Exception class for signaling {@link ProcessUtils} related errors.
   */
  public static class ProcUtilException extends Exception {

    private static final long serialVersionUID = -8828753883106396094L;

    public ProcUtilException() {
      super();
    }

    public ProcUtilException(final String message) {
      super(message);
    }

    public ProcUtilException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public ProcUtilException(final String message, final Throwable cause,
        final boolean enableSuppression, final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }

    public ProcUtilException(final Throwable cause) {
      super(cause);
    }

  }

}
