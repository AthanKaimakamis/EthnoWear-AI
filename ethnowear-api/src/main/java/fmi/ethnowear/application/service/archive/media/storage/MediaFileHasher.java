package fmi.ethnowear.application.service.archive.media.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class MediaFileHasher {

    public String sha256(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input);
        } catch(IOException ex) {
            throw new IllegalStateException("Could not hash media file", ex);
        }
    }

    public String sha256(InputStream input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch(IOException ex) {
            throw new IllegalStateException("Could not hash media content", ex);
        } catch(NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}