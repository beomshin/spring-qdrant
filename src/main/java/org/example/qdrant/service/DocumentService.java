package org.example.qdrant.service;

import org.example.qdrant.dto.SearchResponse;
import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentService {

    String createDocument(String document);

    List<Document> searchDocuments(String query, int topK);

    Document getDocument(String id);

    List<SearchResponse> searchDocuments(String content, int topK, Double threshold);
}