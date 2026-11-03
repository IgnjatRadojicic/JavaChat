package com.javachat.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deterministic stand in for the Azure embedding model. Each word is hashed into one of 1536
 * slots, so texts that share words end up close together and unrelated texts end up far apart.
 * That is enough to test real pgvector similarity search, including the per user filter.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new Embedding(vector(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    static float[] vector(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (!word.isBlank()) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }

        double length = 0;
        for (float value : vector) {
            length += value * value;
        }
        if (length == 0) {
            vector[0] = 1f;
            return vector;
        }
        float norm = (float) Math.sqrt(length);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
