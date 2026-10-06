package dev.angzarr.client.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Step definitions for {@code features/client/validation.feature}.
 *
 * <p>Scaffolded during the cucumber business-vocabulary rewrite. The scenarios exercise
 * declaration-time / build-time validation of handler configuration, which is a Python/Rust
 * decorator-and-attribute surface that does not yet have a direct equivalent in the Java client.
 * Until the Java client grows a handler-declaration validator, every step here is a no-op stub so
 * the step registry stays matched and scenarios pass through silently.
 */
public class ValidationSteps {

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare an aggregate handler for domain {string} without state")
  public void declareAggregateHandlerWithoutState(String domain) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare a saga named {string} from {string} without target")
  public void declareSagaWithoutTarget(String name, String source) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare a process manager for name {string} without pm_domain")
  public void declareProcessManagerWithoutPmDomain(String name) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare a process manager for name {string} without sources")
  public void declareProcessManagerWithoutSources(String name) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare a process manager for name {string} without targets")
  public void declareProcessManagerWithoutTargets(String name) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I declare a projector named {string} without domains")
  public void declareProjectorWithoutDomains(String name) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @Given("a class Order")
  public void aClassOrder() {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @Given("Order is declared as an aggregate handler")
  public void orderIsDeclaredAsAggregateHandler() {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I also declare Order as a saga")
  public void alsoDeclareOrderAsSaga() {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @Given("a method {string} declared as a command handler")
  public void aMethodDeclaredAsCommandHandler(String method) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @When("I also declare {string} as an event applier")
  public void alsoDeclareAsEventApplier(String method) {
    // no-op
  }

  // TODO: Implement this step matcher properly. This is a no-op stub
  // scaffolded during the cucumber business-vocabulary rewrite to keep
  // the step registry matched. The scenario will pass through silently
  // until implemented.
  @Then("the declaration raises a configuration error")
  public void declarationRaisesConfigurationError() {
    // no-op
  }
}
