package de.grinder.android_fi.experimentResultDetection;

import java.util.HashMap;
import java.util.Map;

public enum ExperimentResult {
  //@formatter:off
  EXPERIMENT_FAILURE((short) -1),
  NONE((short) 0),
  FINISHED((short) 1),
  SYSTEM_HANG_DETECTED((short) 2),
  SYSTEM_HANG_ASSUMED((short) 3),
  SYSTEM_CRASH_DETECTED((short) 4),
  APPLICATION_HANG_DETECTED((short) 5),
  APPLICATION_HANG_ASSUMED((short) 6),
  APPLICATION_FAULT_DETECTED((short) 7),
  SYSINIT_HANG_ASSUMED((short)8 ),
  SYSTEM_OOPS_DETECTED((short)9 );
  //@formatter:on

  private final short id;
  private static final Map<Short, ExperimentResult> lookup = new HashMap<>();

  private ExperimentResult(final short id) {
    this.id = id;
  }

  static {
    for (final ExperimentResult messageType : ExperimentResult.values()) {
      lookup.put(messageType.id, messageType);
    }
  }

  public static ExperimentResult getMessageType(final short id) {
    final ExperimentResult type = lookup.get(id);
    if (type == null) {
      throw new RuntimeException(String.format("Illegal message type: %d", id));
    }
    return type;
  }

  public short getId() {
    return id;
  }
}
