package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for rejection.feature.
 *
 * <p>Covers basic rejection compensation: a matching rejection fires the registered compensator;
 * unmatched rejections are silently ignored; multiple compensators for the same rejection all run.
 */
public class RejectionSteps {

  // ==========================================================================
  // Given Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment is a component in domain {string}")
  public void paymentIsAComponentInDomain(String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment compensates a rejected ReserveStock from inventory by releasing funds")
  public void paymentCompensatesARejectedReserveStockFromInventoryByReleasingFunds() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment is the active component")
  public void paymentIsTheActiveComponent() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a second compensation handler for the same rejection also releases funds")
  public void aSecondCompensationHandlerForTheSameRejectionAlsoReleasesFunds() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Payment then Payment2 are configured")
  public void paymentThenPayment2AreConfigured() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @When("a rejection of ReserveStock arrives from inventory")
  public void aRejectionOfReserveStockArrivesFromInventory() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("a rejection of ProcessPayment arrives from inventory")
  public void aRejectionOfProcessPaymentArrivesFromInventory() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("a FundsReleased event is emitted")
  public void aFundsReleasedEventIsEmitted() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("no events are emitted")
  public void noEventsAreEmitted() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("two FundsReleased events are emitted in registration order")
  public void twoFundsReleasedEventsAreEmittedInRegistrationOrder() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
