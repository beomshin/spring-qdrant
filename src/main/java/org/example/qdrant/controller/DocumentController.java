package org.example.qdrant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.qdrant.dto.AvgSearchResponse;
import org.example.qdrant.dto.SearchResponse;
import org.example.qdrant.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/create")
    public String createDocument(@RequestBody Map<String, Object> document) {
        log.info("Creating document: {}", document);
        String id = documentService.createDocument((String) document.get("content"));
        return id;
    }

    // GET /search?query=검색어&topK=5
    @GetMapping("/search")
    public List<Document> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK
    ) {
        return documentService.searchDocuments(query, topK);
    }

    // GET /documents/{id}
    @GetMapping("/documents/{id}")
    public Document get(@PathVariable String id) {
        return documentService.getDocument(id);
    }

    // POST /search  {"content": "test", "topK": 3, "threshold": 0.5}
    @PostMapping("/search")
    public List<SearchResponse> search(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        int topK = body.get("topK") == null ? 5 : ((Number) body.get("topK")).intValue();
        Double threshold = body.get("threshold") == null ? null : ((Number) body.get("threshold")).doubleValue();

        return documentService.searchDocuments(content, topK, threshold);
    }

    // POST /search/average  {"content": "test", "k": 5, "threshold": 0.65, "source": "api"}
    @PostMapping("/search/average")
    public AvgSearchResponse searchAverage(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        int k = body.get("k") == null ? 5 : ((Number) body.get("k")).intValue();
        double threshold = body.get("threshold") == null ? 0.65 : ((Number) body.get("threshold")).doubleValue();
        String source = (String) body.get("source");   // 선택값

        return documentService.searchAverage(content, k, threshold, source);
    }

}