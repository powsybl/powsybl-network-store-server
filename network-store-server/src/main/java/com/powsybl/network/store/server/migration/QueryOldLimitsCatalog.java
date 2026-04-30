/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration;

import java.util.Collections;

import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.generateInPlaceholders;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public final class QueryOldLimitsCatalog {
    static final String OPERATIONAL_LIMITS_GROUP_TABLE = "operationallimitsgroup";
    static final String GROUP_ID_COLUMN = "operationallimitgroupid";
    static final String CURRENT_LIMITS_PERMANENT_LIMIT_COLUMN = "current_limits_permanent_limit";
    static final String CURRENT_LIMITS_TEMPORARY_LIMITS_COLUMN = "current_limits_temporary_limits";
    static final String CURRENT_LIMITS_PROPERTIES_COLUMN = "current_limits_properties";
    static final String APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "apparent_power_limits_permanent_limit";
    static final String APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "apparent_power_limits_temporary_limits";
    static final String APPARENT_POWER_LIMITS_PROPERTIES_COLUMN = "apparent_power_limits_properties";
    static final String ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN = "active_power_limits_permanent_limit";
    static final String ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN = "active_power_limits_temporary_limits";
    static final String ACTIVE_POWER_LIMITS_PROPERTIES_COLUMN = "active_power_limits_properties";
    public static final String SIDE_COLUMN = "side";
    static final String PROPERTIES_COLUMN = "properties";

    private QueryOldLimitsCatalog() {

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
            CURRENT_LIMITS_PROPERTIES_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
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
            CURRENT_LIMITS_PROPERTIES_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
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
            CURRENT_LIMITS_PROPERTIES_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            APPARENT_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            APPARENT_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PERMANENT_LIMIT_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_TEMPORARY_LIMITS_COLUMN + ", " +
            ACTIVE_POWER_LIMITS_PROPERTIES_COLUMN + ", " +
            PROPERTIES_COLUMN +
            " from " + OPERATIONAL_LIMITS_GROUP_TABLE +
            " where " + NETWORK_UUID_COLUMN + " = ? and " + VARIANT_NUM_COLUMN + " = ? " +
            " and (" + EQUIPMENT_ID_COLUMN + ", " + GROUP_ID_COLUMN + ", " + SIDE_COLUMN + ") " +
            " in (values " + String.join(",", Collections.nCopies(numberOfValues, "(?, ?, ?)")) + ")";
    }
}
