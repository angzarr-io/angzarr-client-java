package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for projector.feature.
 *
 * <p>Covers projector dispatch: per-event handler invocation, unknown event skipping, domain
 * filtering, and instance reuse across a delivery.
 */
public class ProjectorSteps {

  // ==========================================================================
  // Given Steps - Background
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("a projector {string} consuming domains {string}")
  public void aProjectorConsumingDomains(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the projector handles OrderCreated by appending to a write log")
  public void theProjectorHandlesOrderCreatedByAppendingToAWriteLog() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("Output is the active projector")
  public void outputIsTheActiveProjector() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @When("an EventBook with three OrderCreated events is dispatched")
  public void anEventBookWithThreeOrderCreatedEventsIsDispatched() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("an EventBook mixing OrderCreated and OrderCompleted is dispatched")
  public void anEventBookMixingOrderCreatedAndOrderCompletedIsDispatched() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("an EventBook in domain {string} is dispatched")
  public void anEventBookInDomainIsDispatched(String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("an EventBook with five OrderCreated events is dispatched")
  public void anEventBookWithFiveOrderCreatedEventsIsDispatched() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the write log contains {int} entries")
  public void theWriteLogContainsEntries(int count) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the write log contains only OrderCreated entries")
  public void theWriteLogContainsOnlyOrderCreatedEntries() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the write log remains empty")
  public void theWriteLogRemainsEmpty() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("every entry was appended by the same projector instance")
  public void everyEntryWasAppendedBytheSameProjectorInstance() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
