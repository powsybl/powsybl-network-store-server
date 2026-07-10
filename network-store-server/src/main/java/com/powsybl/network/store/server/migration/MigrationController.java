/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server.migration;

import com.powsybl.network.store.model.NetworkStoreApi;
import com.powsybl.network.store.server.NetworkStoreRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + NetworkStoreApi.VERSION + "/migration")
@Tag(name = "Network store migration")
public class MigrationController {

    @Autowired
    private NetworkStoreRepository repository;

    @PutMapping(value = "/v237limits/{networkId}/{variantNum}")
    @Operation(summary = "Migrate limits of a network")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully migrated limits from V2.37.0 to new model"))
    public ResponseEntity<Void> migrateV237Limits(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "Variant num", required = true) @PathVariable("variantNum") int variantNum) {
        V237LimitsMigration.migrateV237Limits(repository, networkId, variantNum);
        return ResponseEntity.ok().build();
    }
}
