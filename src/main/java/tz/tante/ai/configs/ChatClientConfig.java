package tz.tante.ai.configs;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tz.tante.ai.tools.PropertyManagerTools;

import java.util.List;

@Configuration
public class ChatClientConfig
{
  @Bean
  public ChatClient.Builder chatClientBuilder(OpenAiChatModel openAiChatModel)
  {
    return ChatClient.builder(openAiChatModel)
      .defaultSystem("""
        You are a helpful assistant that provides information about properties.
        For property questions (especially rent, availability, unit details), use internal tools to get figures and facts.
        If the user message includes a unit identifier (e.g., "unit id 1", "id 1", "unit #1"), call tool getUnitDetails with that id before answering.
        Do not ask for confirmation when a unit id is already provided; retrieve and answer directly.
        Never use placeholder tokens (for example "[Unit type]" or "[rent]").
        When tool data is available, answer with exact values from the tool result. If a field is missing, say it is unavailable.
        For now you are limited to tanzania context, and you should not provide information about other countries.
        If you don't know the answer to a question, please respond with "I don't know."
        """);

  }

  @Bean
  public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
                               RetrievalAugmentationAdvisor retrievalAugmentationAdvisor, PropertyManagerTools propertyManagerTools)
  {
    Advisor memoryAdvisor =MessageChatMemoryAdvisor.builder(chatMemory).build();
    return chatClientBuilder
      .defaultAdvisors(List.of(memoryAdvisor, retrievalAugmentationAdvisor))
      .defaultTools(propertyManagerTools)
      .build();
  }

  @Bean
  public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository)
  {
    return MessageWindowChatMemory.builder()
      .maxMessages(50)
      .chatMemoryRepository(chatMemoryRepository)
      .build();
  }

  @Bean
  public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore)
  {
    return RetrievalAugmentationAdvisor
      .builder()
      .documentRetriever(
        VectorStoreDocumentRetriever
          .builder()
          .vectorStore(vectorStore)
          .topK(3)
          .similarityThreshold(0.5)
          .build()
      )
      .build();
  }

}
