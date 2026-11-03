package com.javachat.memory;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MemoryService {

    private static final String USER_ID_KEY = "userId";
    private static final int RECALL_LIMIT = 5;

    private final VectorStore vectorStore;

    public MemoryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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

}