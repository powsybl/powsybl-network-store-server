/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import java.util.Collections;

import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.generateInPlaceholders;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public final class QueryLimitsCatalog {
    static final String OPERATIONAL_LIMITS_GROUP_TABLE = "operationallimitsgroup";
    static final String GROUP_ID_COLUMN = "operationallimitgroupid";
    static final String CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN = "current_limits_permanent_limit";
    static final String CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN = "current_limits_temporary_limits";
    static final String APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "apparent_power_limits_permanent_limit";
    static final String APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "apparent_power_limits_temporary_limits";
    static final String ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "active_power_limits_permanent_limit";
    static final String ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "active_power_limits_temporary_limits";
    public static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID1 = "selectedoperationallimitsgroupid1";
    public static final String SELECTED_OPERATIONAL_LIMITS_GROUP_ID2 = "selectedoperationallimitsgroupid2";
    public static final String SIDE_COLUMN = "side";
    static final String PROPERTIES_COLUMN = "properties";

    private QueryLimitsCatalog() {

    }

    public static String buildGetSelectedOperationalLimitsGroupsQuery(String tableName) {
        return "select " + ID_COLUMN + ", " +
            SELECTED_OPERATIONAL_LIMITS_GROUP_ID1 + ", " +
            SELECTED_OPERATIONAL_LIMITS_GROUP_ID2 +
            " from " + tableName +
            " where " + NETWORK_UUID_COLUMN + " = ?" +
            " and " + VARIANT_NUM_COLUMN + " = ?";
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

        return "delete from " + OPERATIONAL_LIMITS_GROUP_TABLE + " t " +
            "where t." + NETWORK_UUID_COLUMN + " = ? " +
            " and t." + VARIANT_NUM_COLUMN + " = ? " +
            " and exists (select 1 from (values " +
            String.join(", ", Collections.nCopies(numberOfValues, "(?, ?, ?)")) +
            ") v(" + EQUIPMENT_ID_COLUMN + ", " + GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ") " +
            "where (t." + EQUIPMENT_ID_COLUMN + ", t." + GROUP_ID_COLUMN + ", t." + SIDE_COLUMN + ") = " +
            "      (v." + EQUIPMENT_ID_COLUMN + ", v." + GROUP_ID_COLUMN + ", v." + SIDE_COLUMN + "))";
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

    public static String buildSelectedOperationalLimitsGroupINQuery(int numberOfValues) {
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
