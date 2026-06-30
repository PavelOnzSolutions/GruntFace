package solutions.onz.toolbox.gruntface.model;

public record ByteRange(int start, int end) {
    public ByteRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid range " + start + ".." + end);
        }
    }
    public int length() { return end - start; }
}
