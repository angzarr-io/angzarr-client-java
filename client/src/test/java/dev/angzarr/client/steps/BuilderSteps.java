package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for builder.feature.
 *
 * <p>Covers handler router build-time validation: empty configurations, unmarked components, mixed
 * handler kinds, duplicate command handlers, and one-shot introspection.
 */
public class BuilderSteps {

  // ==========================================================================
  // Given Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Given("an empty handler configuration")
  public void anEmptyHandlerConfiguration() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a component that has not been marked as a handler kind")
  public void aComponentThatHasNotBeenMarkedAsAHandlerKind() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("a command handler {string} for domain {string} with order state")
  public void aCommandHandlerForDomainWithOrderState(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("another command handler {string} for domain {string} with payment state")
  public void anotherCommandHandlerForDomainWithPaymentState(String name, String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given(
      "two command handlers Alpha and Beta for domain {string} both handling the same command")
  public void twoCommandHandlersAlphaAndBetaForDomainBothHandlingTheSameCommand(String domain) {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Given("the handler reports how many times it has been introspected")
  public void theHandlerReportsHowManyTimesItHasBeenIntrospected() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // When Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @When("I build the router")
  public void iBuildTheRouter() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("I attempt to register it")
  public void iAttemptToRegisterIt() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @When("I register the handler and build the router")
  public void iRegisterTheHandlerAndBuildTheRouter() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // ==========================================================================
  // Then Steps
  // ==========================================================================

  // TODO (WIP): Implement this step matcher properly.
  @Then("the configuration is rejected because no handlers are registered")
  public void theConfigurationIsRejectedBecauseNoHandlersAreRegistered() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the configuration is rejected because the component is not a recognised handler")
  public void theConfigurationIsRejectedBecauseTheComponentIsNotARecognisedHandler() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the result routes commands to their handlers")
  public void theResultRoutesCommandsToTheirHandlers() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the configuration is rejected for mixing handler kinds")
  public void theConfigurationIsRejectedForMixingHandlerKinds() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then(
      "the configuration is rejected because two command handlers share the same domain and command")
  public void theConfigurationIsRejectedBecauseTwoCommandHandlersShareTheSameDomainAndCommand() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the handler has been introspected exactly once")
  public void theHandlerHasBeenIntrospectedExactlyOnce() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }

  // TODO (WIP): Implement this step matcher properly.
  @Then("the result routes saga notifications to their handlers")
  public void theResultRoutesSagaNotificationsToTheirHandlers() {
    throw new io.cucumber.java.PendingException("WIP: step needs implementation");
  }
}
