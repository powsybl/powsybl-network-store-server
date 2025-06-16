/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.network.store.model.Attributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
@Service
public class NetworkStoreObserver {

    private static final String OBSERVATION_PREFIX = "app.network.store.server.";
    private static final String RESOURCE_TYPE_TAG_NAME = "resource_type";
    private static final String EMPTY_TAG_NAME = "empty";
    private static final String PER_RESOURCE = ".per.resource";
    private static final TimeUnit TIME_UNIT = TimeUnit.NANOSECONDS;
    private static final String UNKNOWN_RESOURCE_TYPE = "UNKNOWN";

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public NetworkStoreObserver(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public <E extends Throwable> void observe(String name, ResourceType resourceType, int size, Observation.CheckedRunnable<E> runnable) throws E {
        Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .observeChecked(() -> {
                    runnable.run();
                    recordPerResourceMetric(name, resourceType, size);
                });
    }

    public <E extends Throwable> void observeClone(String name, int numberOfVariantsCloned, Observation.CheckedRunnable<E> runnable) throws E {
        Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .observeChecked(() -> {
                    runnable.run();
                    recordPerVariantMetric(name, numberOfVariantsCloned);
                });
    }

    private void recordPerVariantMetric(String name, int numberOfVariants) {
        Long duration = getDurationFromObservation();
        if (duration == null) {
            return;
        }
        Timer.builder(OBSERVATION_PREFIX + name + ".per.variant")
                .register(meterRegistry)
                .record(duration / numberOfVariants, TIME_UNIT);
    }

    private Long getDurationFromObservation() {
        Observation currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation == null) {
            return null;
        }

        Observation.Context context = currentObservation.getContext();
        LongTaskTimer.Sample timer = context.get(LongTaskTimer.Sample.class);
        if (timer == null) {
            return null;
        }

        return (long) timer.duration(TIME_UNIT);
    }

    public <T, E extends Throwable> List<T> observe(String name, ResourceType resourceType, Observation.CheckedCallable<List<T>, E> callable) throws E {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .observeChecked(() -> {
                    List<T> results = callable.call();
                    recordPerResourceMetric(name, resourceType, results.size());
                    return results;
                });
    }

    private void recordPerResourceMetric(String name, ResourceType resourceType, int size) {
        Long duration = getDurationFromObservation();
        if (duration == null) {
            return;
        }
        Timer.builder(OBSERVATION_PREFIX + name + PER_RESOURCE)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .tag(EMPTY_TAG_NAME, size > 0 ? "false" : "true")
                .register(meterRegistry)
                .record(size > 0 ? duration / size : duration, TIME_UNIT);
    }

    public <T extends Attributes, E extends Throwable> Optional<Resource<T>> observeOne(String name, ResourceType resourceType, Observation.CheckedCallable<Optional<Resource<T>>, E> callable) throws E {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(RESOURCE_TYPE_TAG_NAME, resourceType != null ? resourceType.name() : UNKNOWN_RESOURCE_TYPE)
                .observeChecked(callable);
    }
}
