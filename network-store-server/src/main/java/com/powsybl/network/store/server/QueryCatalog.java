/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.network.store.model.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.powsybl.network.store.server.Mappings.*;
import static com.powsybl.network.store.server.Utils.generateInPlaceholders;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public final class QueryCatalog {

    public static final String MINIMAL_VALUE_REQUIREMENT_ERROR = "Function should not be called without at least one value.";

    static final String VARIANT_ID_COLUMN = "variantId";
    static final String UUID_COLUMN = "uuid";
    public static final String NETWORK_UUID_COLUMN = "networkUuid";
    public static final String VARIANT_NUM_COLUMN = "variantNum";
    static final String FULL_VARIANT_NUM_COLUMN = "fullVariantNum";
    static final String ID_COLUMN = "id";
    static final String VOLTAGE_LEVEL_ID_COLUMN = "voltageLevelId";
    static final String VOLTAGE_LEVEL_ID_1_COLUMN = "voltageLevelId1";
    static final String VOLTAGE_LEVEL_ID_2_COLUMN = "voltageLevelId2";
    static final String VOLTAGE_LEVEL_ID_3_COLUMN = "voltageLevelId3";
    public static final String NAME_COLUMN = "name";
    public static final String EQUIPMENT_TYPE_COLUMN = "equipmentType";
    static final String REGULATING_EQUIPMENT_TYPE_COLUMN = "regulatingEquipmentType";
    static final String REGULATED_EQUIPMENT_TYPE_COLUMN = "regulatedEquipmentType";
    static final String REGULATING_TAP_CHANGER_TYPE = "regulatingTapChangerType";
    public static final String EQUIPMENT_ID_COLUMN = "equipmentId";
    public static final String AREA_ID_COLUMN = "areaId";
    static final String REGULATING_EQUIPMENT_ID = "regulatingEquipmentId";
    public static final String INDEX_COLUMN = "index";
    public static final String TAPCHANGER_TYPE_COLUMN = "tapChangerType";
    public static final String TAPCHANGER_STEPS_COLUMN = "tapchangersteps";
    public static final String TAP_CHANGER_TYPE = "tapchangertype";
    public static final String ALPHA_COLUMN = "alpha";
    public static final String TAP_CHANGER_STEP_TABLE = "tapchangersteps";
    public static final String AREA_BOUNDARY_TABLE = "areaboundary";
    public static final String REACTIVE_CAPABILITY_CURVE_POINT_TABLE = "reactiveCapabilityCurvePoint";
    static final String REGULATING_POINT_TABLE = "regulatingPoint";
    static final String REGULATION_MODE = "regulationMode";
    public static final String SIDE_COLUMN = "side";
    private static final String TYPE_COLUMN = "type";
    static final String REGULATING = "regulating";
    public static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID1 = "selectedoperationallimitsgroupid1";
    public static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID2 = "selectedoperationallimitsgroupid2";
    private static final Predicate<String> CLONE_PREDICATE = column -> !column.equals(UUID_COLUMN) && !column.equals(VARIANT_ID_COLUMN)
            && !column.equals(NAME_COLUMN) && !column.equals(FULL_VARIANT_NUM_COLUMN);
    private static final String TOMBSTONED_IDENTIFIABLE_TABLE = "tombstonedidentifiable";
    private static final String TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE = "tombstonedexternalattributes";
    static final String OPERATIONAL_LIMITS_GROUP_TABLE = "operationallimitsgroup";
    static final String GROUP_ID_COLUMN = "operationallimitgroupid";
    static final String CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN = "current_limits_permanent_limit";
    static final String CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN = "current_limits_temporary_limits";
    static final String APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "apparent_power_limits_permanent_limit";
    static final String APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "apparent_power_limits_temporary_limits";
    static final String ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "active_power_limits_permanent_limit";
    static final String ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "active_power_limits_temporary_limits";
    static final String PROPERTIES_COLUMN = "properties";

    private QueryCatalog() {
    }

    public static String buildGetIdentifiableQuery(String tableName, Collection<String> columns) {
        return "select " +
                String.join(", ", columns) +
                " from " + tableName +
                " where " + NETWORK_UUID_COLUMN + " = ?" +
                " and " + VARIANT_NUM_COLUMN + " = ?" +
                " and " + ID_COLUMN + " = ?";
    }

    public static String buildGetSelectedOperationalLimitsGroupsQuery(String tableName) {
        return "select " + ID_COLUMN + ", " +
            SELECTED_OPERATIONAL_LIMITS_GROUP_ID1 + ", " +
            SELECTED_OPERATIONAL_LIMITS_GROUP_ID2 +
            " from " + tableName +
            " where " + NETWORK_UUID_COLUMN + " = ?" +
            " and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildGetNetworkQuery(Collection<String> columns) {
        return "select " + ID_COLUMN + ", " +
                String.join(", ", columns) +
                " from " + NETWORK_TABLE +
                " where " + UUID_COLUMN + " = ?" +
                " and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildGetIdentifiablesQuery(String tableName, Collection<String> columns) {
        return "select " + ID_COLUMN + ", " +
                String.join(", ", columns) +
                " from " + tableName +
                " where " + NETWORK_UUID_COLUMN + " = ?" +
                " and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildGetIdentifiablesWithInClauseQuery(String tableName, Collection<String> columns, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "select " + ID_COLUMN + ", " +
                String.join(", ", columns) +
                " from " + tableName +
                " where " + NETWORK_UUID_COLUMN + " = ?" +
                " and " + VARIANT_NUM_COLUMN + " = ?" +
                " and " + ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildGetIdentifiablesInContainerQuery(String tableName, Collection<String> columns, Set<String> containerColumns) {
        StringBuilder sql = new StringBuilder()
                .append("select ").append(ID_COLUMN).append(", ")
                .append(String.join(", ", columns))
                .append(" from ").append(tableName)
                .append(" where ").append(NETWORK_UUID_COLUMN).append(" = ?")
                .append(" and ").append(VARIANT_NUM_COLUMN).append(" = ?")
                .append(" and (");
        var it = containerColumns.iterator();
        while (it.hasNext()) {
            String containerColumn = it.next();
            sql.append(containerColumn).append(" = ?");
            if (it.hasNext()) {
                sql.append(" or ");
            }
        }
        sql.append(")");
        return sql.toString();
    }

    public static String buildDeleteIdentifiablesQuery(String tableName, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "delete from " + tableName + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteNetworkQuery() {
        return "delete from " + NETWORK_TABLE + " where " + UUID_COLUMN + " = ?";
    }

    public static String buildDeleteNetworkVariantQuery() {
        return "delete from " + NETWORK_TABLE + " where " + UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteIdentifiablesQuery(String tableName) {
        return "delete from " + tableName + " where " + NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteIdentifiablesVariantQuery(String tableName) {
        return "delete from " + tableName + " where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildInsertNetworkQuery(String tableName, Collection<String> columns) {
        return "insert into " + tableName +
                "(" + VARIANT_NUM_COLUMN + ", " + ID_COLUMN + ", " + String.join(", ", columns) +
                ") values (?, ?, " + columns.stream().map(s -> "?").collect(Collectors.joining(", "))
                + ")";
    }

    public static String buildInsertIdentifiableQuery(String tableName, Collection<String> columns) {
        return "insert into " + tableName +
                "(" + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + ID_COLUMN + ", " + String.join(", ", columns) +
                ") values (?, ?, ?, " + columns.stream().map(s -> "?").collect(Collectors.joining(", "))
                + ")";
    }

    public static String buildGetIdentifiableForAllTablesQuery() {
        StringBuilder sql = new StringBuilder();
        sql.append("select * from (select ?::uuid networkUuid, ?::int variantNum, ?::varchar id) a");
        for (String table : ELEMENT_TABLES) {
            sql.append(" left outer join ").append(table)
                    .append(" on a.id = ")
                    .append(table)
                    .append(".id and a.networkUuid = ")
                    .append(table)
                    .append(".networkUuid and a.variantNum = ")
                    .append(table)
                    .append(".variantNum");
        }
        return sql.toString();
    }

    public static String buildGetNetworkInfos() {
        return "select " + UUID_COLUMN + ", " + ID_COLUMN +
                " from " + NETWORK_TABLE +
                " where " + VARIANT_NUM_COLUMN + " = " + Resource.INITIAL_VARIANT_NUM;
    }

    public static String buildGetVariantsInfos() {
        return "select " + VARIANT_ID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + FULL_VARIANT_NUM_COLUMN +
                " from " + NETWORK_TABLE +
                " where " + UUID_COLUMN + " = ?";
    }

    public static String buildUpdateIdentifiableQuery(String tableName, Collection<String> columns, String columnToAddToWhereClause) {
        StringBuilder query = new StringBuilder("update ")
                .append(tableName)
                .append(" set ");
        var it = columns.iterator();
        while (it.hasNext()) {
            String column = it.next();
            if (!column.equals(columnToAddToWhereClause)) {
                query.append(column).append(" = ?");
                if (it.hasNext()) {
                    query.append(", ");
                }
            }
        }
        query.append(" where ").append(NETWORK_UUID_COLUMN).append(" = ? and ")
                .append(VARIANT_NUM_COLUMN).append(" = ? and ")
                .append(ID_COLUMN).append(" = ?");
        if (columnToAddToWhereClause != null) {
            query.append(" and ").append(columnToAddToWhereClause).append(" = ?");
        }
        return query.toString();
    }

    public static String buildUpdateInjectionSvQuery(String tableName) {
        return "update " +
                tableName +
                " set p = ?" +
                ", q = ?" +
                " where " + NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                ID_COLUMN + " = ?";
    }

    public static String buildUpdateBranchSvQuery(String tableName) {
        return "update " +
                tableName +
                " set p1 = ?" +
                ", q1 = ?" +
                ", p2 = ?" +
                ", q2 = ?" +
                " where " + NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                ID_COLUMN + " = ?";
    }

    public static String buildUpdateThreeWindingsTransformerSvQuery() {
        return "update " +
                THREE_WINDINGS_TRANSFORMER_TABLE +
                " set p1 = ?" +
                ", q1 = ?" +
                ", p2 = ?" +
                ", q2 = ?" +
                ", p3 = ?" +
                ", q3 = ?" +
                " where " + NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                ID_COLUMN + " = ?";
    }

    public static String buildUpdateVoltageLevelSvQuery() {
        return "update " +
                VOLTAGE_LEVEL_TABLE +
                " set calculatedbusesforbusview = ?" +
                ", calculatedbusesforbusbreakerview = ?" +
                " where " + NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                ID_COLUMN + " = ?";
    }

    public static String buildUpdateNetworkQuery(Collection<String> columns) {
        StringBuilder query = new StringBuilder("update ")
                .append(NETWORK_TABLE)
                .append(" set ").append(ID_COLUMN).append(" = ?");
        columns.forEach(column -> {
            if (!column.equals(UUID_COLUMN) && !column.equals(VARIANT_ID_COLUMN)) {
                query.append(", ").append(column).append(" = ?");
            }
        });
        query.append(" where ").append(UUID_COLUMN).append(" = ?")
                .append(" and ").append(VARIANT_NUM_COLUMN).append(" = ?");
        return query.toString();
    }

    public static String buildCloneIdentifiablesQuery(String tableName, Collection<String> columns) {
        return "insert into " + tableName + "(" +
                VARIANT_NUM_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                ID_COLUMN + ", " +
                String.join(",", columns) +
                ") " +
                "select " +
                "?" + "," +
                "?" + "," +
                ID_COLUMN + "," +
                String.join(",", columns) +
                " from " + tableName + " " +
                "where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildCloneNetworksQuery(Collection<String> columns) {
        return "insert into " + NETWORK_TABLE + "(" +
                VARIANT_NUM_COLUMN + ", " +
                VARIANT_ID_COLUMN + ", " +
                UUID_COLUMN + ", " +
                ID_COLUMN + ", " +
                FULL_VARIANT_NUM_COLUMN + ", " +
                columns.stream().filter(CLONE_PREDICATE).collect(Collectors.joining(",")) +
                ") " +
                "select" + " " +
                "?" + ", " +
                "?" + ", " +
                UUID_COLUMN + ", " +
                ID_COLUMN + ", " +
                "?" + ", " +
                columns.stream().filter(CLONE_PREDICATE).collect(Collectors.joining(",")) +
                " from network" + " " +
                "where uuid = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    // Tombstoned identifiables
    public static String buildInsertTombstonedIdentifiablesQuery() {
        return "insert into " + TOMBSTONED_IDENTIFIABLE_TABLE + " (" + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + EQUIPMENT_ID_COLUMN + ") " +
                "values (?, ?, ?)";
    }

    public static String buildGetTombstonedIdentifiablesIdsQuery() {
        return "select " + EQUIPMENT_ID_COLUMN + " FROM " + TOMBSTONED_IDENTIFIABLE_TABLE + " WHERE " + NETWORK_UUID_COLUMN + " = ? AND " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildIsTombstonedIdentifiableQuery() {
        return "select 1 from " + TOMBSTONED_IDENTIFIABLE_TABLE +
                " where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ? and " + EQUIPMENT_ID_COLUMN + " = ? limit 1";
    }

    public static String buildDeleteTombstonedIdentifiablesQuery() {
        return "delete from " + TOMBSTONED_IDENTIFIABLE_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteTombstonedIdentifiablesVariantQuery() {
        return "delete from " + TOMBSTONED_IDENTIFIABLE_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildCloneTombstonedIdentifiablesQuery() {
        return "insert into " + TOMBSTONED_IDENTIFIABLE_TABLE + " (" +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                EQUIPMENT_ID_COLUMN + ") " +
                "select " +
                "?" + "," +
                "?" + "," +
                EQUIPMENT_ID_COLUMN +
                " from " + TOMBSTONED_IDENTIFIABLE_TABLE + " " +
                "where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ? ";
    }

    // Operational limits
    public static String buildCloneOperationalLimitsGroupQuery() {
        return "insert into " + OPERATIONAL_LIMITS_GROUP_TABLE + "(" + EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ", " +
                CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                PROPERTIES_COLUMN + ") " +
                "select " + EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN + ", ?, ?, " +
                GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ", " +
                CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " + ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                PROPERTIES_COLUMN +
                " from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " + NETWORK_UUID_COLUMN +
                " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteOperationalLimitsGroupQuery() {
        return "delete from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteOperationalLimitsGroupVariantQuery() {
        return "delete from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    // Tombstoned external attributes
    public static String buildInsertTombstonedExternalAttributesQuery() {
        return "insert into " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE + " (" + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + EQUIPMENT_ID_COLUMN + ", " + TYPE_COLUMN + ") " +
                "values (?, ?, ?, ?)";
    }

    public static String buildGetTombstonedExternalAttributesIdsQuery() {
        return "select " + EQUIPMENT_ID_COLUMN + " FROM " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE + " WHERE " + NETWORK_UUID_COLUMN + " = ? AND " + VARIANT_NUM_COLUMN + " = ? AND " + TYPE_COLUMN + " = ?";
    }

    public static String buildDeleteTombstonedExternalAttributesQuery() {
        return "delete from " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteTombstonedExternalAttributesVariantQuery() {
        return "delete from " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildCloneTombstonedExternalAttributesQuery() {
        return "insert into " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE + " (" +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                EQUIPMENT_ID_COLUMN + ", " +
                TYPE_COLUMN + ") " +
                "select " +
                "?" + "," +
                "?" + "," +
                EQUIPMENT_ID_COLUMN + "," +
                TYPE_COLUMN +
                " from " + TOMBSTONED_EXTERNAL_ATTRIBUTES_TABLE + " " +
                "where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ? ";
    }

    public static String buildDeleteOperationalLimitsGroupVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteOperationalLimitsGroupByGroupIdAndSideAndIdentifiableIdINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "delete from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                "(" + EQUIPMENT_ID_COLUMN + ", " + GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ") " +
                "in (" + String.join(", ", Collections.nCopies(numberOfValues, "(?, ?, ?)")) + ")";
    }

    // Reactive Capability Curve Point
    public static String buildCloneReactiveCapabilityCurvePointsQuery() {
        return "insert into " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + "(" + EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN +
                ", " + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", minQ, maxQ, p) select " +
                EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN +
                ", ?, ?, minQ, maxQ, p from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " + NETWORK_UUID_COLUMN +
                " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildReactiveCapabilityCurvePointQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                "minQ, maxQ, p " +
                "from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

    public static String buildReactiveCapabilityCurvePointWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                "minQ, maxQ, p " +
                "from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildInsertReactiveCapabilityCurvePointsQuery() {
        return "insert into " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + "(" +
                EQUIPMENT_ID_COLUMN + ", " + EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + " ," +
                VARIANT_NUM_COLUMN + ", minQ, maxQ, p)" +
                " values (?, ?, ?, ?, ?, ?, ?)";
    }

    public static String buildDeleteReactiveCapabilityCurvePointsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteReactiveCapabilityCurvePointsVariantQuery() {
        return "delete from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteReactiveCapabilityCurvePointsQuery() {
        return "delete from " + REACTIVE_CAPABILITY_CURVE_POINT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ?";
    }

    // Area Boundaries
    public static String buildCloneAreaBoundariesQuery() {
        return "insert into " + AREA_BOUNDARY_TABLE + " (" + AREA_ID_COLUMN + ", " + NETWORK_UUID_COLUMN + ", "
            + VARIANT_NUM_COLUMN + ", boundarydanglinglineid, terminalconnectableid, terminalside, ac) select " +
            AREA_ID_COLUMN +
            ", ?, ?, boundarydanglinglineid, terminalconnectableid, terminalside, ac from " + AREA_BOUNDARY_TABLE + " where " + NETWORK_UUID_COLUMN +
            " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildAreaBoundaryQuery(String columnNameForWhereClause) {
        String baseQuery = "select " + AREA_ID_COLUMN + ", " +
            NETWORK_UUID_COLUMN + ", " +
            "boundarydanglinglineid, terminalconnectableid, terminalside, ac " +
            "from " + AREA_BOUNDARY_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? ";
        if (columnNameForWhereClause != null) {
            baseQuery += " and " + columnNameForWhereClause + " = ?";
        }
        return baseQuery;
    }

    public static String buildAreaBoundaryWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + AREA_ID_COLUMN + ", " +
            NETWORK_UUID_COLUMN + ", " +
            "boundarydanglinglineid, terminalconnectableid, terminalside, ac " +
            "from " + AREA_BOUNDARY_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildInsertAreaBoundariesQuery() {
        return "insert into " + AREA_BOUNDARY_TABLE + " (" +
            AREA_ID_COLUMN + ", " +
            NETWORK_UUID_COLUMN + " ," +
            VARIANT_NUM_COLUMN + ", boundarydanglinglineid, terminalconnectableid, terminalside, ac)" +
            " values (?, ?, ?, ?, ?, ?, ?)";
    }

    public static String buildDeleteAreaBoundariesVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + AREA_BOUNDARY_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            AREA_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteAreaBoundariesVariantQuery() {
        return "delete from " + AREA_BOUNDARY_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteAreaBoundariesQuery() {
        return "delete from " + AREA_BOUNDARY_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ?";
    }

    // Regulating point
    public static String buildInsertRegulatingPointsQuery() {
        return "insert into " + REGULATING_POINT_TABLE + " (" +
            NETWORK_UUID_COLUMN + " ," + VARIANT_NUM_COLUMN + ", " + REGULATING_EQUIPMENT_ID + ", " + REGULATING_EQUIPMENT_TYPE_COLUMN + ", " +
            REGULATING_TAP_CHANGER_TYPE + ", " + REGULATION_MODE +
            ", localTerminalConnectableId, localTerminalSide, regulatingterminalconnectableid, regulatingterminalside, " +
            REGULATED_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING + ")" +
            " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public static String buildCloneRegulatingPointsQuery() {
        return "insert into " + REGULATING_POINT_TABLE + " (" + NETWORK_UUID_COLUMN + " ," + VARIANT_NUM_COLUMN + ", " +
             REGULATING_EQUIPMENT_ID + ", " + REGULATING_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING_TAP_CHANGER_TYPE + ", " + REGULATION_MODE +
            ", localTerminalConnectableId, localTerminalSide, regulatingTerminalConnectableId, regulatingTerminalSide, " +
            REGULATED_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING + ") select ?, ?" + ", " + REGULATING_EQUIPMENT_ID + ", " +
            REGULATING_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING_TAP_CHANGER_TYPE + ", " + REGULATION_MODE +
            ", localTerminalConnectableId, localTerminalSide, regulatingTerminalConnectableId, regulatingTerminalSide, "
            + REGULATED_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING + " from " + REGULATING_POINT_TABLE + " where " + NETWORK_UUID_COLUMN +
            " = ? and " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildRegulatingPointsQuery() {
        return "select " +
            NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            REGULATING_EQUIPMENT_ID + ", " + REGULATING_TAP_CHANGER_TYPE + ", " + REGULATION_MODE + ", localterminalconnectableid, localterminalside, " +
            "regulatingterminalconnectableid, regulatingterminalside, " + REGULATING +
            " from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            REGULATING_EQUIPMENT_TYPE_COLUMN + " = ?";
    }

    public static String buildRegulatingPointsIdsQuery() {
        return "select " + REGULATING_EQUIPMENT_ID + " FROM " + REGULATING_POINT_TABLE + " WHERE " + NETWORK_UUID_COLUMN + " = ? AND " + VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildRegulatingPointsWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            REGULATING_EQUIPMENT_ID + ", " + REGULATING_TAP_CHANGER_TYPE + ", " + REGULATION_MODE + ", localterminalconnectableid, localterminalside, " +
            "regulatingterminalconnectableid, regulatingterminalside, " + REGULATING
            + " from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            REGULATING_EQUIPMENT_TYPE_COLUMN + " = ? and " +
            columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteRegulatingPointsVariantQuery() {
        return "delete from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteRegulatingPointsQuery() {
        return "delete from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteRegulatingPointsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            REGULATING_EQUIPMENT_TYPE_COLUMN + " = ? and " +
            REGULATING_EQUIPMENT_ID + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    // regulating equipments
    public static String buildRegulatingEquipmentsQuery() {
        return "select " + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + REGULATING_EQUIPMENT_ID + ", "
            + "regulatingterminalconnectableid," + REGULATING_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING_TAP_CHANGER_TYPE
            + " from " + REGULATING_POINT_TABLE + " where " + NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " + REGULATED_EQUIPMENT_TYPE_COLUMN + " = ?";
    }

    public static String buildRegulatingEquipmentsForOneEquipmentQuery() {
        return "select " + REGULATING_EQUIPMENT_ID + ", " + REGULATING_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING_TAP_CHANGER_TYPE
            + " from " + REGULATING_POINT_TABLE + " where " + NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " + REGULATED_EQUIPMENT_TYPE_COLUMN + " = ? and " + "regulatingterminalconnectableid = ?";
    }

    public static String buildRegulatingEquipmentsWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "select " + NETWORK_UUID_COLUMN + ", " + VARIANT_NUM_COLUMN + ", " + REGULATING_EQUIPMENT_ID + ", "
            + "regulatingterminalconnectableid," + REGULATING_EQUIPMENT_TYPE_COLUMN + ", " + REGULATING_TAP_CHANGER_TYPE
            + " from " + REGULATING_POINT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            REGULATED_EQUIPMENT_TYPE_COLUMN + " = ? and " +
            columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    // Tap Changer Steps
    public static String buildCloneTapChangerStepQuery() {
        return "insert into " + TAP_CHANGER_STEP_TABLE + "(" +
                EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + "," +
                VARIANT_NUM_COLUMN + "," +
                TAPCHANGER_TYPE_COLUMN + ", " +
                TAPCHANGER_STEPS_COLUMN + ") " +
                "select " +
                EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                "?, ?, " +
                TAPCHANGER_TYPE_COLUMN + ", " +
                TAPCHANGER_STEPS_COLUMN +
                " from " + TAP_CHANGER_STEP_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ? ";
    }

    public static String buildTapChangerStepQuery(String columnNameForWhereClause) {
        return "select " +
            EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            TAP_CHANGER_TYPE + ", " +
            TAPCHANGER_STEPS_COLUMN +
            " from " + TAP_CHANGER_STEP_TABLE + " " +
            "where " +
            NETWORK_UUID_COLUMN + " = ?" + " and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            columnNameForWhereClause + " = ?";
    }

    public static String buildTapChangerStepWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " +
                EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                TAP_CHANGER_TYPE + ", " +
                TAPCHANGER_STEPS_COLUMN +
                " from " + TAP_CHANGER_STEP_TABLE + " " +
                "where " +
                NETWORK_UUID_COLUMN + " = ?" + " and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildInsertTapChangerStepQuery() {
        return "insert into " + TAP_CHANGER_STEP_TABLE +
                "(" +
                EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + "," +
                VARIANT_NUM_COLUMN + "," +
                TAPCHANGER_TYPE_COLUMN + ", " +
                TAPCHANGER_STEPS_COLUMN + ") " +
                " values (?, ?, ?, ?, ?, ?)";
    }

    public static String buildDeleteTapChangerStepQuery() {
        return "delete from " + TAP_CHANGER_STEP_TABLE +
               " where " +
               NETWORK_UUID_COLUMN + " = ?";
    }

    public static String buildDeleteTapChangerStepVariantQuery() {
        return "delete from " + TAP_CHANGER_STEP_TABLE +
               " where " +
               NETWORK_UUID_COLUMN + " = ?" + " and " +
               VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildDeleteTapChangerStepVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + TAP_CHANGER_STEP_TABLE +
                " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildGetIdsQuery(String table) {
        return "select " + ID_COLUMN +
                " from " + table + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ?";
    }

    public static String buildInsertOperationalLimitsGroupQuery() {
        return "insert into " + OPERATIONAL_LIMITS_GROUP_TABLE + " (" +
            NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            EQUIPMENT_ID_COLUMN + ", " +
            GROUP_ID_COLUMN + ", " +
            SIDE_COLUMN + ", " +
            CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            PROPERTIES_COLUMN + ")" +
            " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    public static String buildOperationalLimitsGroupQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                SIDE_COLUMN + "," +
                GROUP_ID_COLUMN + "," +
                CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                PROPERTIES_COLUMN +
                " from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

    public static String buildOperationalLimitsGroupWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
            EQUIPMENT_TYPE_COLUMN + ", " +
            NETWORK_UUID_COLUMN + ", " +
            VARIANT_NUM_COLUMN + ", " +
            SIDE_COLUMN + "," +
            GROUP_ID_COLUMN + "," +
            CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            PROPERTIES_COLUMN +
            " from " + OPERATIONAL_LIMITS_GROUP_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildSelectedOperationalLimitsGroupQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }

        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                SIDE_COLUMN + "," +
                GROUP_ID_COLUMN + "," +
                CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
                ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
                PROPERTIES_COLUMN +
                " from " + OPERATIONAL_LIMITS_GROUP_TABLE +
                " where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ? " +
                " and (" + EQUIPMENT_ID_COLUMN + ", " + GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ") " +
                " in (values " + String.join(",", Collections.nCopies(numberOfValues, "(?, ?, ?)")) + ")";
    }
}
