package kz.bns.hub.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocalIntentRouterTest {

    private LocalIntentRouter router;

    @BeforeEach
    public void setUp() {
        EmbeddingModel embeddingModel = new E5SmallV2QuantizedEmbeddingModel();
        router = new LocalIntentRouter(embeddingModel);
        ReflectionTestUtils.setField(router, "queryPrefix", "query:");
        ReflectionTestUtils.setField(router, "routingThreshold", 0.75);
        router.init();
    }

    @Test
    public void testRouteBdapPackage() {
        UserIntent intent = router.route("У меня висит статус 010, как отправить отчет дальше?");
        assertEquals(UserIntent.BDAP_PACKAGES, intent);
    }

    @Test
    public void testRouteRolesAccess() {
        UserIntent intent = router.route("У кого запросить доступ к EStatMETA?");
        assertEquals(UserIntent.ROLES_AND_ACCESS, intent);
    }
}
