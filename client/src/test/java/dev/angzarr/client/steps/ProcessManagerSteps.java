package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for process_manager.feature.
 *
 * <p>Covers process manager dispatch: receiving triggers, emitting commands, rebuilding state from
 * process events, and skipping events outside declared sources.
 */
public class ProcessManagerSteps {

  // ==========================================================================
  // Given Steps - Background
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("a process manager {string} for the fulfillment domain")
  public void aProcessManagerForTheFulfillmentDomain(String name) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the PM sources from {string} and {string}")
  public void thePMSourcesFromAnd(String source1, String source2) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the PM targets {string}")
  public void thePMTargets(String target) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the PM tracks the number of orders seen")
  public void thePMTracksTheNumberOfOrdersSeen() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("OrderCompleted advances the orders-seen count")
  public void orderCompletedAdvancesTheOrdersSeenCount() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the PM handles OrderCreated by emitting a ReserveStock command")
  public void thePMHandlesOrderCreatedByEmittingAReserveStockCommand() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Fulfillment is the active process manager")
  public void fulfillmentIsTheActiveProcessManager() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Given Steps - State setup
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("process state events: OrderCompleted, OrderCompleted")
  public void processStateEventsOrderCompletedOrderCompleted() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // Note: "an OrderCreated trigger is dispatched to the PM router" is in MultiHandlerSteps.

  // TODO (WIP): Implement this step matcher properly.
  @When("a StockReserved trigger with a domain outside sources is dispatched")
  public void aStockReservedTriggerWithADomainOutsideSourcesIsDispatched() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the PM has seen {int} completed orders")
  public void thePMHasSeenCompletedOrders(int count) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // Note: "the response contains exactly one command" is in SagaSteps.
  // Note: "the response contains no commands" is in SagaSteps.
}
