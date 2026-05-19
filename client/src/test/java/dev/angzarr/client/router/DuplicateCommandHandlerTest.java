package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.Cover;
import dev.angzarr.client.annotations.Aggregate;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import dev.angzarr.client.error_codes.Messages;
import org.junit.jupiter.api.Test;

/**
 * Audit #18 / Python {@code 7efab04}, Rust {@code e236307}: multi-handler CommandHandler dispatch
 * is FORBIDDEN.
 *
 * <p>When a Router is built with two {@code @Aggregate}s that both claim the same {@code (domain,
 * type_url)} pair, {@link Router#build()} must throw {@link BuildException} carrying:
 *
 * <ul>
 *   <li>{@code code = DUPLICATE_COMMAND_HANDLER}
 *   <li>{@code message = "duplicate command handler registration for (domain, type_url)"}
 *   <li>{@code details["domain"] = "<domain>"}, {@code details["type_url"] = "<type_url>"}, {@code
 *       details["router_name"] = "<name>"}
 * </ul>
 *
 * <p>Saga / PM / projector fan-out is unaffected (those kinds legitimately broadcast).
 */
class DuplicateCommandHandlerTest {

  public static class S1 {}

  public static class S2 {}

  @Aggregate(domain = "shared", state = S1.class)
  public static class HandlerOne {
    @Handles(Cover.class)
    public Cover handle(Cover cmd, S1 state, long seq) {
      return cmd;
    }
  }

  @Aggregate(domain = "shared", state = S2.class)
  public static class HandlerTwo {
    @Handles(Cover.class)
    public Cover handle(Cover cmd, S2 state, long seq) {
      return cmd;
    }
  }

  @Aggregate(domain = "other", state = S1.class)
  public static class HandlerOther {
    @Handles(Cover.class)
    public Cover handle(Cover cmd, S1 state, long seq) {
      return cmd;
    }
  }

  @Test
  void buildRejectsTwoHandlersWithSameDomainAndType() {
    assertThatThrownBy(
            () ->
                Router.newBuilder("agg")
                    .withHandler(HandlerOne.class, HandlerOne::new)
                    .withHandler(HandlerTwo.class, HandlerTwo::new)
                    .build())
        .isInstanceOf(BuildException.class)
        .satisfies(
            t -> {
              BuildException be = (BuildException) t;
              assertThat(be.getCode()).isEqualTo(Codes.DUPLICATE_COMMAND_HANDLER);
              assertThat(be.getMessage()).isEqualTo(Messages.DUPLICATE_COMMAND_HANDLER);
              assertThat(be.getDetails())
                  .containsEntry(Keys.DOMAIN, "shared")
                  .containsEntry(Keys.ROUTER_NAME, "agg");
              assertThat(be.getDetails().get(Keys.TYPE_URL)).endsWith("angzarr.Cover");
            });
  }

  @Test
  void buildAllowsTwoHandlersInDifferentDomains() {
    // Cross-domain — fan-out remains legal (audit #18 only forbids same
    // (domain, type_url) pair).
    var built =
        Router.newBuilder("agg")
            .withHandler(HandlerOne.class, HandlerOne::new)
            .withHandler(HandlerOther.class, HandlerOther::new)
            .build();
    assertThat(built).isInstanceOf(CommandHandlerRouter.class);
  }
}
