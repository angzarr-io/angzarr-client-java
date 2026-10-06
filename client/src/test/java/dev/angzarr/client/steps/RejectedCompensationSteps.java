package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for rejected_compensation.feature.
 *
 * <p>Covers compensation routing details: state rebuild before compensation, (source_domain,
 * command)-pair routing on multi-handler classes, sequence stamping after prior history, and the
 * empty-handler no-op case.
 */
public class RejectedCompensationSteps {

  // ==========================================================================
  // Given Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("a command handler {string} for domain {string} with stateful rejection")
  public void aCommandHandlerForDomainWithStatefulRejection(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("deposits update Payment's bankroll")
  public void depositsUpdatePaymentsBankroll() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given(
      "Payment compensates a rejected ReserveStock from inventory by emitting FundsReleased with the current bankroll")
  public void
      paymentCompensatesARejectedReserveStockFromInventoryByEmittingFundsReleasedWithTheCurrentBankroll() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment is configured")
  public void paymentIsConfigured() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a prior history with a FundsDeposited event of bankroll {int}")
  public void aPriorHistoryWithAFundsDepositedEventOfBankroll(int amount) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a command handler {string} for domain {string} with two compensation handlers")
  public void aCommandHandlerForDomainWithTwoCompensationHandlers(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment compensates a rejected ReserveStock from inventory by emitting FundsReleased")
  public void paymentCompensatesARejectedReserveStockFromInventoryByEmittingFundsReleased() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment compensates a rejected ProcessPayment from payment by emitting WorkflowFailed")
  public void paymentCompensatesARejectedProcessPaymentFromPaymentByEmittingWorkflowFailed() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given(
      "Payment compensates a rejected ReserveStock from inventory by emitting two FundsReleased events")
  public void
      paymentCompensatesARejectedReserveStockFromInventoryByEmittingTwoFundsReleasedEvents() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a prior history ending at sequence {int}")
  public void aPriorHistoryEndingAtSequence(int seq) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a command handler {string} for domain {string} with no rejection handlers")
  public void aCommandHandlerForDomainWithNoRejectionHandlers(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // Note: "a rejection of ReserveStock arrives from inventory" is in RejectionSteps.

  // TODO (WIP): Implement this step matcher properly.
  @When("a rejection of ProcessPayment arrives from payment")
  public void aRejectionOfProcessPaymentArrivesFromPayment() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("a rejection of CreateShipment arrives from fulfillment")
  public void aRejectionOfCreateShipmentArrivesFromFulfillment() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the response contains one FundsReleased event")
  public void theResponseContainsOneFundsReleasedEvent() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the FundsReleased event carries amount {int}")
  public void theFundsReleasedEventCarriesAmount(int amount) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the response contains one WorkflowFailed event")
  public void theResponseContainsOneWorkflowFailedEvent() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("no FundsReleased event is emitted")
  public void noFundsReleasedEventIsEmitted() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the response contains no events")
  public void theResponseContainsNoEvents() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("compensation events are appended after sequence {int}, taking sequences {int} and {int}")
  public void compensationEventsAreAppendedAfterSequenceTakingSequencesAnd(
      int prior, int s1, int s2) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
