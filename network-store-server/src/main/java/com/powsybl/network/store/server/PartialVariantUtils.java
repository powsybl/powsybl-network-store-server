/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.network.store.model.Attributes;
import com.powsybl.network.store.model.NetworkAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.server.dto.OwnerInfo;
import org.apache.commons.lang3.function.TriFunction;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Antoine Bouhours <antoine.bouhours at rte-france.com>
 */
public final class PartialVariantUtils {
    private PartialVariantUtils() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class can not be initialize.");
    }

    public static <T, U> Map<T, U> getExternalAttributes(
            int variantNum,
            int fullVariantNum,
            Supplier<Set<String>> fetchTombstonedExternalAttributesIds,
            Supplier<Set<String>> fetchTombstonedIdentifiableIds,
            IntFunction<Map<T, U>> fetchExternalAttributesInVariant,
            Function<T, String> idExtractor) {
        if (NetworkAttributes.isFullVariant(fullVariantNum)) {
            // If the variant is full, retrieve external attributes directly
            return fetchExternalAttributesInVariant.apply(variantNum);
        }

        // Retrieve external attributes from the full variant first
        Map<T, U> externalAttributes = fetchExternalAttributesInVariant.apply(fullVariantNum);

        // Remove external attributes associated to tombstoned resources, tombstoned external attributes and any other additional identifiable ids
        Set<String> tombstonedIds = fetchTombstonedIdentifiableIds.get();
        Set<String> tombstonedExternalAttributesIds = fetchTombstonedExternalAttributesIds.get();
        externalAttributes.keySet().removeIf(ownerInfo ->
                        tombstonedIds.contains(idExtractor.apply(ownerInfo)) ||
                        tombstonedExternalAttributesIds.contains(idExtractor.apply(ownerInfo))
        );

        // Retrieve external attributes in partial variant
        Map<T, U> partialVariantExternalAttributes = fetchExternalAttributesInVariant.apply(variantNum);

        // Combine external attributes from full and partial variant
        externalAttributes.putAll(partialVariantExternalAttributes);

        return externalAttributes;
    }

    public static <T extends OwnerInfo> Set<T> getExternalAttributesToTombstone(
            Map<Integer, List<String>> externalAttributesResourcesIdsByVariant,
            IntFunction<NetworkAttributes> fetchNetworkAttributes,
            TriFunction<Integer, Integer, List<String>, Set<T>> fetchExternalAttributesOwnerInfoInVariant,
            IntFunction<Set<String>> fetchTombstonedExternalAttributesIds,
            Set<T> externalAttributesToTombstoneFromEquipments
    ) {
        if (externalAttributesToTombstoneFromEquipments.isEmpty()) {
            return Set.of();
        }

        Set<T> externalAttributesResourcesInFullVariant = new HashSet<>();
        Set<String> tombstonedExternalAttributes = new HashSet<>();
        Set<Integer> fullVariant = new HashSet<>();
        // Retrieve external attributes from full variant
        for (Map.Entry<Integer, List<String>> entry : externalAttributesResourcesIdsByVariant.entrySet()) {
            int variantNum = entry.getKey();
            List<String> resourcesIds = entry.getValue();
            NetworkAttributes networkAttributes = fetchNetworkAttributes.apply(variantNum);
            int fullVariantNum = networkAttributes.getFullVariantNum();
            if (NetworkAttributes.isFullVariant(fullVariantNum)) {
                fullVariant.add(variantNum);
            }
            externalAttributesResourcesInFullVariant.addAll(fetchExternalAttributesOwnerInfoInVariant.apply(fullVariantNum, variantNum, resourcesIds));
            tombstonedExternalAttributes.addAll(fetchTombstonedExternalAttributesIds.apply(variantNum));
        }
        // Tombstone only external attributes existing in full variant and not already tombstoned
        return externalAttributesToTombstoneFromEquipments.stream().filter(owner ->
                externalAttributesResourcesInFullVariant.contains(owner) &&
                !tombstonedExternalAttributes.contains(owner.getEquipmentId()) &&
                !fullVariant.contains(owner.getVariantNum())).collect(Collectors.toSet());
    }

    public static <T> List<T> getIdentifiables(
            int variantNum,
            int fullVariantNum,
            Supplier<Set<String>> fetchTombstonedIdentifiableIds,
            IntFunction<List<T>> fetchIdentifiablesInVariant,
            Function<T, String> idExtractor,
            Supplier<List<String>> fetchIdentifiblesIdsInVariant) {
        if (NetworkAttributes.isFullVariant(fullVariantNum)) {
            // If the variant is full, retrieve identifiables directly
            return fetchIdentifiablesInVariant.apply(variantNum);
        }

        // Retrieve identifiables from the full variant first
        List<T> identifiables = fetchIdentifiablesInVariant.apply(fullVariantNum);

        // Retrieve identifiables in partial variant
        List<T> partialVariantIdentifiables = fetchIdentifiablesInVariant.apply(variantNum);

        // Retrieve ids in partial variant
        Set<String> partialVariantIds = fetchIdentifiblesIdsInVariant != null
                ? new HashSet<>(fetchIdentifiblesIdsInVariant.get())
                : partialVariantIdentifiables.stream()
                .map(idExtractor)
                .collect(Collectors.toSet());

        // Remove any resources in the partial variant or tombstoned
        Set<String> tombstonedIds = fetchTombstonedIdentifiableIds.get();
        identifiables.removeIf(resource ->
                partialVariantIds.contains(idExtractor.apply(resource)) || tombstonedIds.contains(idExtractor.apply(resource))
        );

        // Combine identifiables from full and partial variant
        identifiables.addAll(partialVariantIdentifiables);

        return identifiables;
    }

    public static <T extends Attributes> Optional<Resource<T>> getOptionalIdentifiable(
            int variantNum,
            int fullVariantNum,
            BooleanSupplier fetchIsTombstonedIdentifiable,
            IntFunction<Optional<Resource<T>>> fetchIdentifiableInVariant) {
        if (NetworkAttributes.isFullVariant(fullVariantNum)) {
            // If the variant is full, retrieve identifiables directly
            return fetchIdentifiableInVariant.apply(variantNum);
        }

        // Retrieve identifiable in partial variant
        Optional<Resource<T>> partialVariantIdentifiable = fetchIdentifiableInVariant.apply(variantNum);
        if (partialVariantIdentifiable.isPresent()) {
            return partialVariantIdentifiable;
        }

        if (fetchIsTombstonedIdentifiable.getAsBoolean()) {
            // Return empty if the identifiable is marked as tombstoned
            return Optional.empty();
        }

        // Retrieve identifiable from the full variant
        return fetchIdentifiableInVariant.apply(fullVariantNum);
    }
}
