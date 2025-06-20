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
import io.micrometer.common.KeyValue;
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
    private static final String PER_RESOURCE_SUFFIX = ".per.resource";
    public static final String PER_VARIANT_SUFFIX = ".per.variant";

    private static final String RESOURCE_TYPE_TAG_NAME = "resource_type";

    private static final TimeUnit TIME_UNIT = TimeUnit.NANOSECONDS;

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public NetworkStoreObserver(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
    }

    public <E extends Throwable> void observe(String name, ResourceType resourceType, int size, Observation.CheckedRunnable<E> runnable) throws E {
        Observation observation = createObservation(name, resourceType);
        observation
                .observeChecked(() -> {
                    runnable.run();
                    recordPerResourceMetric(observation, name, resourceType, size);
                });
    }

    private Observation createObservation(String name, ResourceType resourceType) {
        return Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry)
                .lowCardinalityKeyValue(RESOURCE_TYPE_TAG_NAME, resourceType.name());
    }

    private void recordPerResourceMetric(Observation observation, String name, ResourceType resourceType, int size) {
        if (size == 0) {
            return;
        }

        Long duration = getDurationFromObservation(observation);
        if (duration == null) {
            return;
        }
        Timer.builder(OBSERVATION_PREFIX + name + PER_RESOURCE_SUFFIX)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .register(meterRegistry)
                .record(duration / size, TIME_UNIT);
    }

    private Long getDurationFromObservation(Observation observation) {
        LongTaskTimer.Sample timer = observation.getContext().get(LongTaskTimer.Sample.class);
        if (timer == null) {
            return null;
        }

        return (long) timer.duration(TIME_UNIT);
    }

    public <E extends Throwable> void observeClone(String name, int numberOfVariantsCloned, Observation.CheckedRunnable<E> runnable) throws E {
        Observation observation = Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry);
        observation
                .observeChecked(() -> {
                    runnable.run();
                    recordPerVariantMetric(observation, name, numberOfVariantsCloned);
                });
    }

    private void recordPerVariantMetric(Observation observation, String name, int numberOfVariants) {
        if (numberOfVariants == 0) {
            return;
        }

        Long duration = getDurationFromObservation(observation);
        if (duration == null) {
            return;
        }
        Timer.builder(OBSERVATION_PREFIX + name + PER_VARIANT_SUFFIX)
                .register(meterRegistry)
                .record(duration / numberOfVariants, TIME_UNIT);
    }

    public <T, E extends Throwable> List<T> observe(String name, ResourceType resourceType, Observation.CheckedCallable<List<T>, E> callable) throws E {
        Observation observation = createObservation(name, resourceType);
        return observation
                .observeChecked(() -> {
                    List<T> results = callable.call();
                    recordPerResourceMetric(observation, name, resourceType, results.size());
                    return results;
                });
    }

    public <T extends Attributes, E extends Throwable> Optional<Resource<T>> observeOne(String name, Observation.CheckedCallable<Optional<Resource<T>>, E> callable) throws E {
        Observation observation = Observation.createNotStarted(OBSERVATION_PREFIX + name, observationRegistry);
        return observation
                .observeChecked(() -> {
                    Optional<Resource<T>> result = callable.call();
                    if (result.isPresent()) {
                        observation.getContext().addLowCardinalityKeyValue(KeyValue.of(RESOURCE_TYPE_TAG_NAME, result.get().getType().name()));
                    }
                    return result;
                });
    }
}
