package com.mixpanel.mixpanelapi.featureflags.provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for exposure tracker mocks.
 * Provides common event storage and retrieval functionality.
 * <p>
 * Subclasses should extend this class and implement the specific ExposureTracker interface
 * for their provider type (LocalFlagsProvider.ExposureTracker or RemoteFlagsProvider.ExposureTracker).
 * </p>
 *
 * @param <E> the type of exposure event
 */
public abstract class BaseExposureTrackerMock<E> {
    protected final List<E> events = new ArrayList<>();

    /**
     * Reset the tracker by clearing all recorded events.
     */
    public void reset() {
        events.clear();
    }

    /**
     * Get the count of tracked exposure events.
     *
     * @return the number of events tracked
     */
    public int getEventCount() {
        return events.size();
    }

    /**
     * Get the most recently tracked exposure event.
     *
     * @return the last event, or null if no events have been tracked
     */
    public E getLastEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }
}
