package org.example.qdrant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.qdrant.dto.SearchResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final VectorStore vectorStore;

    @Override
    public String createDocument(String content) {
        String id = UUID.randomUUID().toString();

        Document document = new Document(id, content, Map.of("source", "api"));
        vectorStore.add(List.of(document));

        return id; // 생성된 ID를 반환해야 이후에 단건 조회 가능
    }

    @Override
    public List<Document> searchDocuments(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }

    @Override
    public Document getDocument(String id) {
        // Qdrant는 doc_id를 페이로드에 저장하므로 필터로 조회
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("*")
                        .topK(1)
                        .filterExpression(new FilterExpressionBuilder().eq("doc_id", id).build())
                        .build()
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<SearchResponse> searchDocuments(String content, int topK, Double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(content)   // 이 content가 임베딩되어 k-NN 기준 벡터가 됨
                .topK(topK);      // 가까운 k개

        if (threshold != null) {
            builder.similarityThreshold(threshold); // 이 점수 이상만 반환 (0.0~1.0)
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());

        return results.stream()
                .map(doc -> {
                    Double score = doc.getScore();
                    Double distance = score == null ? null : 1.0 - score;
                    return new SearchResponse(doc.getId(), doc.getText(), score, distance);
                })
                .toList();
    }

}