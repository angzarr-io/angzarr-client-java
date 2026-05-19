package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.Any;
import dev.angzarr.BusinessResponse;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.ContextualCommand;
import dev.angzarr.Cover;
import dev.angzarr.Notification;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Applies;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.error_codes.Codes;
import org.junit.jupiter.api.Test;

/**
 * Audit #18 / Python {@code 7efab04}, Rust {@code e236307}: multi-handler CommandHandler dispatch
 * is FORBIDDEN.
 *
 * <p>Build-time enforcement now blocks two {@code @Aggregate}s claiming the same {@code (domain,
 * type_url)} pair (see {@link DuplicateCommandHandlerTest}). This file pins the dispatch behavior
 * of legitimately-distinct handlers (single matching handler per command) and the build-time
 * refusal of the forbidden shape.
 */
class MultiHandlerTest {

  public static class State {
    public int applied;
  }

  @Aggregate(domain = "shared", state = State.class)
  public static class HandlerA {
    @Applies(Cover.class)
    public void onCover(State s, Cover evt) {
      s.applied += 1;
    }

    @Handles(Cover.class)
    public Notification handle(Cover cmd, State s, long seq) {
      return Notification.newBuilder()
          .setCover(Cover.newBuilder().setDomain("A@" + seq + ",applied=" + s.applied))
          .build();
    }
  }

  @Aggregate(domain = "shared", state = State.class)
  public static class HandlerB {
    @Applies(Cover.class)
    public void onCover(State s, Cover evt) {
      s.applied += 100;
    }

    @Handles(Cover.class)
    public Notification handle(Cover cmd, State s, long seq) {
      return Notification.newBuilder()
          .setCover(Cover.newBuilder().setDomain("B@" + seq + ",applied=" + s.applied))
          .build();
    }
  }

  private static ContextualCommand cmdFor(String domain) {
    Any packed =
        Any.newBuilder()
            .setTypeUrl("type.googleapis.com/angzarr_client.proto.angzarr.Cover")
            .setValue(Cover.newBuilder().setDomain(domain).build().toByteString())
            .build();
    CommandBook book =
        CommandBook.newBuilder()
            .setCover(Cover.newBuilder().setDomain(domain).build())
            .addPages(CommandPage.newBuilder().setCommand(packed).build())
            .build();
    return ContextualCommand.newBuilder().setCommand(book).build();
  }

  @Test
  void buildRefusesTwoCommandHandlersForSameDomainAndType() {
    // Audit #18: build-time refusal — most expressive cross-language
    // failure mode, mirrors Python `RouterBuildError` and Rust
    // `BuildError::DuplicateCommandHandler`.
    assertThatThrownBy(
            () ->
                Router.newBuilder("agg")
                    .withHandler(HandlerA.class, HandlerA::new)
                    .withHandler(HandlerB.class, HandlerB::new)
                    .build())
        .isInstanceOf(BuildException.class)
        .satisfies(
            t ->
                assertThat(((BuildException) t).getCode())
                    .isEqualTo(Codes.DUPLICATE_COMMAND_HANDLER));
  }

  @Test
  void singleHandlerDispatchesToTheOneMatch() throws Exception {
    CommandHandlerRouter<?> router =
        (CommandHandlerRouter<?>)
            Router.newBuilder("agg").withHandler(HandlerA.class, HandlerA::new).build();
    BusinessResponse resp = router.dispatch(cmdFor("shared"));

    assertThat(resp.getEvents().getPagesCount()).isEqualTo(1);
    Notification first = Notification.parseFrom(resp.getEvents().getPages(0).getEvent().getValue());
    assertThat(first.getCover().getDomain()).startsWith("A@");
  }
}
