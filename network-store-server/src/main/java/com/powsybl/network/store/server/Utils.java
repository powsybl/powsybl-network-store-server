/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.network.store.model.IdentifiableAttributes;
import com.powsybl.network.store.model.NetworkAttributes;
import com.powsybl.network.store.model.Resource;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import org.apache.commons.lang3.mutable.MutableInt;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_ID_COLUMN;
import static com.powsybl.network.store.server.QueryCatalog.buildGetTombstonedIdentifiablesIdsQuery;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class Utils {

    public static final int BATCH_SIZE = 1000;

    private Utils() throws IllegalAccessException {
        throw new IllegalAccessException("Utility class can not be initialize.");
    }

    private static boolean isCustomTypeJsonified(Class<?> clazz) {
        return !(
                Integer.class.equals(clazz) || Long.class.equals(clazz)
                        || Float.class.equals(clazz) || Double.class.equals(clazz)
                        || String.class.equals(clazz) || Boolean.class.equals(clazz)
                        || UUID.class.equals(clazz)
                        || Date.class.isAssignableFrom(clazz) // java.util.Date and java.sql.Date
            );
    }

    static void bindValues(PreparedStatement statement, List<Object> values, ObjectMapper mapper) throws SQLException {
        int idx = 0;
        for (Object o : values) {
            if (o instanceof Instant d) {
                statement.setDate(++idx, new java.sql.Date(d.toEpochMilli()));
            } else if (o == null || !isCustomTypeJsonified(o.getClass())) {
                statement.setObject(++idx, o);
            } else {
                try {
                    statement.setObject(++idx, mapper.writeValueAsString(o));
                } catch (JsonProcessingException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    static void bindAttributes(ResultSet resultSet, int columnIndex, ColumnMapping columnMapping, IdentifiableAttributes attributes, ObjectMapper mapper) {
        try {
            Object value = null;
            if (columnMapping.getClassR() == null || isCustomTypeJsonified(columnMapping.getClassR())) {
                String str = resultSet.getString(columnIndex);
                if (str != null) {
                    if (columnMapping.getClassMapKey() != null && columnMapping.getClassMapValue() != null) {
                        value = mapper.readValue(str, mapper.getTypeFactory().constructMapType(Map.class, columnMapping.getClassMapKey(), columnMapping.getClassMapValue()));
                    } else {
                        if (columnMapping.getClassR() == null) {
                            throw new PowsyblException("Invalid mapping config");
                        }
                        if (columnMapping.getClassR() == Instant.class) {
                            value = resultSet.getTimestamp(columnIndex).toInstant();
                        } else {
                            value = mapper.readValue(str, columnMapping.getClassR());
                        }
                    }
                }
            } else {
                value = resultSet.getObject(columnIndex, columnMapping.getClassR());
            }
            if (value != null) {
                columnMapping.set(attributes, value);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String generateInPlaceholders(int numberOfValues) {
        StringJoiner placeholders = new StringJoiner(", ");
        for (int i = 0; i < numberOfValues; i++) {
            placeholders.add("?");
        }
        return placeholders.toString();
    }

    public static NetworkAttributes getNetworkAttributes(Connection connection, UUID networkUuid, int variantNum, Mappings mappings, ObjectMapper mapper) {
        try {
            Resource<NetworkAttributes> networkAttributesResource = getNetwork(connection, networkUuid, variantNum, mappings, mapper).orElseThrow(() -> new PowsyblException("Cannot retrieve source network attributes uuid : " + networkUuid + ", variantNum : " + variantNum));
            return networkAttributesResource.getAttributes();
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public static Optional<Resource<NetworkAttributes>> getNetwork(UUID uuid, int variantNum, DataSource dataSource, Mappings mappings, ObjectMapper mapper) {
        try (var connection = dataSource.getConnection()) {
            return getNetwork(connection, uuid, variantNum, mappings, mapper);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private static Optional<Resource<NetworkAttributes>> getNetwork(Connection connection, UUID uuid, int variantNum, Mappings mappings, ObjectMapper mapper) throws SQLException {
        var networkMapping = mappings.getNetworkMappings();
        try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildGetNetworkQuery(networkMapping.getColumnsMapping().keySet()))) {
            preparedStmt.setObject(1, uuid);
            preparedStmt.setInt(2, variantNum);
            try (ResultSet resultSet = preparedStmt.executeQuery()) {
                if (resultSet.next()) {
                    NetworkAttributes attributes = new NetworkAttributes();
                    MutableInt columnIndex = new MutableInt(2);
                    networkMapping.getColumnsMapping().forEach((columnName, columnMapping) -> {
                        bindAttributes(resultSet, columnIndex.getValue(), columnMapping, attributes, mapper);
                        columnIndex.increment();
                    });
                    String networkId = resultSet.getString(1); // id is first
                    Resource<NetworkAttributes> resource = Resource.networkBuilder()
                        .id(networkId)
                        .variantNum(variantNum)
                        .attributes(attributes)
                        .build();
                    return Optional.of(resource);
                }
            }
            return Optional.empty();
        }
    }

    public static Set<String> getTombstonedIdentifiableIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> tombstonedIdentifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedIdentifiablesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    tombstonedIdentifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return tombstonedIdentifiableIds;
    }
}
