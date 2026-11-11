package protocole;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import protocole.exceptions.InvalidMessageException;

// Conversion Message <-> texte JSON, avec validation du format décrit dans PROTOCOLE.md.
// Toute anomalie lève InvalidMessageException (code INVALID_MESSAGE).
public final class Codec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PSEUDO_MAX = 20;

    private Codec() { }

    public static String encoder(Message m) {
        ObjectNode racine = MAPPER.createObjectNode();
        racine.put("type", m.getType());
        if (m.getRequestId() != null) racine.put("request_id", m.getRequestId());
        if (m.getSessionId() != null) racine.put("session_id", m.getSessionId());
        racine.set("payload", m.getPayload());
        try {
            return MAPPER.writeValueAsString(racine);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation impossible", e);
        }
    }

    public static Message decoder(String json) {
        JsonNode racine;
        try {
            racine = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidMessageException("JSON mal formé");
        }
        if (racine == null || !racine.isObject()) {
            throw new InvalidMessageException("Un objet JSON est attendu");
        }

        JsonNode t = racine.get("type");
        if (t == null || !t.isTextual() || t.asText().isEmpty()) {
            throw new InvalidMessageException("Champ 'type' absent ou invalide");
        }
        String type = t.asText();
        if (!TypeMessage.REQUETES.contains(type) && !TypeMessage.REPONSES.contains(type)) {
            throw new InvalidMessageException("Type inconnu : " + type);
        }

        Long requestId = null;
        JsonNode r = racine.get("request_id");
        if (r != null && !r.isNull()) {
            if (!r.isIntegralNumber() || !r.canConvertToLong()) {
                throw new InvalidMessageException("'request_id' doit être un entier");
            }
            requestId = r.asLong();
        }

        String sessionId = null;
        JsonNode s = racine.get("session_id");
        if (s != null && !s.isNull()) {
            if (!s.isTextual() || s.asText().isEmpty()) {
                throw new InvalidMessageException("'session_id' doit être un texte non vide");
            }
            sessionId = s.asText();
        }

        ObjectNode payload = null;
        JsonNode p = racine.get("payload");
        if (p != null && !p.isNull()) {
            if (!p.isObject()) {
                throw new InvalidMessageException("'payload' doit être un objet");
            }
            payload = (ObjectNode) p;
        }

        Message m = new Message(type, requestId, sessionId, payload);
        if (TypeMessage.REQUETES.contains(type)) {
            validerRequete(m);
        }
        return m;
    }

    // Champs obligatoires de chaque requête (section 4.1 de PROTOCOLE.md)
    private static void validerRequete(Message m) {
        switch (m.getType()) {
            case TypeMessage.HELLO -> {
                String pseudo = m.texte("pseudonym");
                if (pseudo == null || pseudo.isBlank() || pseudo.length() > PSEUDO_MAX) {
                    throw new InvalidMessageException(
                            "'pseudonym' doit faire de 1 à " + PSEUDO_MAX + " caractères");
                }
            }
            case TypeMessage.NEW_GAME -> {
                exigerRequestId(m);
                if (m.getPayload().has("players")) {
                    Integer n = m.entier("players");
                    if (n == null || n < 1) {
                        throw new InvalidMessageException("'players' doit être un entier >= 1");
                    }
                }
            }
            case TypeMessage.LETTER -> {
                exigerRequestId(m);
                exigerSessionId(m);
                if (m.texte("letter") == null) {
                    throw new InvalidMessageException("'letter' est obligatoire");
                }
                // Le contenu de 'letter' (une seule lettre) est contrôlé par le métier : INVALID_LETTER
            }
            case TypeMessage.SAVE -> {
                exigerRequestId(m);
                exigerSessionId(m);
            }
            case TypeMessage.RESUME -> {
                exigerSessionId(m);
                String token = m.texte("token");
                if (token == null || token.isEmpty()) {
                    throw new InvalidMessageException("'token' est obligatoire");
                }
            }
            default -> { }
        }
    }

    private static void exigerRequestId(Message m) {
        if (m.getRequestId() == null) throw new InvalidMessageException("'request_id' est obligatoire");
    }

    private static void exigerSessionId(Message m) {
        if (m.getSessionId() == null) throw new InvalidMessageException("'session_id' est obligatoire");
    }
}