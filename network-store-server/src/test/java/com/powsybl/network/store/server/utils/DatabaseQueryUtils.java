/**
* Copyright (c) 2026, RTE (http://www.rte-france.com)
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package com.powsybl.network.store.server.utils;

import static com.vladmihalcea.sql.SQLStatementCountValidator.*;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
public final class DatabaseQueryUtils {

    private DatabaseQueryUtils() {
        throw new IllegalStateException("Not implemented exception");
    }

    public static void assertRequestsCount(long select, long insert, long update, long delete) {
        assertSelectCount(select);
        assertInsertCount(insert);
        assertUpdateCount(update);
        assertDeleteCount(delete);
    }
}
