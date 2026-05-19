package dev.angzarr.client.router;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by {@link Router#build()} when the registered handlers are inconsistent or invalid.
 *
 * <p>Audit finding #59 (structural error model): carries a stable {@code code} (SCREAMING_SNAKE
 * identifier) and structured {@code details} for cross-language cucumber assertions.
 */
public class BuildException extends RuntimeException {

  private final String code;
  private final Map<String, String> details;

  public BuildException(String message) {
    this(message, null, "", null);
  }

  public BuildException(String message, Throwable cause) {
    this(message, cause, "", null);
  }

  /** Audit #59 structural form: stable code + structured details. */
  public BuildException(String message, Throwable cause, String code, Map<String, ?> details) {
    super(message, cause);
    this.code = code == null ? "" : code;
    if (details == null || details.isEmpty()) {
      this.details = Collections.emptyMap();
    } else {
      Map<String, String> m = new LinkedHashMap<>(details.size());
      for (Map.Entry<String, ?> e : details.entrySet()) {
        m.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
      }
      this.details = Collections.unmodifiableMap(m);
    }
  }

  /** SCREAMING_SNAKE stable identifier. Empty string when not set. */
  public String getCode() {
    return code;
  }

  /** Structured runtime context. Empty map when not set. */
  public Map<String, String> getDetails() {
    return details;
  }
}
