/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.google.common.base.Stopwatch;
import com.powsybl.network.store.model.Attributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.model.ResourceType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;

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
    private static final String PER_ITEM = ".per.item";
    private static final TimeUnit TIME_UNIT = TimeUnit.NANOSECONDS;

    private final MeterRegistry meterRegistry;

    public NetworkStoreObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public <E extends Throwable> void observe(String name, ResourceType resourceType, int size, Observation.CheckedRunnable<E> runnable) throws E {
        Stopwatch stopwatch = Stopwatch.createStarted();
        runnable.run();
        long duration = stopwatch.elapsed(TIME_UNIT);

        Timer.builder(OBSERVATION_PREFIX + name)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .register(meterRegistry)
                .record(duration, TIME_UNIT);

        Timer.builder(OBSERVATION_PREFIX + name + PER_ITEM)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .tag(EMPTY_TAG_NAME, size > 0 ? "false" : "true")
                .register(meterRegistry)
                .record(size > 0 ? duration / size : duration, TIME_UNIT);
    }

    public <E extends Throwable> void observeClone(String name, int numberOfVariantsCloned, Observation.CheckedRunnable<E> runnable) throws E {
        Stopwatch stopwatch = Stopwatch.createStarted();
        runnable.run();
        long duration = stopwatch.elapsed(TIME_UNIT);

        if (numberOfVariantsCloned > 0) {
            Timer.builder(OBSERVATION_PREFIX + name)
                    .register(meterRegistry)
                    .record(duration, TIME_UNIT);

            Timer.builder(OBSERVATION_PREFIX + name + ".per.variant")
                    .register(meterRegistry)
                    .record(duration / numberOfVariantsCloned, TIME_UNIT);
        }
    }

    public <T, E extends Throwable> List<T> observe(String name, ResourceType resourceType, Observation.CheckedCallable<List<T>, E> callable) throws E {
        Stopwatch stopwatch = Stopwatch.createStarted();
        List<T> results = callable.call();
        long duration = stopwatch.elapsed(TIME_UNIT);

        Timer.builder(OBSERVATION_PREFIX + name)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .register(meterRegistry)
                .record(duration, TIME_UNIT);

        Timer.builder(OBSERVATION_PREFIX + name + PER_ITEM)
                .tag(RESOURCE_TYPE_TAG_NAME, resourceType.name())
                .tag(EMPTY_TAG_NAME, !results.isEmpty() ? "false" : "true")
                .register(meterRegistry)
                .record(!results.isEmpty() ? duration / results.size() : duration, TIME_UNIT);

        return results;
    }

    public <T extends Attributes, E extends Throwable> Optional<Resource<T>> observeOne(String name, Observation.CheckedCallable<Optional<Resource<T>>, E> callable) throws E {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Optional<Resource<T>> result = callable.call();
        long duration = stopwatch.elapsed(TIME_UNIT);

        Timer.builder(OBSERVATION_PREFIX + name)
                .tag(RESOURCE_TYPE_TAG_NAME, result.map(resource -> resource.getType().name()).orElse("UNKNOWN"))
                .register(meterRegistry)
                .record(duration, TIME_UNIT);

        return result;
    }
}
