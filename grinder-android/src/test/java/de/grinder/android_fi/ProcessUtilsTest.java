package de.grinder.android_fi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import de.grinder.android_fi.ProcessUtils.ProcUtilException;

public class ProcessUtilsTest {

    @Test
    public void testStartProcessOk() throws ProcUtilException {
        ProcessUtils.startProcessPipe("java", "-version");
    }

    @Test(expected = ProcUtilException.class)
    public void testStartProcessFail() throws ProcUtilException {
        ProcessUtils.startProcessPipe("javaaaaaa");
    }

    @Test
    public void testGetPidCatRunning() throws ProcUtilException, InterruptedException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        final int pid = ProcessUtils.getPid(p);
        assertTrue("Invalid PID", pid > 0);
        System.out.println(String.format("testGetPidCatRunning: %d", pid));
        p.destroy();
        p.waitFor();
        p.exitValue();
    }

    @Test
    public void testGetPidCatStopped() throws ProcUtilException, InterruptedException {
        final Process p = ProcessUtils.startProcessPipe("cat", "invalidFile");
        p.waitFor();
        final int pid = ProcessUtils.getPid(p);
        assertTrue("Invalid PID", pid > 0);
        System.out.println(String.format("testGetPidCatStopped: %d", pid));
    }

    @Test
    public void testGetProcessOutputOk() throws ProcUtilException, IOException {
        final Process p = ProcessUtils.startProcessPipe("echo", "42");
        final String out = ProcessUtils.getProcessOutput(p);
        assertEquals("Wrong process output!", "42\n", out);
    }

    @Test
    public void testGetProcessOutputOkNoNl() throws ProcUtilException, IOException {
        final Process p = ProcessUtils.startProcessPipe("echo", "-n", "42");
        final String out = ProcessUtils.getProcessOutput(p);
        assertEquals("Wrong process output!", "42\n", out);
    }

    @Test
    public void testGetProcessOutputEndless() throws ProcUtilException, IOException {
        final ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor();
        final Process p = ProcessUtils.startProcessPipe("cat");
        s.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                p.destroy();
            }
        }, 800, 200, TimeUnit.MILLISECONDS);
        final String out = ProcessUtils.getProcessOutput(p);
        assertEquals("Canceled 'cat' should have empty output!", "", out);
    }

    @Test
    public void testGetProcNameNullFail() throws ProcUtilException {
        final String name = ProcessUtils.getProcNameNull(-1);
        assertNull("No name string should have been returned!", name);
    }

    @Test
    public void testGetProcNameNullOk() throws ProcUtilException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        final String name = ProcessUtils.getProcNameNull(ProcessUtils.getPid(p));
        assertEquals("Unexpected process name!", "cat", name);
    }

    @Test(expected = ProcUtilException.class)
    public void testGetProcNameFail() throws ProcUtilException {
        ProcessUtils.getProcName(-1);
    }

    @Test
    public void testGetProcNameOk() throws ProcUtilException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        final String name = ProcessUtils.getProcNameNull(ProcessUtils.getPid(p));
        assertEquals("Unexpected process name!", "cat", name);
    }

    @Test
    public void testIsRunningNo() throws ProcUtilException, InterruptedException {
        final Process p = ProcessUtils.startProcessPipe("echo", "42");
        p.waitFor();
        assertFalse(ProcessUtils.isRunning(p));
    }

    @Test
    public void testIsRunningOk() throws ProcUtilException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        assertTrue(ProcessUtils.isRunning(p));
        p.destroy();
    }

    @Test
    public void testIsRunningPidNo() throws ProcUtilException {
        assertFalse(ProcessUtils.isRunning(-1));
    }

    @Test
    public void testIsRunningPidOk() throws ProcUtilException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        assertTrue(ProcessUtils.isRunning(ProcessUtils.getPid(p)));
        p.destroy();
    }

    private void startKillCatWithSignal(final int signal) throws ProcUtilException {
        final Process p = ProcessUtils.startProcessPipe("cat");
        final int pid = ProcessUtils.getPid(p);
        assertTrue(ProcessUtils.isRunning(pid));
        assertTrue(ProcessUtils.isRunning(p));
        ProcessUtils.kill(pid, signal);
        assertFalse(ProcessUtils.isRunning(pid));
        assertFalse(ProcessUtils.isRunning(p));
    }

    @Test
    public void testKillPidSigTermOk() throws ProcUtilException {
        startKillCatWithSignal(ProcessUtils.SIG_TERM);
    }

    @Test
    public void testKillPidSigKillOk() throws ProcUtilException {
        startKillCatWithSignal(ProcessUtils.SIG_KILL);
    }

    @Test
    public void testKillPidFail() throws ProcUtilException {
        ProcessUtils.kill(-1, ProcessUtils.SIG_TERM);
        ProcessUtils.kill(-1, ProcessUtils.SIG_KILL);
    }

}
