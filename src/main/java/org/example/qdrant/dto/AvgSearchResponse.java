package org.example.qdrant.dto;

import java.util.List;

public record AvgSearchResponse(
        int k,                    // 요청한 k
        int found,                // 실제로 찾은 개수 (저장된 문서가 k보다 적을 수 있음)
        double avgScore,          // 평균 유사도 (1에 가까울수록 비슷함)
        double avgDistance,       // 평균 거리 (1 - avgScore)
        double threshold,         // 비교에 사용한 임계치
        boolean passed,           // avgScore >= threshold 여부
        List<SearchResponse> neighbors  // 근거가 된 k개 (필요 없으면 제거)
) {}