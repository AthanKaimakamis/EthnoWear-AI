package fmi.ethnowear.application.service.archive.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class KnowledgeContentHasher {

    public String hash(String content) {
        if (content == null)
            throw new IllegalArgumentException("Knowledge content is required");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] hashBytes = digest.digest(contentBytes);

            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
