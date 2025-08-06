/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration;

import com.powsybl.network.store.model.*;
import com.powsybl.network.store.server.NetworkStoreRepository;
import com.powsybl.network.store.server.migration.v214tapchangersteps.V214TapChangerStepsMigration;
import com.powsybl.network.store.server.migration.v221limits.V221OperationalLimitsGroupMigration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + NetworkStoreApi.VERSION + "/migration")
@Tag(name = "Network store migration")
public class MigrationController {

    @Autowired
    private NetworkStoreRepository repository;

    @PutMapping(value = "/v214tapChangeSteps/{networkId}/{variantNum}")
    @Operation(summary = "Migrate tap changer steps of a network")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully migrated tap changer steps from V2.14.0 to new model"))
    public ResponseEntity<Void> migrateV214TapChangerSteps(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "Variant num", required = true) @PathVariable("variantNum") int variantNum) {
        V214TapChangerStepsMigration.migrateV214TapChangerSteps(repository, networkId, variantNum);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/v221limits/{networkId}/{variantNum}")
    @Operation(summary = "Migrate operational limits groups of a network")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully migrated operational limits group from V2.21.0 to new model"))
    public ResponseEntity<Void> migrateV221Limits(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "Variant num", required = true) @PathVariable("variantNum") int variantNum) {
        V221OperationalLimitsGroupMigration.migrateV221OperationalLimitsGroup(repository, networkId, variantNum);
        return ResponseEntity.ok().build();
    }
}
