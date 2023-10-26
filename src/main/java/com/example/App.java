package com.example;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.Files;
import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.servererrors.QueryExecutionException;
import com.datastax.oss.driver.api.core.servererrors.QueryValidationException;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;

public class App 
{
    public static void PRINT(String what){
        System.out.println(what);
    }
    public static JSONObject parseJSONFile(String filename) throws JSONException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(filename)));
        return new JSONObject(content);
    }
    public static ResultSet annQuery(List<Double> embeddings,CqlSession session,String keyspace,String table,String vector_column,String k_value) 
        throws AllNodesFailedException, QueryExecutionException, QueryValidationException {
            ResultSet rs = session.execute(
                "SELECT * FROM " +
                keyspace + "." + table + " ORDER BY " + vector_column + " ANN OF " +
                embeddings.toString() +
                " LIMIT " + k_value
            );
            return rs;
    }
    public static List<Double> getEmbeddings(List<String> text,OpenAiService api) throws Exception {
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel("text-embedding-ada-002");
        request.setUser("Colvin");
        request.setInput(text);
        return api.createEmbeddings(request).getData().get(0).getEmbedding();
    }
    public static CqlSession getSession(JSONObject auth) throws Exception {
        CqlSession session = null;
        try {
            session = CqlSession.builder()
                .withCloudSecureConnectBundle(Paths.get("/Users/james.colvin/Downloads/secure-connect-vector-demos.zip"))
                .withAuthCredentials(auth.getString("clientId"),auth.getString("secret"))
                .build();
        } catch (Exception e) {
            System.out.println(e);
        }
        return session;
    }
    public static void insertChat(String message,String role,CqlSession session){
        String timeuuid = Uuids.timeBased().toString();
        session.execute("INSERT INTO live.message_store (partition_id, row_id, role, body_blob) VALUES ('adventure',"+
            timeuuid+",'"+role+"',"+"'"+message+"');"
        );
    }
    public static List<ChatCompletionChoice> aiCompletionRequest(List<ChatMessage> messages,OpenAiService api){
        //write a loop that asks for input, then queries Astra and adds messages before another completion request
        //need to write the response back to Astra, then pull the whole message history into each completion request
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
            .builder()
            .model("gpt-3.5-turbo")
            .messages(messages)
            .n(1)
            // .maxTokens(50)
            .logitBias(new HashMap<>())
            .build();
        
        ChatCompletionResult completion = api.createChatCompletion(chatCompletionRequest);
        return completion.getChoices();
    }

    public static void main(String[] args) throws JSONException, IOException {
        JSONObject auth =  parseJSONFile("/Users/james.colvin/Downloads/vector-demos-token.json");      
        OpenAiService aiService = new OpenAiService(auth.getString("openai"), Duration.ofSeconds(30));
        // String question = "When was the college of engineering in the University of Notre Dame established?";
        List<String> textForEmbedding = new ArrayList<>();
        Scanner prompt = new Scanner(System.in);
        List<ChatMessage> messages = new ArrayList<>();
        StringBuffer user_input = new StringBuffer(256);
        try {
            CqlSession session = getSession(auth);
            PRINT("What is your question?");
            user_input.append(prompt.nextLine().toLowerCase());
            while (!user_input.toString().equals("thank you")) {
                String question = user_input.toString();
                user_input.delete(0, user_input.length());
                textForEmbedding.clear();
                messages.clear();
                messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),"You're a chatbot helping customers with questions. Be informative in your responses"));
                messages.add(new ChatMessage(ChatMessageRole.USER.value(),question));
                textForEmbedding.add(question);
                List<Double> embeddings = getEmbeddings(textForEmbedding, aiService);
                ResultSet rs = annQuery(embeddings, session, "live", "squad", "title_context_embedding", "3");
                rs.forEach((Row r) ->{messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), r.getString("context")));});
                List<ChatCompletionChoice> responses = aiCompletionRequest(messages, aiService);
                responses.forEach((ChatCompletionChoice c) -> {PRINT(c.getMessage().getContent());});
                PRINT("Do you have any other questions?");
                user_input.append(prompt.nextLine().toLowerCase());
            }




        } catch (Exception e) {
            System.out.println(e);
        }
        prompt.close();
        System.exit(0);
        
    }
}




// String augmentLLM = "{\"data\": {\"additional_kwargs\": {},\"content\": [\""+r1+"\",\""+r2+"\",\""+r3+"\"],\"example\": true,\"type\": \"ai\"},\"type\": \"ai\"}";
// System.out.println(augmentLLM);
// public static String replaceCharacter(String input){
//         String input1 = input.replace("\'","\'\'");
//         String input2 = input1.replace("\"","\\\"");
//         return input2;
// }