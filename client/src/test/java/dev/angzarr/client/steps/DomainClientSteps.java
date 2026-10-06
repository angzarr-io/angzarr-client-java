package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for domain-client.feature.
 *
 * <p>Covers unified domain access: a single entry point exposing both query and command surfaces
 * over one connection. Earlier DomainClient steps live in ClientConnectivitySteps; the steps below
 * cover newer contract phrasings.
 */
public class DomainClientSteps {

  // ==========================================================================
  // Given Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("a connected domain client")
  public void aConnectedDomainClient() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @When("I create a domain client for the coordinator endpoint")
  public void iCreateADomainClientForTheCoordinatorEndpoint() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("I create a domain client for domain {string}")
  public void iCreateADomainClientForDomain(String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("I create a domain client from environment variable {string}")
  public void iCreateADomainClientFromEnvironmentVariable(String envVar) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("I close the domain client")
  public void iCloseTheDomainClient() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the domain client should be connected")
  public void theDomainClientShouldBeConnected() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("subsequent commands should fail with a connection error")
  public void subsequentCommandsShouldFailWithAConnectionError() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("subsequent queries should fail with a connection error")
  public void subsequentQueriesShouldFailWithAConnectionError() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("I should receive a command response")
  public void iShouldReceiveACommandResponse() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("I should receive {int} event pages")
  public void iShouldReceiveEventPages(int count) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
