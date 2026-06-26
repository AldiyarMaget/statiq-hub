package kz.bns.hub.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfiguration {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String user;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${app.vector-store.table-name:statiq_embeddings}")
    private String tableName;

    @Value("${app.vector-store.dimension:384}")
    private Integer dimension;

    @Value("${app.vector-store.create-table:true}")
    private Boolean createTable;

    @Value("${app.vector-store.drop-table-first:false}")
    private Boolean dropTableFirst;

    @Bean
    public EmbeddingStore<TextSegment> pgvectorEmbeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(getHost(url))
                .port(getPort(url))
                .database(getDatabase(url))
                .user(user)
                .password(password)
                .table(tableName)
                .dimension(dimension)
                .useIndex(true)
                .indexListSize(100)
                .createTable(createTable)
                .dropTableFirst(dropTableFirst)
                .build();
    }

    private String getHost(String url) {
        return url.split("://")[1].split(":")[0];
    }

    private Integer getPort(String url) {
        return Integer.parseInt(url.split("://")[1].split(":")[1].split("/")[0]);
    }

    private String getDatabase(String url) {
        return url.split("://")[1].split("/")[1].split("\\?")[0];
    }
}
