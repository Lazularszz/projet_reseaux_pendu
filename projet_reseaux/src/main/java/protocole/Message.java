package protocole;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Message du protocole : type, request_id (facultatif), session_id (facultatif), payload (objet JSON).
public final class Message {
    private final String type;
    private final Long requestId;
    private final String sessionId;
    private final ObjectNode payload;

    public Message(String type, Long requestId, String sessionId, ObjectNode payload) {
        this.type = type;
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.payload = (payload == null) ? JsonNodeFactory.instance.objectNode() : payload.deepCopy();
    }

    public String getType() { return type; }
    public Long getRequestId() { return requestId; }
    public String getSessionId() { return sessionId; }
    public ObjectNode getPayload() { return payload.deepCopy(); }

    // ===== Lecture du payload =====

    public String texte(String champ) {
        JsonNode n = payload.get(champ);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }

    public Integer entier(String champ) {
        JsonNode n = payload.get(champ);
        return (n != null && n.isInt()) ? n.asInt() : null;
    }

    // ===== Construction de messages =====

    public static Message welcome(String pseudonyme) {
        ObjectNode p = JsonNodeFactory.instance.objectNode();
        p.put("pseudonym", pseudonyme);
        return new Message(TypeMessage.WELCOME, null, null, p);
    }

    public static Message erreur(Long requestId, String code, String message) {
        ObjectNode p = JsonNodeFactory.instance.objectNode();
        p.put("code", code);
        p.put("message", message);
        return new Message(TypeMessage.ERROR, requestId, null, p);
    }

    @Override
    public String toString() {
        // Jamais de jeton dans les logs
        ObjectNode p = payload.deepCopy();
        if (p.has("token")) p.put("token", "***");
        return type + "[req=" + requestId + ", session=" + sessionId + ", " + p + "]";
    }
}