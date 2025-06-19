package com.soukon.novelEditorAi;


import com.soukon.novelEditorAi.service.impl.RagServiceImpl;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class SpringAiRagExample {

    @Autowired
    private RagServiceImpl ragService;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void searchRag() {
        SearchRequest request = SearchRequest.builder()
                .filterExpression("source == 'sample.txt'")
                .topK(2).query("法国首都在哪").build();
        List<Document> documents = vectorStore.similaritySearch(request);
        System.out.println(documents);
    }


    @Test
    void delRag() {
        vectorStore.delete("source == 'sample.txt'");
    }

    @Test
    void addRag() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "sample.txt");
        ragService.createDocument("",metadata);
    }
}
