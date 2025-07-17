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
import com.powsybl.network.store.server.dto.OperationalLimitsGroupOwnerInfo;
import com.powsybl.network.store.server.dto.OwnerInfo;
import com.powsybl.network.store.server.dto.PermanentLimitAttributes;
import com.powsybl.network.store.server.exceptions.UncheckedSqlException;
import com.powsybl.network.store.server.json.LimitsGroupAttributesSqlData;
import com.powsybl.network.store.server.json.TemporaryLimitInfosSqlData;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

    public Map<OwnerInfo, LimitsInfos> getLimitsInfos(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        return getOperationaLimitsGroup(networkUuid, variantNum, columnNameForWhereClause, valueForWhereClause);
    }

    public Map<OwnerInfo, LimitsInfos> getLimitsInfosWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        return getOperationaLimitsGroupWithInClause(networkUuid, variantNum, columnNameForWhereClause, valuesForInClause);
    }

    public Map<OwnerInfo, LimitsInfos> getOperationaLimitsGroup(UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedOperationalLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupForVariant(connection, networkUuid, variant, columnNameForWhereClause, valueForWhereClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, LimitsInfos> getOperationaLimitsGroupWithInClause(UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause) {
        try (var connection = dataSource.getConnection()) {
            return PartialVariantUtils.getExternalAttributes(
                variantNum,
                getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper).getFullVariantNum(),
                () -> getTombstonedOperationalLimitsIds(connection, networkUuid, variantNum),
                () -> getTombstonedIdentifiableIds(connection, networkUuid, variantNum),
                variant -> getOperationalLimitsGroupWithInClauseForVariant(connection, networkUuid, variant, columnNameForWhereClause, valuesForInClause, variantNum),
                OwnerInfo::getEquipmentId);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, LimitsInfos> getOperationalLimitsGroupForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, String valueForWhereClause, int variantNumOverride) {
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupQuery(columnNameForWhereClause))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, valueForWhereClause);

            return innerGetOperationalLimitsGroup(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Map<OwnerInfo, LimitsInfos> getOperationalLimitsGroupWithInClauseForVariant(Connection connection, UUID networkUuid, int variantNum, String columnNameForWhereClause, List<String> valuesForInClause, int variantNumOverride) {
        if (valuesForInClause.isEmpty()) {
            return Collections.emptyMap();
        }
        try (var preparedStmt = connection.prepareStatement(buildOperationalLimitsGroupWithInClauseQuery(columnNameForWhereClause, valuesForInClause.size()))) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            for (int i = 0; i < valuesForInClause.size(); i++) {
                preparedStmt.setString(3 + i, valuesForInClause.get(i));
            }

            return innerGetOperationalLimitsGroup(preparedStmt, variantNumOverride);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private void addLimit(LimitsInfos limitsInfos, String operationalLimitsGroupId, Integer side, LimitType limitType, Double permanentLimit, String temporaryLimitData) throws JsonProcessingException {
        if (!Double.isNaN(permanentLimit)) {
            limitsInfos.addPermanentLimit(PermanentLimitAttributes.builder()
                .operationalLimitsGroupId(operationalLimitsGroupId)
                .limitType(limitType)
                .side(side)
                .value((Double) permanentLimit)
                .build());
        }

        if (!StringUtils.isEmpty(temporaryLimitData)) {
            List<TemporaryLimitInfosSqlData> parsedCurrentLimitTemporaryLimitData = mapper.readValue(temporaryLimitData, new TypeReference<>() {
            });
            for (TemporaryLimitInfosSqlData sqlData : parsedCurrentLimitTemporaryLimitData) {
                limitsInfos.addTemporaryLimit(TemporaryLimitAttributes.builder()
                    .operationalLimitsGroupId(operationalLimitsGroupId)
                    .limitType(limitType)
                    .side(side)
                    .name(sqlData.getName())
                    .value(sqlData.getValue())
                    .acceptableDuration(sqlData.getAcceptableDuration())
                    .fictitious(sqlData.isFictitious())
                    .build());
            }
        }
    }

    private Map<OwnerInfo, LimitsInfos> innerGetOperationalLimitsGroup(PreparedStatement preparedStmt, int variantNumOverride) throws SQLException {
        try (ResultSet resultSet = preparedStmt.executeQuery()) {
            Map<OwnerInfo, LimitsInfos> map = new HashMap<>();
            while (resultSet.next()) {
                OwnerInfo owner = new OwnerInfo();
                // In order, from the QueryCatalog.buildOperationalLimitsGroupQuery SQL query :
                // equipmentId, equipmentType, networkUuid, variantNum, side, operationallimitgroupid,
                // current_limits_permanent_limit, current_limits_temporary_limits,
                // apparent_power_limits_permanent_limit, apparent_power_limits_temporary_limits,
                // active_power_limits_permanent_limit, active_power_limits_temporary_limits
                owner.setEquipmentId(resultSet.getString(1));
                owner.setEquipmentType(ResourceType.valueOf(resultSet.getString(2)));
                owner.setNetworkUuid(UUID.fromString(resultSet.getString(3)));
                owner.setVariantNum(variantNumOverride);

                if (!map.containsKey(owner)) {
                    map.put(owner, new LimitsInfos());
                }
                LimitsInfos limitsInfos = map.get(owner);

                Integer side = resultSet.getInt(5);
                String operationalLimitsGroupId = resultSet.getString(6);

                // current limits
                addLimit(limitsInfos, operationalLimitsGroupId, side, LimitType.CURRENT, resultSet.getDouble(7), resultSet.getString(8));

                // apparent power limits
                addLimit(limitsInfos, operationalLimitsGroupId, side, LimitType.APPARENT_POWER, resultSet.getDouble(9), resultSet.getString(10));

                // active power limits
                addLimit(limitsInfos, operationalLimitsGroupId, side, LimitType.ACTIVE_POWER, resultSet.getDouble(11), resultSet.getString(12));
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

    public void insertOperationalLimitsGroup(Map<OwnerInfo, LimitsInfos> limitsInfos) {
        Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits = limitsInfos.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTemporaryLimits() != null ? e.getValue().getTemporaryLimits() : List.of()));
        Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits = limitsInfos.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getPermanentLimits() != null ? e.getValue().getPermanentLimits() : List.of()));
        insertOperationalLimitsGroupAttributes(permanentLimits, temporaryLimits);
    }

    public void insertTemporaryLimitsAttributes(Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits) {
        throw new AssertionError("This method should not be called anymore"); // obsolete code called in V211LimitsMigration
    }

    public static Map<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> buildOperationalLimitsGroup(Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits,
                                                                                                                 Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits) {
        Map<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> result = new HashMap<>();

        // permanent limits
        for (Map.Entry<OwnerInfo, List<PermanentLimitAttributes>> entry : permanentLimits.entrySet()) {
            for (PermanentLimitAttributes permanentLimitAttributes : entry.getValue()) {
                OperationalLimitsGroupOwnerInfo owner =
                    new OperationalLimitsGroupOwnerInfo(entry.getKey().getEquipmentId(),
                        entry.getKey().getEquipmentType(),
                        entry.getKey().getNetworkUuid(),
                        entry.getKey().getVariantNum(),
                        permanentLimitAttributes.getOperationalLimitsGroupId(),
                        permanentLimitAttributes.getSide());
                if (!result.containsKey(owner)) {
                    result.put(owner, new LimitsGroupAttributesSqlData());
                }
                LimitsGroupAttributesSqlData limits = result.get(owner);
                if (permanentLimitAttributes.getLimitType() == LimitType.CURRENT) {
                    limits.setCurrentLimitsPermanentLimit(permanentLimitAttributes.getValue());
                } else if (permanentLimitAttributes.getLimitType() == LimitType.APPARENT_POWER) {
                    limits.setApparentPowerLimitsPermanentLimit(permanentLimitAttributes.getValue());
                } else if (permanentLimitAttributes.getLimitType() == LimitType.ACTIVE_POWER) {
                    limits.setActivePowerLimitsPermanentLimit(permanentLimitAttributes.getValue());
                }
            }
        }

        // temporary limits
        for (Map.Entry<OwnerInfo, List<TemporaryLimitAttributes>> entry : temporaryLimits.entrySet()) {
            for (TemporaryLimitAttributes temporaryLimitAttributes : entry.getValue()) {
                OperationalLimitsGroupOwnerInfo owner =
                    new OperationalLimitsGroupOwnerInfo(entry.getKey().getEquipmentId(),
                        entry.getKey().getEquipmentType(),
                        entry.getKey().getNetworkUuid(),
                        entry.getKey().getVariantNum(),
                        temporaryLimitAttributes.getOperationalLimitsGroupId(),
                        temporaryLimitAttributes.getSide());
                if (!result.containsKey(owner)) {
                    result.put(owner, new LimitsGroupAttributesSqlData());
                }
                LimitsGroupAttributesSqlData limits = result.get(owner);
                TemporaryLimitInfosSqlData temporaryLimitInfosSqlData = new TemporaryLimitInfosSqlData(temporaryLimitAttributes.getName(),
                    temporaryLimitAttributes.getValue(),
                    temporaryLimitAttributes.getAcceptableDuration(),
                    temporaryLimitAttributes.isFictitious());
                if (temporaryLimitAttributes.getLimitType() == LimitType.CURRENT) {
                    limits.getCurrentLimitsTemporaryLimits().add(temporaryLimitInfosSqlData);
                } else if (temporaryLimitAttributes.getLimitType() == LimitType.APPARENT_POWER) {
                    limits.getApparentPowerLimitsTemporaryLimits().add(temporaryLimitInfosSqlData);
                } else if (temporaryLimitAttributes.getLimitType() == LimitType.ACTIVE_POWER) {
                    limits.getActivePowerLimitsTemporaryLimits().add(temporaryLimitInfosSqlData);
                }
            }
        }
        return result;
    }

    public void insertOperationalLimitsGroupAttributes(Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits,
                                                       Map<OwnerInfo, List<TemporaryLimitAttributes>> temporaryLimits) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildInsertOperationalLimitsGroupQuery())) {
                Map<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> operationalLimitsGroup = buildOperationalLimitsGroup(permanentLimits, temporaryLimits);
                List<Object> values = new ArrayList<>(12);
                List<Map.Entry<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData>> list = new ArrayList<>(operationalLimitsGroup.entrySet());
                for (List<Map.Entry<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData>> subUnit : Lists.partition(list, BATCH_SIZE)) {
                    for (Map.Entry<OperationalLimitsGroupOwnerInfo, LimitsGroupAttributesSqlData> entry : subUnit) {
                        values.clear();
                        values.add(entry.getKey().getNetworkUuid());
                        values.add(entry.getKey().getVariantNum());
                        values.add(entry.getKey().getEquipmentType().toString());
                        values.add(entry.getKey().getEquipmentId());
                        values.add(entry.getKey().getOperationalLimitsGroupId());
                        values.add(entry.getKey().getSide());
                        values.add(entry.getValue().getCurrentLimitsPermanentLimit());
                        values.add(entry.getValue().getCurrentLimitsTemporaryLimits());
                        values.add(entry.getValue().getApparentPowerLimitsPermanentLimit());
                        values.add(entry.getValue().getApparentPowerLimitsTemporaryLimits());
                        values.add(entry.getValue().getActivePowerLimitsPermanentLimit());
                        values.add(entry.getValue().getActivePowerLimitsTemporaryLimits());
                        bindValues(preparedStmt, values, mapper);
                        preparedStmt.addBatch();
                    }
                    preparedStmt.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public void insertPermanentLimitsAttributes(Map<OwnerInfo, List<PermanentLimitAttributes>> permanentLimits) {
        throw new AssertionError("This method should not be called anymore"); // obsolete code called in V211LimitsMigration
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
                    List<TemporaryLimitAttributes> temporaryLimitAttributes = limitsInfos.get(owner).getTemporaryLimits();
                    if (temporaryLimitAttributes != null) {
                        for (TemporaryLimitAttributes temporaryLimit : temporaryLimitAttributes) {
                            insertTemporaryLimitInEquipment(equipment, temporaryLimit);
                        }
                    }
                    List<PermanentLimitAttributes> permanentLimitAttributes = limitsInfos.get(owner).getPermanentLimits();
                    if (permanentLimitAttributes != null) {
                        for (PermanentLimitAttributes permanentLimit : permanentLimitAttributes) {
                            insertPermanentLimitInEquipment(equipment, permanentLimit);
                        }
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

    public void deleteOperationalLimitsGroup(UUID networkUuid, int variantNum, List<String> equipmentIds) {
        try (var connection = dataSource.getConnection()) {
            try (var preparedStmt = connection.prepareStatement(buildDeleteOperationalLimitsGroupVariantEquipmentINQuery(equipmentIds.size()))) {
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

    public <T extends IdentifiableAttributes> void deleteOperationalLimitsGroup(UUID networkUuid, List<Resource<T>> resources) {
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
        resourceIdsByVariant.forEach((k, v) -> deleteOperationalLimitsGroup(networkUuid, k, v));
    }

    private Set<String> getTombstonedOperationalLimitsIds(Connection connection, UUID networkUuid, int variantNum) {
        Set<String> identifiableIds = new HashSet<>();
        try (var preparedStmt = connection.prepareStatement(buildGetTombstonedExternalAttributesIdsQuery())) {
            preparedStmt.setObject(1, networkUuid);
            preparedStmt.setInt(2, variantNum);
            preparedStmt.setString(3, ExternalAttributesType.OPERATIONAL_LIMIT_GROUP.toString());
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
                            result.addTemporaryLimit(e);
                        });
                    }
                    if (!Double.isNaN(limits.getPermanentLimit())) {
                        result.addPermanentLimit(PermanentLimitAttributes.builder()
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

    public <T extends IdentifiableAttributes> void updateOperationalLimitsGroup(UUID networkUuid, List<Resource<T>> resources, Map<OwnerInfo, LimitsInfos> limitsInfos) {
        deleteOperationalLimitsGroup(networkUuid, resources);
        insertOperationalLimitsGroup(limitsInfos);
        insertTombstonedOperationalLimitsGroup(networkUuid, limitsInfos, resources);
    }

    public <T extends BranchAttributes> void updateOperationalLimitsGroupWithLazyLoading(UUID networkUuid, List<Resource<T>> resources) {
        // Update limits for resources with at least one operational limit group
        Map<Integer, List<Resource<T>>> resourcesByVariant = resources.stream()
                .filter(r -> !r.getAttributes().getOperationalLimitsGroups1().isEmpty() || !r.getAttributes().getOperationalLimitsGroups2().isEmpty())
                .collect(Collectors.groupingBy(Resource::getVariantNum));
        resourcesByVariant.forEach((variantNum, variantResources) ->
                updateOperationalLimitsGroupForVariant(networkUuid, variantNum, variantResources));
    }

    private <T extends BranchAttributes> void updateOperationalLimitsGroupForVariant(UUID networkUuid, Integer variantNum, List<Resource<T>> variantResources) {
        List<String> resourceIds = variantResources.stream()
                .map(Resource::getId)
                .collect(Collectors.toList());

        Map<OwnerInfo, LimitsInfos> existingOperationalLimitsGroup =
                getOperationaLimitsGroupWithInClause(networkUuid, variantNum, EQUIPMENT_ID_COLUMN, resourceIds);

        Map<OwnerInfo, LimitsInfos> limitsInfosFromUpdatedEquipments =
                getLimitsInfosFromEquipments(networkUuid, variantResources);

        Map<OwnerInfo, LimitsInfos> mergedLimitsInfos = existingOperationalLimitsGroup.isEmpty()
                ? limitsInfosFromUpdatedEquipments
                : mergeExistingWithUpdatedOperationalLimitsGroup(existingOperationalLimitsGroup, limitsInfosFromUpdatedEquipments, variantResources);

        deleteOperationalLimitsGroup(networkUuid, variantResources);
        insertOperationalLimitsGroup(mergedLimitsInfos);
        insertTombstonedOperationalLimitsGroup(networkUuid, mergedLimitsInfos, variantResources);
    }

    private <T extends BranchAttributes> Map<OwnerInfo, LimitsInfos> mergeExistingWithUpdatedOperationalLimitsGroup(
            Map<OwnerInfo, LimitsInfos> existingOperationalLimitsGroup,
            Map<OwnerInfo, LimitsInfos> limitsInfosFromUpdatedEquipments,
            List<Resource<T>> variantResources) {
        Map<String, Set<String>> updatedOperationalGroupIds1 = getOperationalLimitsGroupIds(variantResources, 1);
        Map<String, Set<String>> updatedOperationalGroupIds2 = getOperationalLimitsGroupIds(variantResources, 2);

        // Merge updated limits with existing limits in DB
        Map<OwnerInfo, LimitsInfos> mergedLimitsInfos = new HashMap<>();
        existingOperationalLimitsGroup.forEach((ownerInfo, existingLimits) -> {
            List<TemporaryLimitAttributes> mergedTemporaryLimits = existingLimits.getTemporaryLimits().stream()
                    .filter(existingLimit -> !(
                            updatedOperationalGroupIds1.get(ownerInfo.getEquipmentId()).contains(existingLimit.getOperationalLimitsGroupId()) && existingLimit.getSide() == 1 ||
                            updatedOperationalGroupIds2.get(ownerInfo.getEquipmentId()).contains(existingLimit.getOperationalLimitsGroupId()) && existingLimit.getSide() == 2))
                    .collect(Collectors.toList());
            List<PermanentLimitAttributes> mergedPermanentLimits = existingLimits.getPermanentLimits().stream()
                .filter(existingLimit -> !(
                    updatedOperationalGroupIds1.get(ownerInfo.getEquipmentId()).contains(existingLimit.getOperationalLimitsGroupId()) && existingLimit.getSide() == 1 ||
                        updatedOperationalGroupIds2.get(ownerInfo.getEquipmentId()).contains(existingLimit.getOperationalLimitsGroupId()) && existingLimit.getSide() == 2))
                .collect(Collectors.toList());

            LimitsInfos newLimitsInfos = limitsInfosFromUpdatedEquipments.get(ownerInfo);
            if (!CollectionUtils.isEmpty(newLimitsInfos.getTemporaryLimits())) {
                mergedTemporaryLimits.addAll(newLimitsInfos.getTemporaryLimits());
            }
            if (!CollectionUtils.isEmpty(newLimitsInfos.getPermanentLimits())) {
                mergedPermanentLimits.addAll(newLimitsInfos.getPermanentLimits());
            }
            mergedLimitsInfos.put(ownerInfo, new LimitsInfos(mergedPermanentLimits, mergedTemporaryLimits));
        });

        // Create updated limits not existing in DB
        limitsInfosFromUpdatedEquipments.forEach((ownerInfo, limitsInfos) -> {
            if (!mergedLimitsInfos.containsKey(ownerInfo)) {
                mergedLimitsInfos.put(ownerInfo, limitsInfos);
            }
        });

        return mergedLimitsInfos;
    }

    private static <T extends BranchAttributes> Map<String, Set<String>> getOperationalLimitsGroupIds(List<Resource<T>> variantResources, int side) {
        return variantResources.stream()
                .collect(Collectors.toMap(
                        Resource::getId,
                        r -> r.getAttributes().getOperationalLimitsGroups(side).keySet()
                ));
    }

    private <T extends IdentifiableAttributes> Set<OwnerInfo> getOperationaLimitsGroupToTombstoneFromEquipment(UUID networkUuid, Map<OwnerInfo, LimitsInfos> externalAttributesToInsert, List<Resource<T>> resources) {
        Set<OwnerInfo> externalAttributesToTombstoneFromEquipment = new HashSet<>();
        for (Resource<T> resource : resources) {
            OwnerInfo ownerInfo = new OwnerInfo(resource.getId(), resource.getType(), networkUuid, resource.getVariantNum());
            if (!externalAttributesToInsert.containsKey(ownerInfo) ||
                CollectionUtils.isEmpty(externalAttributesToInsert.get(ownerInfo).getPermanentLimits()) ||
                CollectionUtils.isEmpty(externalAttributesToInsert.get(ownerInfo).getTemporaryLimits())) {
                externalAttributesToTombstoneFromEquipment.add(ownerInfo);
            }
        }
        return externalAttributesToTombstoneFromEquipment;
    }

    private <T extends IdentifiableAttributes> void insertTombstonedOperationalLimitsGroup(UUID networkUuid, Map<OwnerInfo, LimitsInfos> limitsInfos, List<Resource<T>> resources) {
        try (var connection = dataSource.getConnection()) {
            Map<Integer, List<String>> resourcesByVariant = resources.stream()
                .collect(Collectors.groupingBy(
                    Resource::getVariantNum,
                    Collectors.mapping(Resource::getId, Collectors.toList())
                ));
            Set<OwnerInfo> tombstonedOperationalLimitsGroup = PartialVariantUtils.getExternalAttributesToTombstone(
                resourcesByVariant,
                variantNum -> getNetworkAttributes(connection, networkUuid, variantNum, mappings, mapper),
                (fullVariantNum, variantNum, ids) -> getOperationalLimitsGroupWithInClauseForVariant(connection, networkUuid, fullVariantNum, EQUIPMENT_ID_COLUMN, ids, variantNum).keySet(),
                variantNum -> getTombstonedOperationalLimitsIds(connection, networkUuid, variantNum),
                getOperationaLimitsGroupToTombstoneFromEquipment(networkUuid, limitsInfos, resources)
            );

            try (var preparedStmt = connection.prepareStatement(buildInsertTombstonedExternalAttributesQuery())) {
                for (OwnerInfo ownerInfo : tombstonedOperationalLimitsGroup) {
                    preparedStmt.setObject(1, ownerInfo.getNetworkUuid());
                    preparedStmt.setInt(2, ownerInfo.getVariantNum());
                    preparedStmt.setString(3, ownerInfo.getEquipmentId());
                    preparedStmt.setString(4, ExternalAttributesType.OPERATIONAL_LIMIT_GROUP.toString());
                    preparedStmt.addBatch();
                }
                preparedStmt.executeBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    private boolean containsOperationalLimitsGroup(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId) {
        return map.containsKey(branchId) && map.get(branchId).containsKey(side) && map.get(branchId).get(side).containsKey(groupId);
    }

    private void addElementToOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> map, String branchId, Integer side, String groupId, OperationalLimitsGroupAttributes operationalLimitsGroupAttributes) {
        map.computeIfAbsent(branchId, k -> new HashMap<>())
            .computeIfAbsent(side, k -> new HashMap<>())
            .put(groupId, operationalLimitsGroupAttributes);
    }

    private Optional<OperationalLimitsGroupAttributes> getElementFromOperationalLimitsGroupMap(Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitsGroupMap, String branchId, Integer side, String groupId) {
        return Optional.ofNullable(operationalLimitsGroupMap.get(branchId))
            .map(sideMap -> sideMap.get(side))
            .map(groupMap -> groupMap.get(groupId));
    }

    public Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> convertLimitInfosToOperationalLimitsGroupMap(String equipmentId, LimitsInfos limitsInfos) {
        Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> operationalLimitGroups = new HashMap<>();
        // permanent limits
        if (!CollectionUtils.isEmpty(limitsInfos.getPermanentLimits())) {
            limitsInfos.getPermanentLimits().forEach(permanentLimit -> {
                if (containsOperationalLimitsGroup(operationalLimitGroups, equipmentId, permanentLimit.getSide(), permanentLimit.getOperationalLimitsGroupId())) {
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
        }
        // temporary limits
        if (!CollectionUtils.isEmpty(limitsInfos.getTemporaryLimits())) {
            limitsInfos.getTemporaryLimits().forEach(temporaryLimit -> {
                if (containsOperationalLimitsGroup(operationalLimitGroups, equipmentId, temporaryLimit.getSide(), temporaryLimit.getOperationalLimitsGroupId())) {
                    setTemporaryLimit(operationalLimitGroups.get(equipmentId).get(temporaryLimit.getSide())
                        .get(temporaryLimit.getOperationalLimitsGroupId()), temporaryLimit);
                } else {
                    throw new PowsyblException("a limit groups can not have temporary limits without a permanent limit");
                }
            });
        }
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
        return getElementFromOperationalLimitsGroupMap(operationalLimitsGroupAttributes, branchId, side, operationalLimitsGroupName);
    }

    public List<OperationalLimitsGroupAttributes> getAllOperationalLimitsGroupAttributesForBranchSide(
        UUID networkId, int variantNum, ResourceType type, String branchId, int side) {
        OwnerInfo ownerInfo = new OwnerInfo(branchId, type, networkId, variantNum);
        Optional<LimitsInfos> limitsInfos = Optional.ofNullable(getLimitsInfos(networkId, variantNum, EQUIPMENT_ID_COLUMN, branchId).get(ownerInfo));
        if (limitsInfos.isPresent()) {
            Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>> allBranchOperationalLimitsGroups =
                convertLimitInfosToOperationalLimitsGroupMap(branchId, limitsInfos.get());
            List<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = new ArrayList<>();
            allBranchOperationalLimitsGroups.forEach((equipmentId, sideToGroupsMap) -> {
                if (sideToGroupsMap.get(side) != null) {
                    operationalLimitsGroupAttributes.addAll(sideToGroupsMap.get(side).values().stream().toList());
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
            Optional<OperationalLimitsGroupAttributes> operationalLimitsGroupAttributes = getElementFromOperationalLimitsGroupMap(operationalLimitsGroupAttributesMap, equipmentId, side, selectedOperationalLimitsGroupId);
            operationalLimitsGroupAttributes.ifPresent(attributes ->
                addElementToOperationalLimitsGroupMap(selectedOperationalLimitsGroupAttributes, equipmentId, side, selectedOperationalLimitsGroupId, attributes));
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
