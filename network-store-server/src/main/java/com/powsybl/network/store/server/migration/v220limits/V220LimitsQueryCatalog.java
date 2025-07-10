/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration.v220limits;

import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_ID_COLUMN;
import static com.powsybl.network.store.server.QueryCatalog.EQUIPMENT_TYPE_COLUMN;
import static com.powsybl.network.store.server.QueryCatalog.NETWORK_UUID_COLUMN;
import static com.powsybl.network.store.server.QueryCatalog.VARIANT_NUM_COLUMN;
import static com.powsybl.network.store.server.Utils.generateInPlaceholders;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public final class V220LimitsQueryCatalog {
    public static final String MINIMAL_VALUE_REQUIREMENT_ERROR = "Function should not be called without at least one value.";
    public static final String TEMPORARY_LIMIT_TABLE = "temporarylimits";
    public static final String PERMANENT_LIMIT_TABLE = "permanentlimits";

    private V220LimitsQueryCatalog() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // Temporary Limits
    public static String buildGetV220TemporaryLimitQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", temporarylimits" +
                " from " + TEMPORARY_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

    public static String buildDeleteV220TemporaryLimitsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + TEMPORARY_LIMIT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }

    // Permanent Limits
    public static String buildGetV220PermanentLimitQuery(String columnNameForWhereClause) {
        return "select " + EQUIPMENT_ID_COLUMN + ", " +
                EQUIPMENT_TYPE_COLUMN + ", " +
                NETWORK_UUID_COLUMN + ", " +
                VARIANT_NUM_COLUMN + ", permanentlimits" +
                " from " + PERMANENT_LIMIT_TABLE + " where " +
                NETWORK_UUID_COLUMN + " = ? and " +
                VARIANT_NUM_COLUMN + " = ? and " +
                columnNameForWhereClause + " = ?";
    }

    public static String buildDeleteV220PermanentLimitsVariantEquipmentINQuery(int numberOfValues) {
        if (numberOfValues < 1) {
            throw new IllegalArgumentException(MINIMAL_VALUE_REQUIREMENT_ERROR);
        }
        return "delete from " + PERMANENT_LIMIT_TABLE + " where " +
            NETWORK_UUID_COLUMN + " = ? and " +
            VARIANT_NUM_COLUMN + " = ? and " +
            EQUIPMENT_ID_COLUMN + " in (" + generateInPlaceholders(numberOfValues) + ")";
    }
}
