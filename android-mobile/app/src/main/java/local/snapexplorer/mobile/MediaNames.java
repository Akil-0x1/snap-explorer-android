package local.snapexplorer.mobile;

/** Small framework-free rules shared by media UI and export code. */
public final class MediaNames {
    public static String safeFileName(String raw) {
        String name = raw == null ? "media" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}<>:\"|?*]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "media";
        if (name.length() > 160) {
            int dot = name.lastIndexOf('.');
            String extension = dot >= 0 && name.length() - dot < 12 ? name.substring(dot) : "";
            name = name.substring(0, 160 - extension.length()) + extension;
        }
        return name;
    }
    public static int sampleSize(int width, int height, int bound) {
        if (bound < 1) throw new IllegalArgumentException("bound");
        int sample = 1;
        while (width / sample > bound || height / sample > bound) {
            if (sample >= 1 << 29) break;
            sample *= 2;
        }
        return sample;
    }
    public static String duration(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        return java.lang.String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }
    private MediaNames() { }
}
