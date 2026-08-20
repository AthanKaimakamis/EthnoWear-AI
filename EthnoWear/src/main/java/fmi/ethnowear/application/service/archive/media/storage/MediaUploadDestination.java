package fmi.ethnowear.application.service.archive.media.storage;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

public final class MediaUploadDestination {

    private final String fileDirectory;
    private final String thumbnailDirectory;

    private MediaUploadDestination(String fileDirectory, String thumbnailDirectory) {
        this.fileDirectory = fileDirectory;
        this.thumbnailDirectory = thumbnailDirectory;
    }

    @Contract("_ -> new")
    public  static @NonNull MediaUploadDestination documentOriginal(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "documents/" + documentId + "/original",
                "documents/" + documentId + "/thumbnails"
        );
    }

    @Contract("_ -> new")
    public static @NonNull MediaUploadDestination captureOriginal(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "captures/" + documentId + "/original",
                "captures/" + documentId + "/thumbnails"
        );
    }

    @Contract("_ -> new")
    public static @NonNull MediaUploadDestination documentPages(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "documents/" + documentId + "/pages",
                "documents/" + documentId + "/thumbnails"
        );
    }

    @Contract(value = "_ -> new", pure = true)
    static @NonNull MediaUploadDestination generic(String category) {
        return new MediaUploadDestination(category, "thumbnails");
    }

    String fileDirectory() {
        return fileDirectory;
    }

    String thumbnailDirectory() {
        return thumbnailDirectory;
    }
}
