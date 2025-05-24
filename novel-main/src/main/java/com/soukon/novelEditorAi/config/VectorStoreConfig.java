//package com.soukon.novelEditorAi.config;
//
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//
//@Configuration
//public class VectorStoreConfig {
//
//    @Bean
//    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
//        return SimpleVectorStore.builder(embeddingModel).build();
//    }
//}