package tz.tante.ai.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tz.tante.ai.models.TanteAiResponseData;
import tz.tante.ai.models.PropertyManagerApiResponse;
import tz.tante.ai.models.propertymanager.UnitDetailsDTO;
import tz.tante.ai.service.PropertyManagerService;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/v1/chat/property")
public class ChatController
{
  private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
  private final ChatClient chatClient;
  private final PropertyManagerService propertyManagerService;

  public ChatController(ChatClient chatClient, PropertyManagerService propertyManagerService)
  {
    this.chatClient = chatClient;
    this.propertyManagerService = propertyManagerService;
  }

  @Value("classpath:/promptTemplates/systemPromptTemplate.st")
  Resource systemPromptTemplate;

  @GetMapping("/units/assist")
  public ResponseEntity<PropertyManagerApiResponse<TanteAiResponseData>> findProperty(
    @RequestParam String message,
    @RequestParam String customerName,
    @RequestParam Long userAccountId,
    @RequestParam Long unitId
  )
  {
    String verifiedUnitContext = "";

    if (unitId != null)
    {
      try
      {
        UnitDetailsDTO unitDetails = propertyManagerService.getUnitDetails(unitId);
        verifiedUnitContext = """
          Verified unit data from Tante Property Manager:
          %s
          """.formatted(unitDetails);
      }
      catch (Exception exception)
      {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new PropertyManagerApiResponse<>(
            false,
            "Failed to retrieve unit " + unitId + " from the property database.",
            null,
            HttpStatus.SERVICE_UNAVAILABLE.value()
          ));
      }
    }

    try
    {
      String answer = chatClient
        .prompt()
        .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID,userAccountId))
        .system(systemPromptTemplate)
        .user("""
          Customer: %s
          User question: %s
          %s
          """.formatted(customerName, message, verifiedUnitContext))
        .call()
        .content();
      TanteAiResponseData responseData = new TanteAiResponseData(answer, "llm");
      return ResponseEntity.ok(new PropertyManagerApiResponse<>(true, "Request successful", responseData, HttpStatus.OK.value()));
    }
    catch (RuntimeException e)
    {
      logger.error("Chat model error for message '{}'", message, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new PropertyManagerApiResponse<>(
          false,
          "Sorry, I was unable to complete that request. Please try again.",
          null,
          HttpStatus.INTERNAL_SERVER_ERROR.value()
        ));
    }
  }
}
