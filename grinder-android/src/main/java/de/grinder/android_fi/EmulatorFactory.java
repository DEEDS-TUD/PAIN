package de.grinder.android_fi;

import de.grinder.android_fi.Emulator.EmulatorConfiguration;

/**
 * Factory for {@link Emulator} instances. Each constructed {@link Emulator} instance gets
 * its own unique ID and control port according to the needs of the {@link Emulator}
 * class. Note that the factory does not keep track of the objects it creates. It merely
 * ensures that no two {@link Emulator} objects with conflicting IDs or ports are
 * constructed. This class is implemented as a singleton since there must not be multiple
 * instances of this factory to ensure unique IDs and ports. Note that all
 * {@link Emulator} objects must be created with this factory in order to guarantee unique
 * IDs and ports.
 * <p>
 * Emulator IDs start at {@code 0} and control ports are assigned starting at {@code 5554}
 * , which is the usual default port for Android SDK setups. This class does not detect
 * port conflicts. A large enough range of free ports starting from {@code 5554} must be
 * ensured externally.
 * <p>
 * This class is thread safe, i.e., it should be safe to invoke its methods from multiple
 * threads.
 */
public class EmulatorFactory {

  /** The singleton instance of this factory. */
  private static EmulatorFactory instance = new EmulatorFactory();

  /** Next emulator ID to use. */
  private int nextId = 0;

  /** NExt emulator control port to use. */
  private int nextControlPort = 5554;

  /**
   * Compute the next unique emulator ID.
   * 
   * @return The next emulator ID to use.
   */
  private int nextId() {
    return nextId++;
  }

  /**
   * Compute the next free emulator control port to use. Note that external port conflicts
   * are not detected.
   * 
   * @return The next control port to use.
   */
  private int nextControlPort() {
    final int tmp = nextControlPort;
    nextControlPort += 2;
    return tmp;
  }

  /**
   * Constructs the factory object. Singleton constructor.
   */
  private EmulatorFactory() {
  }

  /**
   * Retrieves the only instance of this factory.
   * 
   * @return The singleton instance of this factory.
   */
  public synchronized static EmulatorFactory getInstance() {
    return instance;
  }

  /**
   * Create a new {@link Emulator} instance with unique ID and ports.
   * 
   * @param expSettings
   *          Global experiment settings to use for the emulator.
   * @return A new {@link Emulator} instance.
   */
  public synchronized Emulator getNewEmu(final ExperimentSettings expSettings) {
    final EmulatorConfiguration es = new EmulatorConfiguration(nextId(),
        nextControlPort(), expSettings);
    final Emulator emu = new Emulator(es);
    return emu;
  }

}
