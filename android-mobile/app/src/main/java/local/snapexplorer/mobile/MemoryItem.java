package local.snapexplorer.mobile;

/** Memory metadata; an empty mediaId means the export did not include the file. */
public final class MemoryItem {
    public String id = "", date = "", mediaType = "", mediaId = "";
    public long epoch;
    public Double latitude, longitude;
}
