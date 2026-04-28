package dev.angzarr.client.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import dev.angzarr.Cover;
import dev.angzarr.EventBook;
import dev.angzarr.EventPage;
import dev.angzarr.Projection;
import dev.angzarr.client.annotations.Handles;
import dev.angzarr.client.annotations.Projector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** R13 — ProjectorRouter.dispatch: multi-handler fan-out, one instance per dispatch. */
class ProjectorDispatchTest {

    static final List<String> WRITE_LOG = new ArrayList<>();

    @Projector(name = "prj-output", domains = {"order"})
    public static class OutputProjector {
        public static final AtomicInteger INSTANCES = new AtomicInteger();

        public OutputProjector() {
            INSTANCES.incrementAndGet();
        }

        @Handles(Cover.class)
        public void onCover(Cover evt) {
            WRITE_LOG.add("output:" + evt.getDomain());
        }
    }

    @Projector(name = "prj-second", domains = {"order"})
    public static class SecondProjector {
        @Handles(Cover.class)
        public void onCover(Cover evt) {
            WRITE_LOG.add("second:" + evt.getDomain());
        }
    }

    @Projector(name = "prj-other-domain", domains = {"unrelated"})
    public static class OtherDomainProjector {
        @Handles(Cover.class)
        public void onCover(Cover evt) {
            WRITE_LOG.add("other:" + evt.getDomain());
        }
    }

    private static EventBook bookOf(String domain, int pageCount) {
        EventBook.Builder b =
                EventBook.newBuilder().setCover(Cover.newBuilder().setDomain(domain).build());
        for (int i = 0; i < pageCount; i++) {
            Any packed =
                    Any.newBuilder()
                            .setTypeUrl("type.googleapis.com/angzarr.Cover")
                            .setValue(Cover.newBuilder().setDomain("e" + i).build().toByteString())
                            .build();
            b.addPages(EventPage.newBuilder().setEvent(packed).build());
        }
        return b.build();
    }

    @Test
    void projectorReceivesEventsForMatchingDomain() {
        WRITE_LOG.clear();
        OutputProjector.INSTANCES.set(0);
        ProjectorRouter router =
                (ProjectorRouter)
                        Router.newBuilder("prjs")
                                .withHandler(OutputProjector.class, OutputProjector::new)
                                .build();

        Projection result = router.dispatch(bookOf("order", 2));

        assertThat(WRITE_LOG).containsExactly("output:e0", "output:e1");
        assertThat(result.getCover().getDomain()).isEqualTo("order");
    }

    @Test
    void oneProjectorInstancePerDispatchReusedAcrossEvents() {
        WRITE_LOG.clear();
        OutputProjector.INSTANCES.set(0);
        ProjectorRouter router =
                (ProjectorRouter)
                        Router.newBuilder("prjs")
                                .withHandler(OutputProjector.class, OutputProjector::new)
                                .build();

        router.dispatch(bookOf("order", 3));
        assertThat(OutputProjector.INSTANCES).hasValue(1);
    }

    @Test
    void multipleProjectorsAllInvoked() {
        WRITE_LOG.clear();
        ProjectorRouter router =
                (ProjectorRouter)
                        Router.newBuilder("prjs")
                                .withHandler(OutputProjector.class, OutputProjector::new)
                                .withHandler(SecondProjector.class, SecondProjector::new)
                                .build();

        router.dispatch(bookOf("order", 1));

        assertThat(WRITE_LOG).containsExactly("output:e0", "second:e0");
    }

    @Test
    void projectorSkipsEventsFromOtherDomains() {
        WRITE_LOG.clear();
        ProjectorRouter router =
                (ProjectorRouter)
                        Router.newBuilder("prjs")
                                .withHandler(OtherDomainProjector.class, OtherDomainProjector::new)
                                .build();

        router.dispatch(bookOf("order", 2));

        assertThat(WRITE_LOG).isEmpty();
    }
}
