package org.example.qdrant.dto;

public record SearchResponse(
        String id,
        String content,
        Double score,     // Cosine이면 유사도 (1에 가까울수록 비슷함)
        Double distance   // 1 - score (0에 가까울수록 가까움)
) {}