/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration.v211limits;

import static com.powsybl.network.store.server.QueryCatalog.*;
import static com.powsybl.network.store.server.Utils.generateInPlaceholders;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
public final class V211LimitsQueryCatalog {
    public static final String MINIMAL_VALUE_REQUIREMENT_ERROR = "Function should not be called without at least one value.";
    static final String LIMIT_TYPE_COLUMN = "limitType";
    public static final String V211_TEMPORARY_LIMIT_TABLE = "v211temporarylimit";
    public static final String V211_PERMANENT_LIMIT_TABLE = "v211permanentlimit";

    private V211LimitsQueryCatalog() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Temporary Limits
    public static String buildGetV211TemporaryLimitWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", " +
                "operationallimitsgroupid, side, limittype, name, value_, acceptableduration, fictitious" +
                " from " + V211_TEMPORARY_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildDeleteV211TemporaryLimitsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + V211_TEMPORARY_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildGetV211TemporaryLimitQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", operationallimitsgroupid, side, limittype, name, value_, acceptableduration, fictitious" +
                " from " + V211_TEMPORARY_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

    // Permanent Limits
    public static String buildDeleteV211PermanentLimitsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + V211_PERMANENT_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildGetV211PermanentLimitWithInClauseQuery(String columnNameForInClause, int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", operationallimitsgroupid, side, limittype, value_ " +
                " from " + V211_PERMANENT_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForInClause + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    public static String buildGetV211PermanentLimitQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", operationallimitsgroupid, side, limittype, value_" +
                " from " + V211_PERMANENT_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

}
