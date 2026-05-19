package dev.angzarr.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.angzarr.AngzarrDeferredSequence;
import dev.angzarr.CommandBook;
import dev.angzarr.CommandPage;
import dev.angzarr.Cover;
import dev.angzarr.PageHeader;
import dev.angzarr.client.error_codes.Codes;
import dev.angzarr.client.error_codes.Keys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Audit findings for {@link Destinations}:
 *
 * <ul>
 *   <li>P2.2: insertion-order preservation.
 *   <li>#31: {@code deferredHeader} static helper.
 *   <li>#64: {@code stampCommand} on unknown domain raises with {@code
 *       MISSING_DESTINATION_SEQUENCE} code and {@code details["domain"]}.
 * </ul>
 */
class DestinationsParityTest {

  @Test
  void domainsPreserveInsertionOrder() {
    // P2.2: cucumber `destinations.feature` pins insertion order.
    Map<String, Integer> seq = new LinkedHashMap<>();
    seq.put("z-third", 30);
    seq.put("a-second", 20);
    seq.put("m-first", 10);
    var destinations = new Destinations(seq);
    assertThat(destinations.domains()).containsExactly("z-third", "a-second", "m-first");
  }

  @Test
  void stampCommandUnknownDomainRaisesStructuredError() {
    // Audit #64: error code MISSING_DESTINATION_SEQUENCE, message
    // "no sequence for destination domain", details["domain"]=<domain>.
    var destinations = new Destinations(Map.of("inventory", 5));
    CommandBook cmd =
        CommandBook.newBuilder()
            .setCover(Cover.newBuilder().setDomain("orders").build())
            .addPages(CommandPage.newBuilder().build())
            .build();

    assertThatThrownBy(() -> destinations.stampCommand(cmd, "fulfillment"))
        .isInstanceOf(Errors.InvalidArgumentError.class)
        .hasMessage("no sequence for destination domain")
        .satisfies(
            e -> {
              var ce = (Errors.ClientError) e;
              assertThat(ce.getCode()).isEqualTo(Codes.MISSING_DESTINATION_SEQUENCE);
              assertThat(ce.getDetails()).containsEntry(Keys.DOMAIN, "fulfillment");
            });
  }

  @Test
  void deferredHeaderCarriesSourceAndSeq() {
    // Audit #31: build a PageHeader carrying AngzarrDeferredSequence.
    Cover source = Cover.newBuilder().setDomain("orders").build();
    PageHeader header = Destinations.deferredHeader(source, 7L);

    assertThat(header.hasAngzarrDeferred()).isTrue();
    AngzarrDeferredSequence d = header.getAngzarrDeferred();
    assertThat(d.getSourceSeq()).isEqualTo(7L);
    assertThat(d.getSource().getDomain()).isEqualTo("orders");
  }

  @Test
  void hasDomainAliasMatchesContains() {
    var destinations = new Destinations(Map.of("a", 1, "b", 2));
    assertThat(destinations.hasDomain("a")).isTrue();
    assertThat(destinations.hasDomain("missing")).isFalse();
  }
}
