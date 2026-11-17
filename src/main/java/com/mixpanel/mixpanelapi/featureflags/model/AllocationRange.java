package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents a half-open range of allocation buckets, [start, end), assigned to an
 * experiment entry of a multi-experiment flag.
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class AllocationRange {
    private final int start;
    private final int end;

    /**
     * Creates a new AllocationRange.
     *
     * @param start the first bucket in the range (inclusive)
     * @param end the bucket after the last bucket in the range (exclusive)
     */
    public AllocationRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    /**
     * @return the first bucket in the range (inclusive)
     */
    public int getStart() {
        return start;
    }

    /**
     * @return the bucket after the last bucket in the range (exclusive)
     */
    public int getEnd() {
        return end;
    }

    /**
     * @param bucket the allocation bucket (0-99)
     * @return true if start &lt;= bucket &lt; end
     */
    public boolean contains(int bucket) {
        return start <= bucket && bucket < end;
    }

    @Override
    public String toString() {
        return "AllocationRange{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
