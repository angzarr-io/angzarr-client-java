package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for saga.feature.
 *
 * <p>Covers saga dispatch: source-domain events translated into target-domain commands, no-match
 * cases, destination sequence stamping, and multi-target emission.
 */
public class SagaSteps {

  // ==========================================================================
  // Given Steps
  // ==========================================================================

  // Note: "a saga {string} translating from {string} to {string}" is in EditionPropagationSteps.
  // Note: "the saga handles OrderCreated by emitting a ReserveStock command" is in
  // EditionPropagationSteps.

  // TODO (WIP): Implement this step matcher properly.
  @Given("the router is built with the OrderFulfillment saga")
  public void theRouterIsBuiltWithTheOrderFulfillmentSaga() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("destination sequences inventory={int} and fulfillment={int}")
  public void destinationSequencesInventoryAndFulfillment(int inv, int ful) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a saga {string} translating from {string} to {string} and {string}")
  public void aSagaTranslatingFromToAnd(String saga, String source, String target1, String target2) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given(
      "the saga handles OrderCreated by emitting a ReserveStock for {string} and a CreateShipment for {string}")
  public void theSagaHandlesOrderCreatedByEmittingAReserveStockForAndACreateShipmentFor(
      String target1, String target2) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // Note: "an OrderCreated event is dispatched to the saga router" is in MultiHandlerSteps.

  // TODO (WIP): Implement this step matcher properly.
  @When("a StockReserved event is dispatched to the saga router")
  public void aStockReservedEventIsDispatchedToTheSagaRouter() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the response contains exactly one command")
  public void theResponseContainsExactlyOneCommand() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the command targets the {string} domain")
  public void theCommandTargetsTheDomain(String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the response contains no commands")
  public void theResponseContainsNoCommands() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the saga observed destination inventory = {int}")
  public void theSagaObservedDestinationInventory(int seq) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the saga observed destination fulfillment = {int}")
  public void theSagaObservedDestinationFulfillment(int seq) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the ReserveStock command carries destination sequence {int}")
  public void theReserveStockCommandCarriesDestinationSequence(int seq) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the CreateShipment command carries destination sequence {int}")
  public void theCreateShipmentCommandCarriesDestinationSequence(int seq) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
