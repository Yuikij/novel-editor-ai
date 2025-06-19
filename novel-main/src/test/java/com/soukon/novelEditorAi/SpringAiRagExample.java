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
    void searchRagById() {
        SearchRequest request = SearchRequest.builder()

                .filterExpression("id == 'doc-1750351730499-chunk-0'")
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
        ragService.createDocument("" +
                "法兰西共和国（法语：La République française），简称法国，首都巴黎，位于欧洲西部，与比利时、卢森堡、德国、瑞士、意大利、摩纳哥、西班牙、安道尔接壤，西北隔英吉利海峡与英国相望，海洋性、大陆性、地中海型和山地气候并存。地势东南高西北低。总面积550000平方千米（不含海外领地），海岸线2700千米，陆地线2800千米，本土划为13个大区、94个省。截至2025年3月，法国人口为6640万人， [52]主要为法兰西民族，大多信奉天主教，官方语言为法语。 [1]\n" +
                "法国古称“高卢”，5世纪，法兰克人移居到这里，建立法兰克王国。10~14世纪，卡佩王朝统治时期改称法兰西王国。1789年7月14日，爆发法国大革命，发表《人权宣言》，废除君主制。1792年，建立第一共和国。此后历经拿破仑建立的第一帝国、波旁王朝复辟、七月王朝、第二共和国、第二帝国、第三共和国。1871年3月，巴黎人民武装起义，成立巴黎公社。第一次世界大战中，法国参加协约国，对同盟国作战获胜。第二次世界大战期间遭到德国入侵，戴高乐将军组织了反法西斯的“自由法国”运动，1944年8月解放巴黎。1946年10月，法兰西第四共和国成立。1958年，第五共和国成立。 [1]\n" +
                "法国是最发达的工业国家之一，在核电、航空、航天和铁路方面居世界领先地位，钢铁、汽车和建筑业为三大工业支柱。是联合国安全理事会常任理事国、欧盟创始国及北约成员国；是联合国教科文组织、国际刑警组织、经合组织、欧洲议会等国际和地区组织总部所在地。" +
                "" +
                "",metadata);
    }
}
