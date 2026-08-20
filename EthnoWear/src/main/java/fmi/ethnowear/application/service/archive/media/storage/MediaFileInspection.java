package fmi.ethnowear.application.service.archive.media.storage;

public record MediaFileInspection(
        Integer width,
        Integer height
) {
    public static MediaFileInspection withoutDimensions() {
        return new MediaFileInspection(null, null);
    }
}
