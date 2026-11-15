package local.snapexplorer.mobile;

/** A locally staged archive media file, addressed by SHA-256 of its bytes. */
public final class MediaItem {
    public String id = "", sha256 = "", storedPath = "", originalPath = "", name = "", mime = "", kind = "other", source = "chat";
    public long epoch, bytes;
    public Double latitude, longitude;
}
