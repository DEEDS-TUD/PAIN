package de.grinder.android_fi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.grinder.android_fi.Emulator.EmulatorConfiguration;
import de.grinder.android_fi.Emulator.EmulatorException;
import de.grinder.android_fi.ExperimentSettings.SettingsException;

@Ignore
public class EmulatorTest {

    static final Logger LOG = LoggerFactory.getLogger(EmulatorTest.class);

    private final int DEFAULT_PORT = 5554;

    private static ExperimentSettings expSettings;

    @BeforeClass
    public static void createEnvironment() {
        LOG.info("Setting up test environment");
        expSettings = loadSettings("grinder-afi-mini.properties");
    }

    @AfterClass
    public static void finish() {
        LOG.info("Tests finished");
    }

    private static ExperimentSettings loadSettings(final String propFileName) {
        final URL propFile = EmulatorTest.class.getClassLoader().getResource(propFileName);
        assertNotNull(
            String.format("Failed to find grinder-afi properties file [%s]!", propFileName),
            propFile);
        try {
            return new ExperimentSettings(propFile.getPath(), null);
        } catch (final SettingsException e) {
            fail(String.format("Failed to load experiment settings: %s", e.getMessage()));
        }
        return null;
    }

    private Emulator getEmu(final int id, final int port) {
        final EmulatorConfiguration es = new EmulatorConfiguration(id, port, expSettings);
        final Emulator emu = new Emulator(es);
        return emu;
    }

    @Test
    public void testSettings() {
        final int id = 42;
        final int port = 4242;
        final ExperimentSettings expSettings = loadSettings("grinder-afi-dummy.properties");
        final String prefix = String.format("emu-%d", id);

        final EmulatorConfiguration es = new EmulatorConfiguration(id, port, expSettings);
        assertEquals("Invalid control port", port, es.getControlPort());
        assertEquals("Invalid ADB port", port + 1, es.getAdbPort());
        assertEquals("Unexpected emulator name!", "emulator-4242", es.getAdbName());
        assertEquals("Wring SD vanilla image",
                     "/path/to/sdcard/vanilla/images/vanilla_sdcard.img", es.getSdCardVanillaImage());
        assertEquals("Wrong userdata vanilla image",
                     "/path/to/userdata/vanilla/images/vanilla_userdata.img",
                     es.getUserdataVanillaImage());
        assertEquals("Wrong system vanilla image",
                     "/path/to/system/vanilla/images/vanilla_system.img", es.getSystemVanillaImage());

        assertEquals("Wrong global SD image!", "/path/to/sdcard/images/sdcard.img",
                     expSettings.SDCARD_IMAGE);
        assertEquals("Wrong global userdata image! ",
                     "/path/to/userdata/images/userdata.img", expSettings.USERDATA_IMAGE);
        assertEquals("Wrong global system image! ", "/path/to/system/images/system.img",
                     expSettings.SYSTEM_IMAGE);

        assertEquals("Wrong SD image!",
                     String.format("/path/to/sdcard/images/%s-sdcard.img", prefix),
                     es.getSdCardImage());
        assertEquals("Wrong userdata image!",
                     String.format("/path/to/userdata/images/%s-userdata.img", prefix),
                     es.getUserdataImage());
        assertEquals("Wrong system image!",
                     String.format("/path/to/system/images/%s-system.img", prefix),
                     es.getSystemImage());
    }

    @Test
    public void testEmuRunSingle() throws EmulatorException, InterruptedException {
        final Emulator emu = getEmu(0, DEFAULT_PORT);
        final EmulatorConfiguration es = emu.getConfig();
        emu.start();
        assertTrue("Emulator has no associated accounting process!", emu.hasAccProcess());
        assertTrue("Emulator accounting is not running!", emu.isAccRunning());
        Thread.sleep(100); // wait for emu start
        assertTrue("Emulator is not running!", emu.isEmuRunning());
        assertEquals("Wrong control port!", es.getControlPort(), DEFAULT_PORT);
        assertEquals("Wrong ADB port!", es.getAdbPort(), DEFAULT_PORT + 1);
        Thread.sleep(20 * 1000);
        assertTrue("Emulator terminated prematurely!", emu.isEmuRunning());
        assertTrue("Emulator accounting terminated prematurely!", emu.isAccRunning());
        emu.kill();
        System.out.flush();
        assertFalse("Emulator accounting process was not detached!", emu.hasAccProcess());
        assertFalse("Emulator was not killed!", emu.isEmuRunning());
        assertFalse("Emulator accounting was not terminated!", emu.isAccRunning());

    }

    private void runAndTestMultiEmu(final int n) throws EmulatorException,
        InterruptedException {
        final Emulator emus[] = new Emulator[n];
        for (int i = 0; i < n; ++i) {
            System.out.format("Starting emu %d on port %d\n", i, DEFAULT_PORT + (2 * i));
            emus[i] = getEmu(i, DEFAULT_PORT + (2 * i));
            emus[i].start();
            assertTrue(emus[i].isEmuRunning());
            Thread.sleep(10 * 1000);
        }
        for (int i = 0; i < n; ++i) {
            assertEquals("Wrong control port!", emus[i].getConfig().getControlPort(),
                         DEFAULT_PORT + (2 * i));
            assertEquals("Wrong ADB port!", emus[i].getConfig().getAdbPort(), emus[i]
                         .getConfig().getControlPort() + 1);
            assertTrue(String.format("Emulator %d has not started correctly!", i),
                       emus[i].isEmuRunning());
        }
        System.out.println("Waiting");
        Thread.sleep(5 * 60 * 1000);
        for (int i = 0; i < n; ++i) {
            System.out.format("Killing emu %d\n", i);
            emus[i].kill();
            assertFalse(String.format("Emulator %d was not killed correctly!", i),
                        emus[i].isEmuRunning());
            Thread.sleep(500);
        }
    }

    @Ignore
    @Test
    public void testEmuRunTwo() throws EmulatorException, InterruptedException {
        runAndTestMultiEmu(2);
    }

    @Ignore
    @Test
    public void testEmuRunFour() throws EmulatorException, InterruptedException {
        runAndTestMultiEmu(4);
    }

}
