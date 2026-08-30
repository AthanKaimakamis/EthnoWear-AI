package fmi.ethnowear.application.service.archive.media.storage;

import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

public final class MediaUploadDestination {

    private final String fileDirectory;
    private final String thumbnailDirectory;
    private final MediaOrigin origin;
    private final MediaRetentionPolicy retentionPolicy;

    private MediaUploadDestination(
            String fileDirectory,
            String thumbnailDirectory,
            MediaOrigin origin,
            MediaRetentionPolicy retentionPolicy
    ) {
        this.fileDirectory = fileDirectory;
        this.thumbnailDirectory = thumbnailDirectory;
        this.origin = origin;
        this.retentionPolicy = retentionPolicy;
    }

    @Contract("_ -> new")
    public  static @NonNull MediaUploadDestination documentOriginal(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "documents/" + documentId + "/original",
                "documents/" + documentId + "/thumbnails",
                MediaOrigin.DOCUMENT_ORIGINAL,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }


    @Contract("_ -> new")
    public static @NonNull MediaUploadDestination captureOriginal(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "captures/" + documentId + "/original",
                "captures/" + documentId + "/thumbnails",
                MediaOrigin.DOCUMENT_ORIGINAL,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }

    @Contract("_ -> new")
    public static @NonNull MediaUploadDestination documentPages(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "documents/" + documentId + "/pages",
                "documents/" + documentId + "/thumbnails",
                MediaOrigin.MANUAL_REPLACEMENT,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }

    public static @NonNull MediaUploadDestination documentThumbnail(Long documentId) {
        requireId(documentId, "Document");

        return new MediaUploadDestination(
                "documents/" + documentId + "/cover",
                "documents/" + documentId + "/thumbnails",
                MediaOrigin.USER_UPLOAD,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }

    public static @NonNull MediaUploadDestination documentPageRendition(
            Long documentId,
            Long pageId
    ) {
        requireId(documentId, "Document");
        requireId(pageId, "Document page");

        return new MediaUploadDestination(
                "documents/" + documentId + "/pages/" + pageId + "/renditions",
                "documents/" + documentId + "/pages/" + pageId + "/thumbnails",
                MediaOrigin.GENERATED,
                MediaRetentionPolicy.KEEP_ORIGINAL_ONLY
        );
    }

    public static @NonNull MediaUploadDestination documentPageFigure(
            Long documentId,
            Long pageId
    ) {
        requireId(documentId, "Document");
        requireId(pageId, "Document page");

        return new MediaUploadDestination(
                "documents/" + documentId + "/pages/" + pageId + "/figures",
                "documents/" + documentId + "/pages/" + pageId + "/thumbnails",
                MediaOrigin.GENERATED,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }

    @Contract(value = "_ -> new", pure = true)
    static @NonNull MediaUploadDestination generic(String category) {
        return new MediaUploadDestination(
                category,
                "thumbnails",
                MediaOrigin.USER_UPLOAD,
                MediaRetentionPolicy.KEEP_PERMANENTLY
        );
    }

    String fileDirectory() {
        return fileDirectory;
    }

    String thumbnailDirectory() {
        return thumbnailDirectory;
    }

    MediaOrigin origin() {
        return origin;
    }

    MediaRetentionPolicy retentionPolicy() {
        return retentionPolicy;
    }
}
