package dev.angzarr.client.router;

import dev.angzarr.CommandBook;
import dev.angzarr.EventBook;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response from process manager handlers.
 *
 * <p>Process managers produce:
 *
 * <ul>
 *   <li>Commands (to send to other aggregates)
 *   <li>Process events (to persist their own state) — list of EventBooks passed through verbatim to
 *       the wire's {@code repeated EventBook process_events} field
 *   <li>Facts (events to inject directly into other aggregates)
 * </ul>
 *
 * <p>Audit #92 (2026-04-29 revert): {@code process_events} is {@code repeated EventBook} on the
 * wire so the coordinator gets every book intact; the client passes them through without merging.
 * Mirrors Python {@code router.responses.ProcessManagerResponse.process_events: list[EventBook]}
 * and Rust {@code Vec<EventBook>}.
 *
 * <p>Back-compat: the single-{@code EventBook} factory and accessor remain (wrapping the value in a
 * one-element list / unwrapping the first element) so existing callers compile unchanged.
 */
public class ProcessManagerResponse {

  private final List<CommandBook> commands;
  private final List<EventBook> processEvents;
  private final List<EventBook> facts;

  private ProcessManagerResponse(
      List<CommandBook> commands, List<EventBook> processEvents, List<EventBook> facts) {
    this.commands = commands != null ? commands : Collections.emptyList();
    this.processEvents = processEvents != null ? processEvents : Collections.emptyList();
    this.facts = facts != null ? facts : Collections.emptyList();
  }

  /** Create an empty response (no commands, no process events, no facts). */
  public static ProcessManagerResponse empty() {
    return new ProcessManagerResponse(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  /** Create a response with commands only. */
  public static ProcessManagerResponse withCommands(List<CommandBook> commands) {
    return new ProcessManagerResponse(
        new ArrayList<>(commands), Collections.emptyList(), Collections.emptyList());
  }

  /**
   * Create a response with a single process-events book.
   *
   * <p>Back-compat shim: wraps {@code processEvents} in a one-element list. Prefer {@link
   * #withProcessEventsList(List)} for new code.
   */
  public static ProcessManagerResponse withProcessEvents(EventBook processEvents) {
    List<EventBook> events =
        processEvents == null ? Collections.emptyList() : List.of(processEvents);
    return new ProcessManagerResponse(Collections.emptyList(), events, Collections.emptyList());
  }

  /**
   * Audit #92: create a response with a list of process-events books, passed through verbatim to
   * the wire's {@code repeated EventBook process_events}.
   */
  public static ProcessManagerResponse withProcessEventsList(List<EventBook> processEvents) {
    return new ProcessManagerResponse(
        Collections.emptyList(),
        processEvents == null ? Collections.emptyList() : new ArrayList<>(processEvents),
        Collections.emptyList());
  }

  /** Create a response with facts only. */
  public static ProcessManagerResponse withFacts(List<EventBook> facts) {
    return new ProcessManagerResponse(
        Collections.emptyList(), Collections.emptyList(), new ArrayList<>(facts));
  }

  /**
   * Create a response with commands and a single process-events book.
   *
   * <p>Back-compat shim — see {@link #withProcessEvents(EventBook)}.
   */
  public static ProcessManagerResponse withBoth(
      List<CommandBook> commands, EventBook processEvents) {
    List<EventBook> events =
        processEvents == null ? Collections.emptyList() : List.of(processEvents);
    return new ProcessManagerResponse(new ArrayList<>(commands), events, Collections.emptyList());
  }

  /** Create a response with all fields (single process-events book — back-compat). */
  public static ProcessManagerResponse withAll(
      List<CommandBook> commands, EventBook processEvents, List<EventBook> facts) {
    List<EventBook> events =
        processEvents == null ? Collections.emptyList() : List.of(processEvents);
    return new ProcessManagerResponse(new ArrayList<>(commands), events, new ArrayList<>(facts));
  }

  /**
   * Audit #92: create a response with all fields, taking a list of process-events books that the
   * dispatch layer passes through verbatim.
   */
  public static ProcessManagerResponse withAllList(
      List<CommandBook> commands, List<EventBook> processEvents, List<EventBook> facts) {
    return new ProcessManagerResponse(
        new ArrayList<>(commands),
        processEvents == null ? Collections.emptyList() : new ArrayList<>(processEvents),
        new ArrayList<>(facts));
  }

  /** Get the commands to send to other aggregates. */
  public List<CommandBook> getCommands() {
    return commands;
  }

  /** Check if response has commands. */
  public boolean hasCommands() {
    return !commands.isEmpty();
  }

  /**
   * Back-compat accessor returning the first process-events book, or {@code null} when none were
   * emitted. New callers should prefer {@link #getProcessEventsList()}.
   */
  public EventBook getProcessEvents() {
    return processEvents.isEmpty() ? null : processEvents.get(0);
  }

  /**
   * Audit #92: list of process-events books emitted by this handler, passed through verbatim into
   * the wire's {@code repeated EventBook process_events}.
   */
  public List<EventBook> getProcessEventsList() {
    return processEvents;
  }

  /** Check if response has process events. */
  public boolean hasProcessEvents() {
    return !processEvents.isEmpty();
  }

  /** Get the facts to inject into other aggregates. */
  public List<EventBook> getFacts() {
    return facts;
  }

  /** Check if response has facts. */
  public boolean hasFacts() {
    return !facts.isEmpty();
  }
}
