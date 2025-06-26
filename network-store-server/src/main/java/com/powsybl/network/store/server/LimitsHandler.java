/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.dto.LimitsInfos;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.PermanentLimitAttributes;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.PermanentLimitSqlData;
import com.powsybl.network.store.server.json.TemporaryLimitSqlData;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@Component
public class LimitsHandler {
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Mappings mappings;

    public LimitsHandler(DataSource dataSource, ObjectMapper mapper, Mappings mappings) {
        this.dataSource = dataSource;
        this.mapper = mapper;
        this.mappings = mappings;
    }

    // temporary limits
    public Map<OwnerInfo, List<TemporaryLimitAttributes>> getTemporaryLimitsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedTemporaryLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getTemporaryLimitsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<TemporaryLimitAttributes>> getTemporaryLimitsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildTemporaryLimitWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetTemporaryLimits(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<PermanentLimitAttributes>> getPermanentLimitsWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedPermanentLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getPermanentLimitsWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<PermanentLimitAttributes>> getPermanentLimitsWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildPermanentLimitWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetPermanentLimits(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, LimitsInfos> getLimitsInfos(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits = getTemporaryLimits(networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
        Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits = getPermanentLimits(networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
        return mergeLimitsIntoLimitsInfos(temporaryLimits, permanentLimits);
    }

    public Map<OwnerInfo, LimitsInfos> getLimitsInfosWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits = getTemporaryLimitsWithInClause(networkUuid, variantNum, columnNameForWhereClause, valuesForInClause);
        Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits = getPermanentLimitsWithInClause(networkUuid, variantNum, columnNameForWhereClause, valuesForInClause);
        return mergeLimitsIntoLimitsInfos(temporaryLimits, permanentLimits);
    }

    private Map<OwnerInfo, LimitsInfos> mergeLimitsIntoLimitsInfos(Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits, Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits) {
        Map<OwnerInfo, LimitsInfos> limitsInfos = new HashMap<>();
        temporaryLimits.forEach((ownerInfo, temporaryLimitAttributes) -> limitsInfos.put(ownerInfo,
            new LimitsInfos(new ArrayList<>(), temporaryLimitAttributes)));
        permanentLimits.forEach((ownerInfo, permanentLimitAttributes) -> {
            if (limitsInfos.containsKey(ownerInfo)) {
                limitsInfos.get(ownerInfo).getPermanentLimits().addAll(permanentLimitAttributes);
            } else {
                limitsInfos.put(ownerInfo, new LimitsInfos(permanentLimitAttributes, new ArrayList<>()));
            }
        });
        return limitsInfos;
    }

    public Map<OwnerInfo, List<TemporaryLimitAttributes>> getTemporaryLimits(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedTemporaryLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getTemporaryLimitsForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<TemporaryLimitAttributes>> getTemporaryLimitsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildTemporaryLimitQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetTemporaryLimits(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<PermanentLimitAttributes>> getPermanentLimits(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedPermanentLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getPermanentLimitsForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, List<PermanentLimitAttributes>> getPermanentLimitsForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildGetPermanentLimitQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetPermanentLimits(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private Map<OwnerInfo, List<TemporaryLimitAttributes>> innerGetTemporaryLimits(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<TemporaryLimitAttributes>> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildTemporaryLimitQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, side, limitType, name, value, acceptableDuration, fictitious
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);
                String temporaryLimitData = resultSet.getString(5);
                List<TemporaryLimitSqlData> parsedTemporaryLimitData = mapper.readValue(temporaryLimitData, new TypeReference<>() { });
                List<TemporaryLimitAttributes> temporaryLimits = parsedTemporaryLimitData.stream().map(TemporaryLimitSqlData::toTemporaryLimitAttributes).toList();
                if (!temporaryLimits.isEmpty()) {
                    map.put(owner, temporaryLimits);
                }
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<OwnerInfo, List<PermanentLimitAttributes>> innerGetPermanentLimits(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, List<PermanentLimitAttributes>> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);
                String permanentLimitData = resultSet.getString(5);
                List<PermanentLimitSqlData> parsedPermanentLimitData = mapper.readValue(permanentLimitData, new TypeReference<>() { });
                List<PermanentLimitAttributes> permanentLimits = parsedPermanentLimitData.stream().map(PermanentLimitSqlData::toPermanentLimitAttributes).toList();
                if (!permanentLimits.isEmpty()) {
                    map.put(owner, permanentLimits);
                }
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected <T extends LimitHolder & IdentifiableAttributes> Map<OwnerInfo, LimitsInfos> getLimitsInfosFromEquipments(UUID networkUuid, List<Resource<T>> resources) {
        Map<OwnerInfo, LimitsInfos> map = new HashMap<>();

        if (!resources.isEmpty()) {
            for (Resource<T> resource : resources) {
                OwnerInfo info = new OwnerInfo(
                    resource.getId(),
                    resource.getType(),
                    networkUuid,
                    resource.getVariantNum()
                );
                T equipment = resource.getAttributes();
                map.put(info, getAllLimitsInfos(equipment));
            }
        }
        return map;
    }

    public void insertTemporaryLimits(Map<OwnerInfo, LimitsInfos> limitsInfos) {
        Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits = limitsInfos.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTemporaryLimits()));
        insertTemporaryLimitsAttributes(temporaryLimits);
    }

    public void insertTemporaryLimitsAttributes(Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(QueryCatalog.buildInsertTemporaryLimitsQuery())) {
                List<Object> values = new ArrayList<>(5);
                List<Map.Entry<OwnerInfo, List<TemporaryLimitAttributes>>> list = new ArrayList<>(temporaryLimits.entrySet());
                for (List<Map.Entry<OwnerInfo, List<TemporaryLimitAttributes>>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<TemporaryLimitAttributes>> entry : subUnit) {
                        if (!entry.getValue().isEmpty()) {
                            values.clear();
                            values.add(entry.getKey().getEquipmentId());
                            values.add(entry.getKey().getEquipmentType().toString());
                            values.add(entry.getKey().getNetworkUuid());
                            values.add(entry.getKey().getVariantNum());
                            values.add(entry.getValue().stream()
                                .map(TemporaryLimitSqlData::of).toList());
                            bindValues(preparedStmt, values, mapper);
                            preparedStmt.addBatch();
                        }
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void insertPermanentLimits(Map<OwnerInfo, LimitsInfos> limitsInfos) {
        Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits = limitsInfos.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getPermanentLimits()));
        insertPermanentLimitsAttributes(permanentLimits);
    }

    public void insertPermanentLimitsAttributes(Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertPermanentLimitsQuery())) {
                List<Object> values = new ArrayList<>(8);
                List<Map.Entry<OwnerInfo, List<PermanentLimitAttributes>>> list = new ArrayList<>(permanentLimits.entrySet());
                for (List<Map.Entry<OwnerInfo, List<PermanentLimitAttributes>>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OwnerInfo, List<PermanentLimitAttributes>> entry : subUnit) {
                        if (!entry.getValue().isEmpty()) {
                            values.clear();
                            values.add(entry.getKey().getEquipmentId());
                            values.add(entry.getKey().getEquipmentType().toString());
                            values.add(entry.getKey().getNetworkUuid());
                            values.add(entry.getKey().getVariantNum());
                            values.add(entry.getValue().stream()
                                .map(PermanentLimitSqlData::of).toList());
                            bindValues(preparedStmt, values, mapper);
                            preparedStmt.addBatch();
                        }
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    protected <T extends LimitHolder & IdentifiableAttributes> void insertLimitsInEquipments(UUID networkUuid, List<Resource<T>> equipments, Map<OwnerInfo, LimitsInfos> limitsInfos) {

        if (!limitsInfos.isEmpty() && !equipments.isEmpty()) {
            for (Resource<T> equipmentAttributesResource : equipments) {
                OwnerInfo owner = new OwnerInfo(
                    equipmentAttributesResource.getId(),
                    equipmentAttributesResource.getType(),
                    networkUuid,
                    equipmentAttributesResource.getVariantNum()
                );
                if (limitsInfos.containsKey(owner)) {
                    T equipment = equipmentAttributesResource.getAttributes();
                    for (TemporaryLimitAttributes temporaryLimit : limitsInfos.get(owner).getTemporaryLimits()) {
                        insertTemporaryLimitInEquipment(equipment, temporaryLimit);
                    }
                    for (PermanentLimitAttributes permanentLimit : limitsInfos.get(owner).getPermanentLimits()) {
                        insertPermanentLimitInEquipment(equipment, permanentLimit);
                    }
                }
            }
        }
    }

    private <T extends LimitHolder> void insertTemporaryLimitInEquipment(T equipment, TemporaryLimitAttributes temporaryLimit) {
        LimitType type = temporaryLimit.getLimitType();
        int side = temporaryLimit.getSide();
        String groupId = temporaryLimit.getOperationalLimitsGroupId();
        if (getLimits(equipment, type, side, groupId) == null) {
            setLimits(equipment, type, side, new LimitsAttributes(), groupId);
        }
        if (getLimits(equipment, type, side, groupId).getTemporaryLimits() == null) {
            getLimits(equipment, type, side, groupId).setTemporaryLimits(new TreeMap<>());
        }
        getLimits(equipment, type, side, groupId).getTemporaryLimits().put(temporaryLimit.getAcceptableDuration(), temporaryLimit);
    }

    private <T extends LimitHolder> void insertPermanentLimitInEquipment(T equipment, PermanentLimitAttributes permanentLimit) {
        LimitType type = permanentLimit.getLimitType();
        int side = permanentLimit.getSide();
        String groupId = permanentLimit.getOperationalLimitsGroupId();
        if (getLimits(equipment, type, side, groupId) == null) {
            setLimits(equipment, type, side, new LimitsAttributes(), groupId);
        }
        getLimits(equipment, type, side, groupId).setPermanentLimit(permanentLimit.getValue());
    }

    public void deleteTemporaryLimits(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildDeleteTemporaryLimitsVariantEquipmentINQuery(equipmentIds.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                for (int i = 0; i < equipmentIds.size(); i++) {
                    preparedStmt.setString(3 + i, equipmentIds.get(i));
                }
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void deletePermanentLimits(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildDeletePermanentLimitsVariantEquipmentINQuery(equipmentIds.size()))) {
                preparedStmt.setObject(1, networkUuid);
                preparedStmt.setInt(2, variantNum);
                for (int i = 0; i < equipmentIds.size(); i++) {
                    preparedStmt.setString(3 + i, equipmentIds.get(i));
                }
                preparedStmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public <T extends IdentifiableAttributes> void deleteTemporaryLimits(UUID networkUuid, List<Resource<T>> resources) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<T> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deleteTemporaryLimits(networkUuid, k, v));
    }

    private <T extends IdentifiableAttributes> void deletePermanentLimits(UUID networkUuid, List<Resource<T>> resources) {
        Map<Integer, List<String>> resourceIdsByVariant = new HashMap<>();
        for (Resource<T> resource : resources) {
            List<String> resourceIds = resourceIdsByVariant.get(resource.getVariantNum());
            if (resourceIds != null) {
                resourceIds.add(resource.getId());
            } else {
                resourceIds = new ArrayList<>();
                resourceIds.add(resource.getId());
            }
            resourceIdsByVariant.put(resource.getVariantNum(), resourceIds);
        }
        resourceIdsByVariant.forEach((k, v) -> deletePermanentLimits(networkUuid, k, v));
    }

    private Set<String> getTombstonedTemporaryLimitsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.TEMPORARY_LIMIT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    private Set<String> getTombstonedPermanentLimitsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.PERMANENT_LIMIT.toString());
            try (var resultSet = preparedStmt.executeQuery()) {
                while (resultSet.next()) {
                    identifiableIds.add(resultSet.getString(EQUIPMENT_ID_COLUMN));
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        return identifiableIds;
    }

    public Set<String> getTombstonedIdentifiableIds(Connection connection, UUID networkUuid, int variantNum) {
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

    private static final String EXCEPTION_UNKNOWN_LIMIT_TYPE = "Unknown limit type";

    private void fillLimitsInfosByTypeAndSide(LimitHolder equipment, LimitsInfos result, LimitType type, int side) {
        Map<String, OperationalLimitsGroupAttributes> operationalLimitsGroups = equipment.getOperationalLimitsGroups(side);
        if (operationalLimitsGroups != null) {
            for (Map.Entry<String, OperationalLimitsGroupAttributes> entry : operationalLimitsGroups.entrySet()) {
                LimitsAttributes limits = getLimits(equipment, type, side, entry.getKey());
                if (limits != null) {
                    if (limits.getTemporaryLimits() != null) {
                        List<TemporaryLimitAttributes> temporaryLimits = new ArrayList<>(
                            limits.getTemporaryLimits().values());
                        temporaryLimits.forEach(e -> {
                            e.setSide(side);
                            e.setLimitType(type);
                            e.setOperationalLimitsGroupId(entry.getKey());
                        });
                        result.getTemporaryLimits().addAll(temporaryLimits);
                    }
                    if (!Double.isNaN(limits.getPermanentLimit())) {
                        result.getPermanentLimits().add(PermanentLimitAttributes.builder()
                            .side(side)
                            .limitType(type)
                            .value(limits.getPermanentLimit())
                            .operationalLimitsGroupId(entry.getKey())
                            .build());
                    }
                }
            }
        }
    }

    private LimitsInfos getAllLimitsInfos(LimitHolder equipment) {
        LimitsInfos result = new LimitsInfos();
        for (Integer side : equipment.getSideList()) {
            fillLimitsInfosByTypeAndSide(equipment, result, LimitType.CURRENT, side);
            fillLimitsInfosByTypeAndSide(equipment, result, LimitType.ACTIVE_POWER, side);
            fillLimitsInfosByTypeAndSide(equipment, result, LimitType.APPARENT_POWER, side);
        }
        return result;
    }

    private void setLimits(LimitHolder equipment, LimitType type, int side, LimitsAttributes limits, String operationalLimitsGroupId) {
        switch (type) {
            case CURRENT -> equipment.setCurrentLimits(side, limits, operationalLimitsGroupId);
            case APPARENT_POWER -> equipment.setApparentPowerLimits(side, limits, operationalLimitsGroupId);
            case ACTIVE_POWER -> equipment.setActivePowerLimits(side, limits, operationalLimitsGroupId);
            default -> throw new IllegalArgumentException(EXCEPTION_UNKNOWN_LIMIT_TYPE);
        }
    }

    private LimitsAttributes getLimits(LimitHolder equipment, LimitType type, int side, String operationalLimitsGroupId) {
        return switch (type) {
            case CURRENT -> equipment.getCurrentLimits(side, operationalLimitsGroupId);
            case APPARENT_POWER -> equipment.getApparentPowerLimits(side, operationalLimitsGroupId);
            case ACTIVE_POWER -> equipment.getActivePowerLimits(side, operationalLimitsGroupId);
            default -> throw new IllegalArgumentException(EXCEPTION_UNKNOWN_LIMIT_TYPE);
        };
    }

    public <T extends IdentifiableAttributes> void updateTemporaryLimits(UUID networkUuid, List<Resource<T>> resources, Map<OwnerInfo, LimitsInfos> limitsInfos) {
        deleteTemporaryLimits(networkUuid, resources);
        insertTemporaryLimits(limitsInfos);
        insertTombstonedTemporaryLimits(networkUuid, limitsInfos, resources);
    }

    private <T extends IdentifiableAttributes> void insertTombstonedTemporaryLimits(UUID networkUuid, Map<OwnerInfo, LimitsInfos> limitsInfos, List<Resource<T>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                .collect(Collectors.groupingBy(
                    Resource::getVariantNum,
                    Collectors.mapping(Resource::getId, Collectors.toList())
                ));
            Set<OwnerInfo> tombstonedTemporaryLimits = PartialVariantUtils.getExternalAttributesToTombstone(
                resourcesByVariant,
                variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                (fullVariantNum, variantNum, ids) -> getTemporaryLimitsWithInClauseForVariant(connection, networkUuid, fullVariantNum, EQUIPMENT_ID_COLUMN, ids, variantNum).keySet(),
                variantNum -> getTombstonedTemporaryLimitsIds(connection, networkUuid, variantNum),
                getTemporaryLimitsToTombstoneFromEquipment(networkUuid, limitsInfos, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo temporaryLimit : tombstonedTemporaryLimits) {
                    preparedStmt.setObject(1, temporaryLimit.getNetworkUuid());
                    preparedStmt.setInt(2, temporaryLimit.getVariantNum());
                    preparedStmt.setString(3, temporaryLimit.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.TEMPORARY_LIMIT.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private <T extends IdentifiableAttributes> Set<OwnerInfo> getPermanentLimitsToTombstoneFromEquipment(UUID networkUuid, Map<OwnerInfo, LimitsInfos> externalAttributesToInsert, List<Resource<T>> resources) {
        Set<OwnerInfo> externalAttributesToTombstoneFromEquipment = new HashSet<>();
        for (Resource<T> resource : resources) {
            OwnerInfo ownerInfo = new OwnerInfo(resource.getId(), resource.getType(), networkUuid, resource.getVariantNum());
            if (!externalAttributesToInsert.containsKey(ownerInfo) || externalAttributesToInsert.get(ownerInfo).getPermanentLimits().isEmpty()) {
                externalAttributesToTombstoneFromEquipment.add(ownerInfo);
            }
        }
        return externalAttributesToTombstoneFromEquipment;
    }

    private <T extends IdentifiableAttributes> Set<OwnerInfo> getTemporaryLimitsToTombstoneFromEquipment(UUID networkUuid, Map<OwnerInfo, LimitsInfos> externalAttributesToInsert, List<Resource<T>> resources) {
        Set<OwnerInfo> externalAttributesToTombstoneFromEquipment = new HashSet<>();
        for (Resource<T> resource : resources) {
            OwnerInfo ownerInfo = new OwnerInfo(resource.getId(), resource.getType(), networkUuid, resource.getVariantNum());
            if (!externalAttributesToInsert.containsKey(ownerInfo) || externalAttributesToInsert.get(ownerInfo).getTemporaryLimits().isEmpty()) {
                externalAttributesToTombstoneFromEquipment.add(ownerInfo);
            }
        }
        return externalAttributesToTombstoneFromEquipment;
    }

    public <T extends IdentifiableAttributes> void updatePermanentLimits(UUID networkUuid, List<Resource<T>> resources, Map<OwnerInfo, LimitsInfos> limitsInfos) {
        deletePermanentLimits(networkUuid, resources);
        insertPermanentLimits(limitsInfos);
        insertTombstonedPermanentLimits(networkUuid, limitsInfos, resources);
    }

    private <T extends IdentifiableAttributes> void insertTombstonedPermanentLimits(UUID networkUuid, Map<OwnerInfo, LimitsInfos> limitsInfos, List<Resource<T>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                .collect(Collectors.groupingBy(
                    Resource::getVariantNum,
                    Collectors.mapping(Resource::getId, Collectors.toList())
                ));
            Set<OwnerInfo> tombstonedPermanentLimits = PartialVariantUtils.getExternalAttributesToTombstone(
                resourcesByVariant,
                variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                (fullVariantNum, variantNum, ids) -> getPermanentLimitsWithInClauseForVariant(connection, networkUuid, fullVariantNum, EQUIPMENT_ID_COLUMN, ids, variantNum).keySet(),
                variantNum -> getTombstonedPermanentLimitsIds(connection, networkUuid, variantNum),
                getPermanentLimitsToTombstoneFromEquipment(networkUuid, limitsInfos, resources)
            );
            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo permanentLimit : tombstonedPermanentLimits) {
                    preparedStmt.setObject(1, permanentLimit.getNetworkUuid());
                    preparedStmt.setInt(2, permanentLimit.getVariantNum());
                    preparedStmt.setString(3, permanentLimit.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.PERMANENT_LIMIT.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private boolean isMapContainsOperationalLimitsGroup(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId) {
        return map.containsKey(branchId) && map.get(branchId).containsKey(side) && map.get(branchId).get(side).containsKey(groupId);
    }

    private void addElementToOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId, OperationalLimitsGroupAttributes operationalLimitsGroupAttributes) {
        map.computeIfAbsent(branchId, k -> new HashMap<>())
            .computeIfAbsent(side, k -> new HashMap<>())
            .put(groupId, operationalLimitsGroupAttributes);
    }

    private OperationalLimitsGroupAttributes getElementFromOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId) {
        if (isMapContainsOperationalLimitsGroup(map, branchId, side, groupId)) {
            return map.get(branchId).get(side).get(groupId);
        }
        return null;
    }

    public Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> convertLimitInfosToOperationalLimitsGroupMap(String equipmentId, LimitsInfos limitsInfos) {
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitGroups = new HashMap<>();
        // permanent limits
        limitsInfos.getPermanentLimits().forEach(permanentLimit -> {
            if (isMapContainsOperationalLimitsGroup(operationalLimitGroups, equipmentId, permanentLimit.getSide(), permanentLimit.getOperationalLimitsGroupId())) {
                setPermanentLimit(operationalLimitGroups.get(equipmentId).get(permanentLimit.getSide())
                    .get(permanentLimit.getOperationalLimitsGroupId()), permanentLimit);
            } else {
                OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = new OperationalLimitsGroupAttributes();
                operationalLimitsGroupAttributes.setId(permanentLimit.getOperationalLimitsGroupId());
                setPermanentLimit(operationalLimitsGroupAttributes, permanentLimit);
                addElementToOperationalLimitsGroupMap(operationalLimitGroups, equipmentId, permanentLimit.getSide(),
                    permanentLimit.getOperationalLimitsGroupId(), operationalLimitsGroupAttributes);
            }
        });
        // temporary limits
        limitsInfos.getTemporaryLimits().forEach(temporaryLimit -> {
            if (isMapContainsOperationalLimitsGroup(operationalLimitGroups, equipmentId, temporaryLimit.getSide(), temporaryLimit.getOperationalLimitsGroupId())) {
                setTemporaryLimit(operationalLimitGroups.get(equipmentId).get(temporaryLimit.getSide())
                    .get(temporaryLimit.getOperationalLimitsGroupId()), temporaryLimit);
            } else {
                throw new PowsyblException("a limit groups can not have temporary limits without a permanent limit");
            }
        });
        return operationalLimitGroups;
    }

    private void setPermanentLimit(OperationalLimitsGroupAttributes operationalLimitsGroupAttributes, PermanentLimitAttributes permanentLimitAttributes) {
        LimitsAttributes limitsAttributes = new LimitsAttributes();
        limitsAttributes.setOperationalLimitsGroupId(operationalLimitsGroupAttributes.getId());
        limitsAttributes.setPermanentLimit(permanentLimitAttributes.getValue());
        switch (permanentLimitAttributes.getLimitType()) {
            case CURRENT -> operationalLimitsGroupAttributes.setCurrentLimits(limitsAttributes);
            case ACTIVE_POWER -> operationalLimitsGroupAttributes.setActivePowerLimits(limitsAttributes);
            case APPARENT_POWER -> operationalLimitsGroupAttributes.setApparentPowerLimits(limitsAttributes);
            case VOLTAGE, VOLTAGE_ANGLE -> throw new IllegalArgumentException("VOLTAGE and VOLTAGE_ANGLE does not support permanent limits");
        }
    }

    private void setTemporaryLimit(OperationalLimitsGroupAttributes operationalLimitsGroupAttributes, TemporaryLimitAttributes temporaryLimitAttributes) {
        switch (temporaryLimitAttributes.getLimitType()) {
            case CURRENT -> setTemporaryLimitInLimitsAttribute(operationalLimitsGroupAttributes.getCurrentLimits(), temporaryLimitAttributes);
            case ACTIVE_POWER -> setTemporaryLimitInLimitsAttribute(operationalLimitsGroupAttributes.getActivePowerLimits(), temporaryLimitAttributes);
            case APPARENT_POWER -> setTemporaryLimitInLimitsAttribute(operationalLimitsGroupAttributes.getApparentPowerLimits(), temporaryLimitAttributes);
            case VOLTAGE, VOLTAGE_ANGLE -> throw new IllegalArgumentException("VOLTAGE and VOLTAGE_ANGLE does not support temporary limits");
        }
    }

    private void setTemporaryLimitInLimitsAttribute(LimitsAttributes limitsAttributes, TemporaryLimitAttributes temporaryLimitAttributes) {
        if (limitsAttributes.getTemporaryLimits() == null) {
            limitsAttributes.setTemporaryLimits(new TreeMap<>());
        }
        limitsAttributes.addTemporaryLimit(temporaryLimitAttributes);
    }

    public Optional<OperationalLimitsGroupAttributes> getOperationalLimitsGroup(UUID networkId, int variantNum,
                                                                                String branchId, ResourceType type,
                                                                                String operationalLimitsGroupName,
                                                                                int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        LimitsInfos limitsInfos = getLimitsInfos(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo);
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupAttributes = convertLimitInfosToOperationalLimitsGroupMap(branchId, limitsInfos);
        return Optional.ofNullable(getElementFromOperationalLimitsGroupMap(operationalLimitsGroupAttributes, branchId, side, operationalLimitsGroupName));
    }

    public List<OperationalLimitsGroupAttributes> getAllOperationalLimitsGroupAttributesForBranchSide(
        UUID networkId, int variantNum, ResourceType type, String branchId, int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Optional<LimitsInfos> limitsInfos = Optional.ofNullable(getLimitsInfos(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo));
        if (limitsInfos.isPresent()) {
            Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> allBranchOperationalLimitsGroups =
                convertLimitInfosToOperationalLimitsGroupMap(branchId, limitsInfos.get());
            List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = new ArrayList<>();
            allBranchOperationalLimitsGroups.forEach((equipmentId, map1) -> {
                if (map1.get(side) != null) {
                    operationalLimitsGroupAttributes.addAll(map1.get(side).values().stream().toList());
                }
            });
            return operationalLimitsGroupAttributes;
        }
        return Collections.emptyList();
    }

    public Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> getAllSelectedOperationalLimitsGroupAttributesByResourceType(
            UUID networkId, int variantNum, ResourceType type, int fullVariantNum, Set<String> tombstonedElements) {
        // get selected operational limits ids for each element of type indicated
        Map<OwnerInfo, LimitsInfos> limitsInfos = getLimitsInfos(networkId, variantNum, EQUIPMENT_TYPE_COLUMN, type.toString());
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedOperationalLimitsGroups =
            getSelectedOperationalLimitsGroupIdsForVariant(networkId, variantNum, fullVariantNum, type, tombstonedElements);
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> selectedOperationalLimitsGroupAttributes = new HashMap<>();
        // get operational limits group associated
        selectedOperationalLimitsGroups.forEach((owner, selectedOperationalLimitsGroupIdentifiers) -> {
            String selectedOperationalLimitsGroupId1 = selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId1();
            String selectedOperationalLimitsGroupId2 = selectedOperationalLimitsGroupIdentifiers.operationalLimitsGroupId2();
            if (selectedOperationalLimitsGroupId1 != null || selectedOperationalLimitsGroupId2 != null) {
                String equipmentId = owner.getEquipmentId();
                Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupAttributesMap = convertLimitInfosToOperationalLimitsGroupMap(
                    equipmentId, limitsInfos.get(owner));
                // side 1
                addSelectedOperationalLimitsGroupOnSide(selectedOperationalLimitsGroupId1, operationalLimitsGroupAttributesMap, 1, equipmentId, selectedOperationalLimitsGroupAttributes);
                addSelectedOperationalLimitsGroupOnSide(selectedOperationalLimitsGroupId2, operationalLimitsGroupAttributesMap, 2, equipmentId, selectedOperationalLimitsGroupAttributes);
            }
        });
        return selectedOperationalLimitsGroupAttributes;
    }

    private void addSelectedOperationalLimitsGroupOnSide(String selectedOperationalLimitsGroupId,
                                                         Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupAttributesMap,
                                                         int side, String equipmentId,
                                                         Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> selectedOperationalLimitsGroupAttributes) {
        if (selectedOperationalLimitsGroupId != null) {
            OperationalLimitsGroupAttributes operationalLimitsGroupAttributes = getElementFromOperationalLimitsGroupMap(operationalLimitsGroupAttributesMap, equipmentId, side, selectedOperationalLimitsGroupId);
            if (operationalLimitsGroupAttributes != null) {
                addElementToOperationalLimitsGroupMap(selectedOperationalLimitsGroupAttributes, equipmentId, side, selectedOperationalLimitsGroupId, operationalLimitsGroupAttributes);
            }
        }
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIdsForVariant(UUID networkId, int variantNum, int fullVariantNum, ResourceType type, Set<String> tombstonedElements) {
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> selectedGroupIds = getSelectedOperationalLimitsGroupIds(networkId, variantNum, type, tombstonedElements, variantNum);
        if (NetworkAttributes.isFullVariant(fullVariantNum)) {
            return selectedGroupIds;
        }
        Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> fullVariantSelectedGroupIds = getSelectedOperationalLimitsGroupIds(networkId, fullVariantNum, type, tombstonedElements, variantNum);
        fullVariantSelectedGroupIds.putAll(selectedGroupIds);
        return fullVariantSelectedGroupIds;
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getSelectedOperationalLimitsGroupIds(UUID networkId, int variantNum, ResourceType type, Set<String> tombstonedElements, int refVariantNum) {

        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(
                    QueryCatalog.buildGetSelectedOperationalLimitsGroupsQuery(mappings.getTableMapping(type).getTable()))) {
                preparedStmt.setObject(1, networkId);
                preparedStmt.setInt(2, variantNum);
                return getInnerSelectedOperationalLimitsGroupIds(networkId, type, preparedStmt, tombstonedElements, refVariantNum);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
        // get selected operational Limits group
    }

    private Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> getInnerSelectedOperationalLimitsGroupIds(UUID networkId, ResourceType type, PreparedStatement preparedStmt, Set<String> tombstonedElements, int refVariantNum) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, SelectedOperationalLimitsGroupIdentifiers> resources = new HashMap<>();
            while (resultSet.next()) {
                // first is ID
                String branchId = resultSet.getString(1);
                String operationalLimitsGroupId1 = resultSet.getString(2);
                String operationalLimitsGroupId2 = resultSet.getString(3);
                if (!tombstonedElements.contains(branchId)) {
                    resources.put(new OwnerInfo(branchId, type, networkId, refVariantNum), new SelectedOperationalLimitsGroupIdentifiers(branchId, operationalLimitsGroupId1, operationalLimitsGroupId2));
                }
            }
            return resources;
        }
    }

    record SelectedOperationalLimitsGroupIdentifiers(String branchId, String operationalLimitsGroupId1,
                                                     String operationalLimitsGroupId2) {
    }
}
