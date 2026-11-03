package com.javachat.memory;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MemoryService {

    private static final String USER_ID_KEY = "userId";
    private static final int RECALL_LIMIT = 5;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public MemoryService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void remember(Long userId, String fact) {
        Document document = new Document(fact, Map.of(USER_ID_KEY, userId));
        vectorStore.add(List.of(document));
    }

    public List<String> recall(Long userId, String query) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(RECALL_LIMIT)
            .filterExpression(new FilterExpressionBuilder().eq(USER_ID_KEY, userId).build())
            .build();

        return vectorStore.similaritySearch(request).stream()
            .map(Document::getText)
            .toList();
    }

    public List<MemoryResponse> listMemories(Long userId) {
        return jdbcTemplate.query("""
                SELECT id, content
                FROM vector_store
                WHERE (metadata ->> 'userId')::bigint = ?
                ORDER BY content
                """,
            (row, rowNumber) -> new MemoryResponse(row.getObject("id", UUID.class), row.getString("content")),
            userId);
    }

    public boolean forget(Long userId, UUID memoryId) {
        int deletedRows = jdbcTemplate.update("""
                DELETE FROM vector_store
                WHERE id = ? AND (metadata ->> 'userId')::bigint = ?
                """,
            memoryId, userId);
        return deletedRows > 0;
    }

    public void forgetAll(Long userId) {
        jdbcTemplate.update("""
                DELETE FROM vector_store
                WHERE (metadata ->> 'userId')::bigint = ?
                """,
            userId);
    }
}
