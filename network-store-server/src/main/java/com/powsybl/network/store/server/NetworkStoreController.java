/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.server;

import com.powsybl.network.store.model.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
@RestController
@RequestMapping(value = "/" + NetworkStoreApi.VERSION + "/networks")
@Tag(name = "Network store")
public class NetworkStoreController {

    @Autowired
    private NetworkStoreRepository repository;

    @Autowired
    private NetworkStoreObserver networkStoreObserver;

    private <T extends IdentifiableAttributes> ResponseEntity<TopLevelDocument<T>> get(Supplier<Optional<Resource<T>>> f) {
        Optional<Resource<T>> optResource = networkStoreObserver.observeOne("get", f::get);
        return optResource
                .map(resource -> ResponseEntity.ok(TopLevelDocument.of(resource)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(TopLevelDocument.empty()));
    }

    private ResponseEntity<ExtensionAttributesTopLevelDocument> getExtensionAttributes(Supplier<Optional<ExtensionAttributes>> f) {
        return f.get()
                .map(resource -> ResponseEntity.ok(ExtensionAttributesTopLevelDocument.of(resource)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ExtensionAttributesTopLevelDocument.empty()));
    }

    private ResponseEntity<OperationalLimitsGroupAttributesTopLevelDocument> getOperationalLimitsGroupAttributes(Supplier<Optional<OperationalLimitsGroupAttributes>> f) {
        return f.get()
            .map(resource -> ResponseEntity.ok(OperationalLimitsGroupAttributesTopLevelDocument.of(resource)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(OperationalLimitsGroupAttributesTopLevelDocument.empty()));
    }

    private <T extends IdentifiableAttributes> ResponseEntity<Void> createAll(Consumer<List<Resource<T>>> f, List<Resource<T>> resources, ResourceType resourceType) {
        networkStoreObserver.observe("create.all", resourceType, resources.size(), () -> f.accept(resources));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private <T extends Attributes> ResponseEntity<Void> updateAll(Consumer<List<Resource<T>>> f, List<Resource<T>> resources, ResourceType resourceType) {
        networkStoreObserver.observe("update.all", resourceType, resources.size(), () -> f.accept(resources));
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private <T extends IdentifiableAttributes> ResponseEntity<TopLevelDocument<T>> getAll(Supplier<List<Resource<T>>> resourcesSupplier, Integer limit, ResourceType resourceType) {
        List<Resource<T>> resources = networkStoreObserver.observe("get.all", resourceType, resourcesSupplier::get);
        List<Resource<T>> limitedResources;
        if (limit == null || resources.size() < limit) {
            limitedResources = resources;
        } else {
            limitedResources = resources.stream().limit(limit).collect(Collectors.toList());
        }
        TopLevelDocument<T> document = TopLevelDocument.of(limitedResources);
        document.addMeta("totalCount", Integer.toString(resources.size()));
        return ResponseEntity.ok()
                .body(document);
    }

    private ResponseEntity<Void> clone(Runnable r, int numberOfVariants) {
        networkStoreObserver.observeClone("clone", numberOfVariants, r::run);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> removeAll(Consumer<List<String>> c, List<String> ids, ResourceType resourceType) {
        networkStoreObserver.observe("remove.all", resourceType, ids.size(), () -> c.accept(ids));
        return ResponseEntity.ok().build();
    }

    // network

    @GetMapping(produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all networks infos")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get all networks infos"))
    public List<NetworkInfos> getNetworksInfos() {
        return repository.getNetworksInfos();
    }

    @GetMapping(value = "/{networkId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get variants infos for a given network")
    @ApiResponses(value = @ApiResponse(responseCode = "200", description = "Successfully get variants infos"))
    public List<VariantInfos> getNetworks(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID id) {
        return repository.getVariantsInfos(id);
    }

    @GetMapping(value = "/{networkId}/{variantNum}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a network by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get network"),
        @ApiResponse(responseCode = "404", description = "Network has not been found")
    })
    public ResponseEntity<TopLevelDocument<NetworkAttributes>> getNetwork(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID id,
                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum) {
        return get(() -> repository.getNetwork(id, variantNum));
    }

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Create networks")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create networks"))
    public ResponseEntity<Void> createNetworks(@Parameter(description = "Network resources", required = true) @RequestBody List<Resource<NetworkAttributes>> networkResources) {
        return createAll(repository::createNetworks, networkResources, ResourceType.NETWORK);
    }

    @DeleteMapping(value = "/{networkId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a network by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete network"),
        @ApiResponse(responseCode = "404", description = "Network has not been found")
        })
    public ResponseEntity<Void> deleteNetwork(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID id) {
        repository.deleteNetwork(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a network by id (only one variant)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete network variant"),
        @ApiResponse(responseCode = "404", description = "Network has not been found")
    })
    public ResponseEntity<Void> deleteNetwork(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID id,
                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum) {
        repository.deleteNetwork(id, variantNum);
        return ResponseEntity.ok().build();
    }

    @PutMapping(value = "/{networkId}")
    @Operation(summary = "Update network")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update network"))
    public ResponseEntity<Void> updateNetwork(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "network resource", required = true) @RequestBody Resource<NetworkAttributes> networkResources) {

        return updateAll(resources -> repository.updateNetworks(resources), Collections.singletonList(networkResources), ResourceType.NETWORK);
    }

    @PutMapping(value = "/{networkId}/{sourceVariantNum}/to/{targetVariantNum}")
    @Operation(summary = "Clone a network variant")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully clone the network variant"))
    public ResponseEntity<Void> cloneNetworkVariant(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "Source variant number", required = true) @PathVariable("sourceVariantNum") int sourceVariantNum,
                                                    @Parameter(description = "Target variant number", required = true) @PathVariable("targetVariantNum") int targetVariantNum,
                                                    @Parameter(description = "Target variant id") @RequestParam(required = false) String targetVariantId) {
        return clone(() -> repository.cloneNetworkVariant(networkId, sourceVariantNum, targetVariantNum, targetVariantId), 1);
    }

    @PostMapping(value = "/{targetNetworkUuid}")
    @Operation(summary = "Clone a network provided variants to a different network")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully clone the network"))
    public ResponseEntity<Void> cloneNetwork(@Parameter(description = "Target network ID", required = true) @PathVariable("targetNetworkUuid") UUID targetNetworkUuid,
                                             @Parameter(description = "Source network ID", required = true) @RequestParam("duplicateFrom") UUID sourceNetworkId,
                                             @Parameter(description = "List of target variant ID", required = true) @RequestParam("targetVariantIds") List<String> targetVariantIds) {
        return clone(() -> repository.cloneNetwork(targetNetworkUuid, sourceNetworkId, targetVariantIds), targetVariantIds.size());

    }

    @PutMapping(value = "/{networkId}/{sourceVariantId}/toId/{targetVariantId}")
    @Operation(summary = "Clone a network variant")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully clone the network variant"),
        @ApiResponse(responseCode = ErrorObject.CLONE_OVER_EXISTING_STATUS, description = ErrorObject.CLONE_OVER_EXISTING_TITLE),
        @ApiResponse(responseCode = ErrorObject.CLONE_OVER_INITIAL_FORBIDDEN_STATUS, description = ErrorObject.CLONE_OVER_INITIAL_FORBIDDEN_TITLE),
        })
    public ResponseEntity<Void> cloneNetwork(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                             @Parameter(description = "Source variant Id", required = true) @PathVariable("sourceVariantId") String sourceVariantId,
                                             @Parameter(description = "Target variant Id", required = true) @PathVariable("targetVariantId") String targetVariantId,
                                             @Parameter(description = "mayOverwrite") @RequestParam(required = false) boolean mayOverwrite) {
        return clone(() -> repository.cloneNetwork(networkId, sourceVariantId, targetVariantId, mayOverwrite), 1);
    }

    // substation

    @GetMapping(value = "/{networkId}/{variantNum}/substations", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get substations")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get substation list"))
    public ResponseEntity<TopLevelDocument<SubstationAttributes>> getSubstations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                 @Parameter(description = "Max number of substation to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getSubstations(networkId, variantNum), limit, ResourceType.SUBSTATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/substations/{substationId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a substation by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get substation"),
        @ApiResponse(responseCode = "404", description = "Substation has not been found")
        })
    public ResponseEntity<TopLevelDocument<SubstationAttributes>> getSubstation(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                @Parameter(description = "Substation ID", required = true) @PathVariable("substationId") String substationId) {
        return get(() -> repository.getSubstation(networkId, variantNum, substationId));
    }

    @PostMapping(value = "/{networkId}/substations")
    @Operation(summary = "Create substations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully substations"))
    public ResponseEntity<Void> createSubstations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "Substation resources", required = true) @RequestBody List<Resource<SubstationAttributes>> substationResources) {
        return createAll(resource -> repository.createSubstations(networkId, resource), substationResources, ResourceType.SUBSTATION);
    }

    @PutMapping(value = "/{networkId}/substations")
    @Operation(summary = "Update substations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update substations"))
    public ResponseEntity<Void> updateSubstations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "substation resource", required = true) @RequestBody List<Resource<SubstationAttributes>> substationsResources) {
        return updateAll(resources -> repository.updateSubstations(networkId, resources), substationsResources, ResourceType.SUBSTATION);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/substations", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple substations by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted substations"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteSubstations(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of substation IDs to delete", required = true) @RequestBody List<String> substationIds) {
        return removeAll(ids -> repository.deleteSubstations(networkId, variantNum, ids), substationIds, ResourceType.SUBSTATION);
    }


    // voltage level

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get voltage levels")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get voltage level list"))
    public ResponseEntity<TopLevelDocument<VoltageLevelAttributes>> getVoltageLevels(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                     @Parameter(description = "Max number of voltage level to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getVoltageLevels(networkId, variantNum), limit, ResourceType.VOLTAGE_LEVEL);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a voltage level by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get voltage level"),
        @ApiResponse(responseCode = "404", description = "Voltage level has not been found")
        })
    public ResponseEntity<TopLevelDocument<VoltageLevelAttributes>> getVoltageLevel(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                    @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return get(() -> repository.getVoltageLevel(networkId, variantNum, voltageLevelId));
    }

    @PostMapping(value = "/{networkId}/voltage-levels")
    @Operation(summary = "Create voltage levels")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create voltage levels"))
    public ResponseEntity<Void> createVoltageLevels(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "Voltage level resources", required = true) @RequestBody List<Resource<VoltageLevelAttributes>> voltageLevelResources) {
        return createAll(resource -> repository.createVoltageLevels(networkId, resource), voltageLevelResources, ResourceType.VOLTAGE_LEVEL);
    }

    @PutMapping(value = "/{networkId}/voltage-levels")
    @Operation(summary = "Update voltage levels")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update voltage levels"))
    public ResponseEntity<Void> updateVoltageLevels(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "voltage level resources", required = true) @RequestBody List<Resource<VoltageLevelAttributes>> voltageLevelResources) {

        return updateAll(resources -> repository.updateVoltageLevels(networkId, resources), voltageLevelResources, ResourceType.VOLTAGE_LEVEL);
    }

    @PutMapping(value = "/{networkId}/voltage-levels/sv")
    @Operation(summary = "Update voltage levels SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update voltage levels SV"))
    public ResponseEntity<Void> updateVoltageLevelsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                      @Parameter(description = "voltage level SV resources", required = true) @RequestBody List<Resource<VoltageLevelSvAttributes>> voltageLevelResources) {

        return updateAll(resources -> repository.updateVoltageLevelsSv(networkId, resources), voltageLevelResources, ResourceType.VOLTAGE_LEVEL);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/voltage-levels", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple voltage levels by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete voltage levels"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteVoltageLevels(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                    @Parameter(description = "List of voltage level IDs to delete", required = true) @RequestBody List<String> voltageLevelIds) {
        return removeAll(ids -> repository.deleteVoltageLevels(networkId, variantNum, ids), voltageLevelIds, ResourceType.VOLTAGE_LEVEL);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/substations/{substationId}/voltage-levels", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get voltage levels for a substation")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get voltage level list for a substation"))
    public ResponseEntity<TopLevelDocument<VoltageLevelAttributes>> getVoltageLevels(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                     @Parameter(description = "Substation ID", required = true) @PathVariable("substationId") String substationId) {
        return getAll(() -> repository.getVoltageLevels(networkId, variantNum, substationId), null, ResourceType.VOLTAGE_LEVEL);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/busbar-sections", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get busbar sections connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get busbar sections connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<BusbarSectionAttributes>> getVoltageLevelBusbarSections(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                   @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                   @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelBusbarSections(networkId, variantNum, voltageLevelId), null, ResourceType.BUSBAR_SECTION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/switches", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get switches connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get switches connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<SwitchAttributes>> getVoltageLevelSwitches(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                      @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                      @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelSwitches(networkId, variantNum, voltageLevelId), null, ResourceType.SWITCH);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/generators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get generators connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get generators connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<GeneratorAttributes>> getVoltageLevelGenerators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                           @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                           @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelGenerators(networkId, variantNum, voltageLevelId), null, ResourceType.GENERATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/batteries", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get batteries connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get batteries connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<BatteryAttributes>> getVoltageLevelBatteries(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                        @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                        @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelBatteries(networkId, variantNum, voltageLevelId), null, ResourceType.BATTERY);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/loads", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get loads connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get loads connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<LoadAttributes>> getVoltageLevelLoads(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                 @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelLoads(networkId, variantNum, voltageLevelId), null, ResourceType.LOAD);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/shunt-compensators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get shunt compensators connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get shunt compensators connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<ShuntCompensatorAttributes>> getVoltageLevelShuntCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                         @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                         @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelShuntCompensators(networkId, variantNum, voltageLevelId), null, ResourceType.SHUNT_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/vsc-converter-stations", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get static VSC converter stations connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get VSC converter stations connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<VscConverterStationAttributes>> getVoltageLevelVscConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                               @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                               @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelVscConverterStations(networkId, variantNum, voltageLevelId), null, ResourceType.VSC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/lcc-converter-stations", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get static LCC converter stations connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get LCC converter stations connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<LccConverterStationAttributes>> getVoltageLevelLccConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                               @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                               @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelLccConverterStations(networkId, variantNum, voltageLevelId), null, ResourceType.LCC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/static-var-compensators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get static var compensators connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get static var compensators connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<StaticVarCompensatorAttributes>> getVoltageLevelStaticVarCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                 @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelStaticVarCompensators(networkId, variantNum, voltageLevelId), null, ResourceType.STATIC_VAR_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/2-windings-transformers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get 2 windings transformers connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get 2 windings transformers connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<TwoWindingsTransformerAttributes>> getVoltageLevelTwoWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                     @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelTwoWindingsTransformers(networkId, variantNum, voltageLevelId), null, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/3-windings-transformers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get 3 windings transformers connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get 3 windings transformers connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<ThreeWindingsTransformerAttributes>> getVoltageLevelThreeWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                         @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                         @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelThreeWindingsTransformers(networkId, variantNum, voltageLevelId), null, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get lines connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get lines connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<LineAttributes>> getVoltageLevelLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                 @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelLines(networkId, variantNum, voltageLevelId), null, ResourceType.LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/dangling-lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get dangling lines connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get dangling lines connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<DanglingLineAttributes>> getVoltageLevelDanglingLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                 @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelDanglingLines(networkId, variantNum, voltageLevelId), null, ResourceType.DANGLING_LINE);
    }

    // grounds

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/grounds", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get grounds connected to voltage level")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get grounds connected to the voltage level"))
    public ResponseEntity<TopLevelDocument<GroundAttributes>> getVoltageLevelGrounds(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                     @Parameter(description = "Voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelGrounds(networkId, variantNum, voltageLevelId), null, ResourceType.GROUND);
    }

    // generator

    @PostMapping(value = "/{networkId}/generators")
    @Operation(summary = "Create generators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create generators"))
    public ResponseEntity<Void> createGenerators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                 @Parameter(description = "Generator resources", required = true) @RequestBody List<Resource<GeneratorAttributes>> generatorResources) {
        return createAll(resource -> repository.createGenerators(networkId, resource), generatorResources, ResourceType.GENERATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/generators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get generators")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get generator list"))
    public ResponseEntity<TopLevelDocument<GeneratorAttributes>> getGenerators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                               @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                               @Parameter(description = "Max number of generator to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getGenerators(networkId, variantNum), limit, ResourceType.GENERATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/generators/{generatorId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a generator by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get generator"),
        @ApiResponse(responseCode = "404", description = "Generator has not been found")
    })
    public ResponseEntity<TopLevelDocument<GeneratorAttributes>> getGenerator(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                              @Parameter(description = "Generator ID", required = true) @PathVariable("generatorId") String generatorId) {
        return get(() -> repository.getGenerator(networkId, variantNum, generatorId));
    }

    @PutMapping(value = "/{networkId}/generators")
    @Operation(summary = "Update generators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update generators"))
    public ResponseEntity<Void> updateGenerators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                 @Parameter(description = "generator resources", required = true) @RequestBody List<Resource<GeneratorAttributes>> generatorResources) {

        return updateAll(resources -> repository.updateGenerators(networkId, resources), generatorResources, ResourceType.GENERATOR);
    }

    @PutMapping(value = "/{networkId}/generators/sv")
    @Operation(summary = "Update generators SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update generators SV"))
    public ResponseEntity<Void> updateGeneratorsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                   @Parameter(description = "generator SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> generatorResources) {

        return updateAll(resources -> repository.updateGeneratorsSv(networkId, resources), generatorResources, ResourceType.GENERATOR);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/generators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple generators by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete generator"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteGenerators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                 @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                 @Parameter(description = "List of generator IDs to delete", required = true) @RequestBody List<String> generatorIds) {
        return removeAll(ids -> repository.deleteGenerators(networkId, variantNum, ids), generatorIds, ResourceType.GENERATOR);
    }
    // tie line

    @PostMapping(value = "/{networkId}/tie-lines")
    @Operation(summary = "Create tie lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create tie lines"))
    public ResponseEntity<Void> createTieLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Tie line resources", required = true) @RequestBody List<Resource<TieLineAttributes>> tielineResources) {
        return createAll(resource -> repository.createTieLines(networkId, resource), tielineResources, ResourceType.TIE_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/tie-lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get tie lines")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get tie line list"))
    public ResponseEntity<TopLevelDocument<TieLineAttributes>> getTieLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                           @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                           @Parameter(description = "Max number of tie lines to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getTieLines(networkId, variantNum), limit, ResourceType.TIE_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/tie-lines/{tieLineId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a tie line by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get tie line"),
        @ApiResponse(responseCode = "404", description = "Tie line has not been found")
    })
    public ResponseEntity<TopLevelDocument<TieLineAttributes>> getTieLine(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                          @Parameter(description = "Tie Line ID", required = true) @PathVariable("tieLineId") String tieLineId) {
        return get(() -> repository.getTieLine(networkId, variantNum, tieLineId));
    }

    @PutMapping(value = "/{networkId}/tie-lines")
    @Operation(summary = "Update tie lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update tie lines"))
    public ResponseEntity<Void> updateTieLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "tie line resources", required = true) @RequestBody List<Resource<TieLineAttributes>> tieLineResources) {

        return updateAll(resources -> repository.updateTieLines(networkId, resources), tieLineResources, ResourceType.TIE_LINE);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/tie-lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple tie lines by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete tie lines"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteTieLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                               @Parameter(description = "List of tie line IDs to delete", required = true) @RequestBody List<String> tieLinesIds) {
        return removeAll(ids -> repository.deleteTieLines(networkId, variantNum, ids), tieLinesIds, ResourceType.TIE_LINE);
    }
    // area

    @PostMapping(value = "/{networkId}/areas")
    @Operation(summary = "Create areas")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create areas"))
    public ResponseEntity<Void> createAreas(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Area resources", required = true) @RequestBody List<Resource<AreaAttributes>> areaResources) {
        return createAll(resource -> repository.createAreas(networkId, resource), areaResources, ResourceType.AREA);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/areas", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get areas")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get area list"))
    public ResponseEntity<TopLevelDocument<AreaAttributes>> getAreas(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                           @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                           @Parameter(description = "Max number of areas to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getAreas(networkId, variantNum), limit, ResourceType.AREA);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/areas/{areaId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a area by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get area"),
        @ApiResponse(responseCode = "404", description = "Area has not been found")
    })
    public ResponseEntity<TopLevelDocument<AreaAttributes>> getArea(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                          @Parameter(description = "area ID", required = true) @PathVariable("areaId") String areaId) {
        return get(() -> repository.getArea(networkId, variantNum, areaId));
    }

    @PutMapping(value = "/{networkId}/areas")
    @Operation(summary = "Update areas")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update areas"))
    public ResponseEntity<Void> updateAreas(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "areas resources", required = true) @RequestBody List<Resource<AreaAttributes>> areasResources) {

        return updateAll(resources -> repository.updateAreas(networkId, resources), areasResources, ResourceType.AREA);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/areas", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple areas by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted areas"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteAreas(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                               @Parameter(description = "List of area IDs to delete", required = true) @RequestBody List<String> areaIds) {
        return removeAll(ids -> repository.deleteAreas(networkId, variantNum, ids), areaIds, ResourceType.AREA);
    }

    // battery
    @PostMapping(value = "/{networkId}/batteries")
    @Operation(summary = "Create batteries")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create batteries"))
    public ResponseEntity<Void> createBatteries(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                @Parameter(description = "Battery resources", required = true) @RequestBody List<Resource<BatteryAttributes>> batteryResources) {
        return createAll(resource -> repository.createBatteries(networkId, resource), batteryResources, ResourceType.BATTERY);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/batteries", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get batteries")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get batteries list"))
    public ResponseEntity<TopLevelDocument<BatteryAttributes>> getBatteries(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                            @Parameter(description = "Max number of batteries to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getBatteries(networkId, variantNum), limit, ResourceType.BATTERY);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/batteries/{batteryId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a battery by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get battery"),
        @ApiResponse(responseCode = "404", description = "Battery has not been found")
    })
    public ResponseEntity<TopLevelDocument<BatteryAttributes>> getBattery(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                          @Parameter(description = "Battery ID", required = true) @PathVariable("batteryId") String batteryId) {
        return get(() -> repository.getBattery(networkId, variantNum, batteryId));
    }

    @PutMapping(value = "/{networkId}/batteries")
    @Operation(summary = "Update batteries")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update batteries"))
    public ResponseEntity<Void> updateBatteries(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                @Parameter(description = "Battery resources", required = true) @RequestBody List<Resource<BatteryAttributes>> batteryResources) {

        return updateAll(resources -> repository.updateBatteries(networkId, resources), batteryResources, ResourceType.BATTERY);
    }

    @PutMapping(value = "/{networkId}/batteries/sv")
    @Operation(summary = "Update batteries SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update batteries SV"))
    public ResponseEntity<Void> updateBatteriesSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                  @Parameter(description = "Battery SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> batteryResources) {

        return updateAll(resources -> repository.updateBatteriesSv(networkId, resources), batteryResources, ResourceType.BATTERY);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/batteries", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple batteries by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete batteries"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteBattery(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                              @Parameter(description = "List of battery IDs to delete", required = true) @RequestBody List<String> batteryIds) {
        return removeAll(ids -> repository.deleteBatteries(networkId, variantNum, ids), batteryIds, ResourceType.BATTERY);
    }

    // load

    @PostMapping(value = "/{networkId}/loads")
    @Operation(summary = "Create loads")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create loads"))
    public ResponseEntity<Void> createLoads(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "Load resources", required = true) @RequestBody List<Resource<LoadAttributes>> loadResources) {
        return createAll(resource -> repository.createLoads(networkId, resource), loadResources, ResourceType.LOAD);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/loads", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get loads")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get load list"))
    public ResponseEntity<TopLevelDocument<LoadAttributes>> getLoads(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                     @Parameter(description = "Max number of load to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getLoads(networkId, variantNum), limit, ResourceType.LOAD);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/loads/{loadId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a load by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get load"),
        @ApiResponse(responseCode = "404", description = "Load has not been found")
        })
    public ResponseEntity<TopLevelDocument<LoadAttributes>> getLoad(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                    @Parameter(description = "Load ID", required = true) @PathVariable("loadId") String loadId) {
        return get(() -> repository.getLoad(networkId, variantNum, loadId));
    }

    @PutMapping(value = "/{networkId}/loads")
    @Operation(summary = "Update loads")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update loads"))
    public ResponseEntity<Void> updateLoads(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "load resources", required = true) @RequestBody List<Resource<LoadAttributes>> loadResources) {

        return updateAll(resources -> repository.updateLoads(networkId, resources), loadResources, ResourceType.LOAD);
    }

    @PutMapping(value = "/{networkId}/loads/sv")
    @Operation(summary = "Update loads SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update loads SV"))
    public ResponseEntity<Void> updateLoadsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "load SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> loadResources) {

        return updateAll(resources -> repository.updateLoadsSv(networkId, resources), loadResources, ResourceType.LOAD);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/loads", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple loads by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully delete loads"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ResponseEntity<Void> deleteLoads(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                            @Parameter(description = "List of load IDs to delete", required = true) @RequestBody List<String> loadIds) {
        return removeAll(ids -> repository.deleteLoads(networkId, variantNum, ids), loadIds, ResourceType.LOAD);
    }

    // shunt compensator

    @PostMapping(value = "/{networkId}/shunt-compensators")
    @Operation(summary = "Create shunt compensators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create shunt compensators"))
    public ResponseEntity<Void> createShuntCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                        @Parameter(description = "Shunt compensator resources", required = true) @RequestBody List<Resource<ShuntCompensatorAttributes>> shuntResources) {
        return createAll(resource -> repository.createShuntCompensators(networkId, resource), shuntResources, ResourceType.SHUNT_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/shunt-compensators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get shunt compensators")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get shunt compensator list"))
    public ResponseEntity<TopLevelDocument<ShuntCompensatorAttributes>> getShuntCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                             @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                             @Parameter(description = "Max number of shunt compensator to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getShuntCompensators(networkId, variantNum), limit, ResourceType.SHUNT_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/shunt-compensators/{shuntCompensatorId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a shunt compensator by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get shunt compensator"),
        @ApiResponse(responseCode = "404", description = "Shunt compensator has not been found")
        })
    public ResponseEntity<TopLevelDocument<ShuntCompensatorAttributes>> getShuntCompensator(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                            @Parameter(description = "Shunt compensator ID", required = true) @PathVariable("shuntCompensatorId") String shuntCompensatorId) {
        return get(() -> repository.getShuntCompensator(networkId, variantNum, shuntCompensatorId));
    }

    @PutMapping(value = "/{networkId}/shunt-compensators")
    @Operation(summary = "Update shunt compensators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update shunt compensators"))
    public ResponseEntity<Void> updateShuntCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                        @Parameter(description = "shunt compensator resources", required = true) @RequestBody List<Resource<ShuntCompensatorAttributes>> shuntCompensatorResources) {

        return updateAll(resources -> repository.updateShuntCompensators(networkId, resources), shuntCompensatorResources, ResourceType.SHUNT_COMPENSATOR);
    }

    @PutMapping(value = "/{networkId}/shunt-compensators/sv")
    @Operation(summary = "Update shunt compensators SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update shunt compensators SV"))
    public ResponseEntity<Void> updateShuntCompensatorsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                          @Parameter(description = "shunt compensator SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> shuntCompensatorResources) {

        return updateAll(resources -> repository.updateShuntCompensatorsSv(networkId, resources), shuntCompensatorResources, ResourceType.SHUNT_COMPENSATOR);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/shunt-compensators", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple shunt compensators by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted shunt compensators"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteShuntCompensators(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of shunt compensator IDs to delete", required = true) @RequestBody List<String> shuntCompensatorIds) {
        return removeAll(ids -> repository.deleteShuntCompensators(networkId, variantNum, ids), shuntCompensatorIds, ResourceType.SHUNT_COMPENSATOR);
    }

    // VSC converter station

    @PostMapping(value = "/{networkId}/vsc-converter-stations")
    @Operation(summary = "Create VSC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create VSC converter stations"))
    public ResponseEntity<Void> createVscConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                           @Parameter(description = "VSC converter station resources", required = true) @RequestBody List<Resource<VscConverterStationAttributes>> vscConverterStationResources) {
        return createAll(resource -> repository.createVscConverterStations(networkId, resource), vscConverterStationResources, ResourceType.VSC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/vsc-converter-stations", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get VSC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get VSC converter stations list"))
    public ResponseEntity<TopLevelDocument<VscConverterStationAttributes>> getVscConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                   @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                   @Parameter(description = "Max number of VSC converter stations to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getVscConverterStations(networkId, variantNum), limit, ResourceType.VSC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/vsc-converter-stations/{vscConverterStationId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a VSC converter station by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get VSC converter station"),
        @ApiResponse(responseCode = "404", description = "VSC converter station has not been found")
        })
    public ResponseEntity<TopLevelDocument<VscConverterStationAttributes>> getVscConverterStation(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                  @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                  @Parameter(description = "VSC converter station ID", required = true) @PathVariable("vscConverterStationId") String vscConverterStationId) {
        return get(() -> repository.getVscConverterStation(networkId, variantNum, vscConverterStationId));
    }

    @PutMapping(value = "/{networkId}/vsc-converter-stations")
    @Operation(summary = "Update VSC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update VSC converter stations"))
    public ResponseEntity<Void> updateVscConverterStationsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                             @Parameter(description = "VSC converter station resources", required = true) @RequestBody List<Resource<VscConverterStationAttributes>> vscConverterStationResources) {

        return updateAll(resources -> repository.updateVscConverterStations(networkId, resources), vscConverterStationResources, ResourceType.VSC_CONVERTER_STATION);
    }

    @PutMapping(value = "/{networkId}/vsc-converter-stations/sv")
    @Operation(summary = "Update VSC converter stations SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update VSC converter stations SV"))
    public ResponseEntity<Void> updateVscConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                           @Parameter(description = "VSC converter station SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> vscConverterStationResources) {

        return updateAll(resources -> repository.updateVscConverterStationsSv(networkId, resources), vscConverterStationResources, ResourceType.VSC_CONVERTER_STATION);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/vsc-converter-stations", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple VSC converter stations by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted VSC converter stations"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteVscConverterStations(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of VSC converter station IDs to delete", required = true) @RequestBody List<String> vscConverterStationIds) {
        return removeAll(ids -> repository.deleteVscConverterStations(networkId, variantNum, ids), vscConverterStationIds, ResourceType.VSC_CONVERTER_STATION);
    }


    // LCC converter station

    @PostMapping(value = "/{networkId}/lcc-converter-stations")
    @Operation(summary = "Create LCC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create LCC converter stations"))
    public ResponseEntity<Void> createLccConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                           @Parameter(description = "LCC converter station resources", required = true) @RequestBody List<Resource<LccConverterStationAttributes>> lccConverterStationResources) {
        return createAll(resource -> repository.createLccConverterStations(networkId, resource), lccConverterStationResources, ResourceType.LCC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/lcc-converter-stations", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get LCC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get LCC converter stations list"))
    public ResponseEntity<TopLevelDocument<LccConverterStationAttributes>> getLccConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                   @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                   @Parameter(description = "Max number of LCC converter stations to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getLccConverterStations(networkId, variantNum), limit, ResourceType.LCC_CONVERTER_STATION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/lcc-converter-stations/{lccConverterStationId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a LCC converter station by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get LCC converter station"),
        @ApiResponse(responseCode = "404", description = "LCC converter station has not been found")
        })
    public ResponseEntity<TopLevelDocument<LccConverterStationAttributes>> getLccConverterStation(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                  @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                  @Parameter(description = "LCC converter station ID", required = true) @PathVariable("lccConverterStationId") String lccConverterStationId) {
        return get(() -> repository.getLccConverterStation(networkId, variantNum, lccConverterStationId));
    }

    @PutMapping(value = "/{networkId}/lcc-converter-stations")
    @Operation(summary = "Update LCC converter stations")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update LCC converter stations"))
    public ResponseEntity<Void> updateLccConverterStations(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                           @Parameter(description = "LCC converter station resources", required = true) @RequestBody List<Resource<LccConverterStationAttributes>> lccConverterStationResources) {

        return updateAll(resources -> repository.updateLccConverterStations(networkId, resources), lccConverterStationResources, ResourceType.LCC_CONVERTER_STATION);
    }

    @PutMapping(value = "/{networkId}/lcc-converter-stations/sv")
    @Operation(summary = "Update LCC converter stations SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update LCC converter stations SV"))
    public ResponseEntity<Void> updateLccConverterStationsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                             @Parameter(description = "LCC converter station SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> lccConverterStationResources) {

        return updateAll(resources -> repository.updateLccConverterStationsSv(networkId, resources), lccConverterStationResources, ResourceType.LCC_CONVERTER_STATION);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/lcc-converter-stations", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple LCC converter stations by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted LCC converter stations"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteLccConverterStations(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of LCC converter station IDs to delete", required = true) @RequestBody List<String> lccConverterStationIds) {
        return removeAll(ids -> repository.deleteLccConverterStations(networkId, variantNum, ids), lccConverterStationIds, ResourceType.LCC_CONVERTER_STATION);
    }


    // static var compensator

    @PostMapping(value = "/{networkId}/static-var-compensators")
    @Operation(summary = "Create static var compensators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create static var compensators"))
    public ResponseEntity<Void> createStaticVarCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                            @Parameter(description = "Static var compensator resources", required = true) @RequestBody List<Resource<StaticVarCompensatorAttributes>> staticVarCompenstatorResources) {
        return createAll(resource -> repository.createStaticVarCompensators(networkId, resource), staticVarCompenstatorResources, ResourceType.STATIC_VAR_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/static-var-compensators", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get static var compensators")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get static var compensator list"))
    public ResponseEntity<TopLevelDocument<StaticVarCompensatorAttributes>> getStaticVarCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                     @Parameter(description = "Max number of static var compensators to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getStaticVarCompensators(networkId, variantNum), limit, ResourceType.STATIC_VAR_COMPENSATOR);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/static-var-compensators/{staticVarCompensatorId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a static var compensator by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get static var compensator"),
        @ApiResponse(responseCode = "404", description = "Static var compensator has not been found")
        })
    public ResponseEntity<TopLevelDocument<StaticVarCompensatorAttributes>> getStaticVarCompensator(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                    @Parameter(description = "Static var compensator ID", required = true) @PathVariable("staticVarCompensatorId") String staticVarCompensatorId) {
        return get(() -> repository.getStaticVarCompensator(networkId, variantNum, staticVarCompensatorId));
    }

    @PutMapping(value = "/{networkId}/static-var-compensators")
    @Operation(summary = "Update static var compensators")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update static var compensators"))
    public ResponseEntity<Void> updateStaticVarCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                           @Parameter(description = "Static var compensator resources", required = true) @RequestBody List<Resource<StaticVarCompensatorAttributes>> staticVarCompensatorResources) {

        return updateAll(resources -> repository.updateStaticVarCompensators(networkId, resources), staticVarCompensatorResources, ResourceType.STATIC_VAR_COMPENSATOR);
    }

    @PutMapping(value = "/{networkId}/static-var-compensators/sv")
    @Operation(summary = "Update static var compensators SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update static var compensators SV"))
    public ResponseEntity<Void> updateStaticVarCompensatorsSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                              @Parameter(description = "Static var compensator SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> staticVarCompensatorResources) {

        return updateAll(resources -> repository.updateStaticVarCompensatorsSv(networkId, resources), staticVarCompensatorResources, ResourceType.STATIC_VAR_COMPENSATOR);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/static-var-compensators", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple static var compensators by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted static var compensators"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteStaticVarCompensators(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                            @Parameter(description = "List of static var compensator IDs to delete", required = true) @RequestBody List<String> staticVarCompensatorIds) {
        return removeAll(ids -> repository.deleteStaticVarCompensators(networkId, variantNum, ids), staticVarCompensatorIds, ResourceType.STATIC_VAR_COMPENSATOR);
    }


    // busbar section

    @PostMapping(value = "/{networkId}/busbar-sections")
    @Operation(summary = "Create busbar sections")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create busbar sections"))
    public ResponseEntity<Void> createBusbarSections(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                     @Parameter(description = "Busbar section resources", required = true) @RequestBody List<Resource<BusbarSectionAttributes>> busbarSectionResources) {
        return createAll(resource -> repository.createBusbarSections(networkId, resource), busbarSectionResources, ResourceType.BUSBAR_SECTION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/busbar-sections", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get busbar sections")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get busbar section list"))
    public ResponseEntity<TopLevelDocument<BusbarSectionAttributes>> getBusbarSections(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                       @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                       @Parameter(description = "Max number of busbar section to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getBusbarSections(networkId, variantNum), limit, ResourceType.BUSBAR_SECTION);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/busbar-sections/{busbarSectionId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a busbar section by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get busbar section"),
        @ApiResponse(responseCode = "404", description = "Busbar section has not been found")
        })
    public ResponseEntity<TopLevelDocument<BusbarSectionAttributes>> getBusbarSection(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                      @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                      @Parameter(description = "Busbar section ID", required = true) @PathVariable("busbarSectionId") String busbarSectionId) {
        return get(() -> repository.getBusbarSection(networkId, variantNum, busbarSectionId));
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/busbar-sections", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple bus bar sections by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted bus bar sections"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteBusBarSections(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of bus bar section IDs to delete", required = true) @RequestBody List<String> busBarSectionIds) {
        return removeAll(ids -> repository.deleteBusBarSections(networkId, variantNum, ids), busBarSectionIds, ResourceType.BUSBAR_SECTION);
    }

    @PutMapping(value = "/{networkId}/busbar-sections")
    @Operation(summary = "Update busbar sections")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update busbar sections"))
    public ResponseEntity<Void> updateBusbarSections(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                     @Parameter(description = "busbarsection resource", required = true) @RequestBody List<Resource<BusbarSectionAttributes>> busbarSectionResources) {
        return updateAll(resources -> repository.updateBusbarSections(networkId, resources), busbarSectionResources, ResourceType.BUSBAR_SECTION);
    }

    // switch

    @PostMapping(value = "/{networkId}/switches")
    @Operation(summary = "Create switches")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create switches"))
    public ResponseEntity<Void> createSwitches(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Switch resource", required = true) @RequestBody List<Resource<SwitchAttributes>> switchResources) {
        return createAll(resources -> repository.createSwitches(networkId, resources), switchResources, ResourceType.SWITCH);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/switches", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get switches")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get switch list"))
    public ResponseEntity<TopLevelDocument<SwitchAttributes>> getSwitches(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                          @Parameter(description = "Max number of switch to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getSwitches(networkId, variantNum), limit, ResourceType.SWITCH);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/switches/{switchId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a switch by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get switch"),
        @ApiResponse(responseCode = "404", description = "Switch has not been found")
    })
    public ResponseEntity<TopLevelDocument<SwitchAttributes>> getSwitch(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                        @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                        @Parameter(description = "Switch ID", required = true) @PathVariable("switchId") String switchId) {
        return get(() -> repository.getSwitch(networkId, variantNum, switchId));
    }

    @PutMapping(value = "/{networkId}/switches")
    @Operation(summary = "Update switches")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update switches"))
    public ResponseEntity<Void> updateSwitches(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                               @Parameter(description = "Switch resource", required = true) @RequestBody List<Resource<SwitchAttributes>> switchResources) {

        return updateAll(resources -> repository.updateSwitches(networkId, resources), switchResources, ResourceType.SWITCH);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/switches", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple switches by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted switches"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteSwitches(
            @Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
            @Parameter(description = "List of switch IDs to delete", required = true) @RequestBody List<String> switchIds) {
        return removeAll(ids -> repository.deleteSwitches(networkId, variantNum, ids), switchIds, ResourceType.SWITCH);
    }


    // 2 windings transformer

    @PostMapping(value = "/{networkId}/2-windings-transformers")
    @Operation(summary = "Create 2 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create 2 windings transformers"))
    public ResponseEntity<Void> createTwoWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                              @Parameter(description = "2 windings transformer resources", required = true) @RequestBody List<Resource<TwoWindingsTransformerAttributes>> twoWindingsTransformerResources) {
        return createAll(resource -> repository.createTwoWindingsTransformers(networkId, resource), twoWindingsTransformerResources, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/2-windings-transformers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get 2 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get 2 windings transformer list"))
    public ResponseEntity<TopLevelDocument<TwoWindingsTransformerAttributes>> getTwoWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                         @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                         @Parameter(description = "Max number of 2 windings transformer to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getTwoWindingsTransformers(networkId, variantNum), limit, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/2-windings-transformers/{twoWindingsTransformerId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a 2 windings transformer by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get 2 windings transformer"),
        @ApiResponse(responseCode = "404", description = "2 windings transformer has not been found")
        })
    public ResponseEntity<TopLevelDocument<TwoWindingsTransformerAttributes>> getTwoWindingsTransformer(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                        @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                        @Parameter(description = "2 windings transformer ID", required = true) @PathVariable("twoWindingsTransformerId") String twoWindingsTransformerId) {
        return get(() -> repository.getTwoWindingsTransformer(networkId, variantNum, twoWindingsTransformerId));
    }

    @PutMapping(value = "/{networkId}/2-windings-transformers")
    @Operation(summary = "Update 2 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update 2 windings transformers"))
    public ResponseEntity<Void> updateTwoWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                              @Parameter(description = "2 windings transformer resources", required = true) @RequestBody List<Resource<TwoWindingsTransformerAttributes>> twoWindingsTransformerResources) {

        return updateAll(resources -> repository.updateTwoWindingsTransformers(networkId, resources), twoWindingsTransformerResources, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    @PutMapping(value = "/{networkId}/2-windings-transformers/sv")
    @Operation(summary = "Update 2 windings transformers SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update 2 windings transformers SV"))
    public ResponseEntity<Void> updateTwoWindingsTransformersSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                @Parameter(description = "2 windings transformer SV resources", required = true) @RequestBody List<Resource<BranchSvAttributes>> twoWindingsTransformerResources) {

        return updateAll(resources -> repository.updateTwoWindingsTransformersSv(networkId, resources), twoWindingsTransformerResources, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/2-windings-transformers", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple 2-windings transformers by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted 2-windings transformers"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteTwoWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                              @Parameter(description = "List of 2-windings transformer IDs to delete", required = true) @RequestBody List<String> twoWindingsTransformerIds) {
        return removeAll(ids -> repository.deleteTwoWindingsTransformers(networkId, variantNum, ids), twoWindingsTransformerIds, ResourceType.TWO_WINDINGS_TRANSFORMER);
    }


    // 3 windings transformer

    @PostMapping(value = "/{networkId}/3-windings-transformers")
    @Operation(summary = "Create 3 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create 3 windings transformers"))
    public ResponseEntity<Void> createThreeWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                @Parameter(description = "3 windings transformer resources", required = true) @RequestBody List<Resource<ThreeWindingsTransformerAttributes>> threeWindingsTransformerResources) {
        return createAll(resource -> repository.createThreeWindingsTransformers(networkId, resource), threeWindingsTransformerResources, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/3-windings-transformers", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get 3 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get 3 windings transformer list"))
    public ResponseEntity<TopLevelDocument<ThreeWindingsTransformerAttributes>> getThreeWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                             @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                             @Parameter(description = "Max number of 3 windings transformer to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getThreeWindingsTransformers(networkId, variantNum), limit, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/3-windings-transformers/{threeWindingsTransformerId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a 3 windings transformer by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get 3 windings transformer"),
        @ApiResponse(responseCode = "404", description = "3 windings transformer has not been found")
        })
    public ResponseEntity<TopLevelDocument<ThreeWindingsTransformerAttributes>> getThreeWindingsTransformer(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                            @Parameter(description = "3 windings transformer ID", required = true) @PathVariable("threeWindingsTransformerId") String threeWindingsTransformerId) {
        return get(() -> repository.getThreeWindingsTransformer(networkId, variantNum, threeWindingsTransformerId));
    }

    @PutMapping(value = "/{networkId}/3-windings-transformers")
    @Operation(summary = "Update 3 windings transformers")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update 3 windings transformers"))
    public ResponseEntity<Void> updateThreeWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                              @Parameter(description = "3 windings transformer resources", required = true) @RequestBody List<Resource<ThreeWindingsTransformerAttributes>> threeWindingsTransformerResources) {

        return updateAll(resources -> repository.updateThreeWindingsTransformers(networkId, resources), threeWindingsTransformerResources, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    @PutMapping(value = "/{networkId}/3-windings-transformers/sv")
    @Operation(summary = "Update 3 windings transformers SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update 3 windings transformers SV"))
    public ResponseEntity<Void> updateThreeWindingsTransformersSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                  @Parameter(description = "3 windings transformer SV resources", required = true) @RequestBody List<Resource<ThreeWindingsTransformerSvAttributes>> threeWindingsTransformerResources) {

        return updateAll(resources -> repository.updateThreeWindingsTransformersSv(networkId, resources), threeWindingsTransformerResources, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/3-windings-transformers", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple 3-windings transformers by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted 3-windings transformers"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteThreeWindingsTransformers(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                @Parameter(description = "List of 3-windings transformer IDs to delete", required = true) @RequestBody List<String> threeWindingsTransformerIds) {
        return removeAll(ids -> repository.deleteThreeWindingsTransformers(networkId, variantNum, ids), threeWindingsTransformerIds, ResourceType.THREE_WINDINGS_TRANSFORMER);
    }


    // line

    @PostMapping(value = "/{networkId}/lines")
    @Operation(summary = "Create lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create lines"))
    public ResponseEntity<Void> createLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "line resources", required = true) @RequestBody List<Resource<LineAttributes>> lineResources) {
        return createAll(resource -> repository.createLines(networkId, resource), lineResources, ResourceType.LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get lines")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get line list"))
    public ResponseEntity<TopLevelDocument<LineAttributes>> getLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                     @Parameter(description = "Max number of line to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getLines(networkId, variantNum), limit, ResourceType.LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/lines/{lineId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a line by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get line"),
        @ApiResponse(responseCode = "404", description = "line has not been found")
        })
    public ResponseEntity<TopLevelDocument<LineAttributes>> getLine(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                    @Parameter(description = "Line ID", required = true) @PathVariable("lineId") String lineId) {
        return get(() -> repository.getLine(networkId, variantNum, lineId));
    }

    @PutMapping(value = "/{networkId}/lines")
    @Operation(summary = "Update lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update lines"))
    public ResponseEntity<Void> updateLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "line resources", required = true) @RequestBody List<Resource<LineAttributes>> lineResources) {

        return updateAll(resources -> repository.updateLines(networkId, resources), lineResources, ResourceType.LINE);
    }

    @PutMapping(value = "/{networkId}/lines/sv")
    @Operation(summary = "Update lines SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update lines SV"))
    public ResponseEntity<Void> updateLinesSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "line SV resources", required = true) @RequestBody List<Resource<BranchSvAttributes>> lineResources) {

        return updateAll(resources -> repository.updateLinesSv(networkId, resources), lineResources, ResourceType.LINE);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/lines", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple lines by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted lines"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                            @Parameter(description = "List of line IDs to delete", required = true) @RequestBody List<String> lineIds) {
        return removeAll(ids -> repository.deleteLines(networkId, variantNum, ids), lineIds, ResourceType.LINE);
    }


    // hvdc line

    @PostMapping(value = "/{networkId}/hvdc-lines")
    @Operation(summary = "Create hvdc lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create hvdc lines"))
    public ResponseEntity<Void> createHvdcLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                @Parameter(description = "Hvdc line resources", required = true) @RequestBody List<Resource<HvdcLineAttributes>> hvdcLineResources) {
        return createAll(resource -> repository.createHvdcLines(networkId, resource), hvdcLineResources, ResourceType.HVDC_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/hvdc-lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get hvdc lines")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get hvdc line list"))
    public ResponseEntity<TopLevelDocument<HvdcLineAttributes>> getHvdcLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                             @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                             @Parameter(description = "Max number of hvdc line to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getHvdcLines(networkId, variantNum), limit, ResourceType.HVDC_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/hvdc-lines/{hvdcLineId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a hvdc line by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get hvdc line"),
        @ApiResponse(responseCode = "404", description = "Hvdc line has not been found")
        })
    public ResponseEntity<TopLevelDocument<HvdcLineAttributes>> getHvdcLine(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                            @Parameter(description = "Hvdc line ID", required = true) @PathVariable("hvdcLineId") String hvdcLineId) {
        return get(() -> repository.getHvdcLine(networkId, variantNum, hvdcLineId));
    }

    @PutMapping(value = "/{networkId}/hvdc-lines")
    @Operation(summary = "Update hvdc lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update hvdc lines"))
    public ResponseEntity<Void> updateHvdcLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "hvdc line resource", required = true) @RequestBody List<Resource<HvdcLineAttributes>> hvdcLineResources) {

        return updateAll(resources -> repository.updateHvdcLines(networkId, resources), hvdcLineResources, ResourceType.HVDC_LINE);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/hvdc-lines", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple hvdc lines by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted hvdc lines"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteHvdcLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                @Parameter(description = "List of hvdc line IDs to delete", required = true) @RequestBody List<String> hvdcLineIds) {
        return removeAll(ids -> repository.deleteHvdcLines(networkId, variantNum, ids), hvdcLineIds, ResourceType.HVDC_LINE);
    }


    // dangling line

    @PostMapping(value = "/{networkId}/dangling-lines")
    @Operation(summary = "Create dangling lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create dangling lines"))
    public ResponseEntity<Void> createDanglingLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "Dangling line resources", required = true) @RequestBody List<Resource<DanglingLineAttributes>> danglingLineResources) {
        return createAll(resource -> repository.createDanglingLines(networkId, resource), danglingLineResources, ResourceType.DANGLING_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/dangling-lines", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get dangling lines")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get dangling line list"))
    public ResponseEntity<TopLevelDocument<DanglingLineAttributes>> getDanglingLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                     @Parameter(description = "Max number of dangling line to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getDanglingLines(networkId, variantNum), limit, ResourceType.DANGLING_LINE);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/dangling-lines/{danglingLineId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a dangling line by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get dangling line"),
        @ApiResponse(responseCode = "404", description = "Dangling line has not been found")
        })
    public ResponseEntity<TopLevelDocument<DanglingLineAttributes>> getDanglingLine(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                    @Parameter(description = "Dangling line ID", required = true) @PathVariable("danglingLineId") String danglingLineId) {
        return get(() -> repository.getDanglingLine(networkId, variantNum, danglingLineId));
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/dangling-lines", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple dangling lines by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted dangling lines"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteDanglingLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                    @Parameter(description = "List of dangling line IDs to delete", required = true) @RequestBody List<String> danglingLineIds) {
        return removeAll(ids -> repository.deleteDanglingLines(networkId, variantNum, ids), danglingLineIds, ResourceType.DANGLING_LINE);
    }

    @PutMapping(value = "/{networkId}/dangling-lines")
    @Operation(summary = "Update dangling lines")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update dangling lines"))
    public ResponseEntity<Void> updateDanglingLines(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                    @Parameter(description = "dangling line resources", required = true) @RequestBody List<Resource<DanglingLineAttributes>> danglingLineResources) {

        return updateAll(resources -> repository.updateDanglingLines(networkId, resources), danglingLineResources, ResourceType.DANGLING_LINE);
    }

    @PutMapping(value = "/{networkId}/dangling-lines/sv")
    @Operation(summary = "Update dangling lines SV")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update dangling lines SV"))
    public ResponseEntity<Void> updateDanglingLinesSv(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                      @Parameter(description = "dangling line SV resources", required = true) @RequestBody List<Resource<InjectionSvAttributes>> danglingLineResources) {

        return updateAll(resources -> repository.updateDanglingLinesSv(networkId, resources), danglingLineResources, ResourceType.DANGLING_LINE);
    }

    // ground
    @GetMapping(value = "/{networkId}/{variantNum}/grounds", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get grounds")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get ground list"))
    public ResponseEntity<TopLevelDocument<GroundAttributes>> getGrounds(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                         @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                         @Parameter(description = "Max number of grounds to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getGrounds(networkId, variantNum), limit, ResourceType.GROUND);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/grounds/{groundId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a ground by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get ground"),
        @ApiResponse(responseCode = "404", description = "Ground has not been found")
    })
    public ResponseEntity<TopLevelDocument<GroundAttributes>> getGround(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                        @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                        @Parameter(description = "Ground ID", required = true) @PathVariable("groundId") String groundId) {
        return get(() -> repository.getGround(networkId, variantNum, groundId));
    }

    @PostMapping(value = "/{networkId}/grounds")
    @Operation(summary = "Create grounds")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create grounds"))
    public ResponseEntity<Void> createGrounds(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "Ground resources", required = true) @RequestBody List<Resource<GroundAttributes>> groundResources) {
        return createAll(resource -> repository.createGrounds(networkId, resource), groundResources, ResourceType.GROUND);
    }

    @PutMapping(value = "/{networkId}/grounds")
    @Operation(summary = "Update grounds")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update grounds"))
    public ResponseEntity<Void> updateGrounds(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "Ground resources", required = true) @RequestBody List<Resource<GroundAttributes>> groundResources) {
        return updateAll(resources -> repository.updateGrounds(networkId, resources), groundResources, ResourceType.GROUND);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/grounds", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple grounds by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted grounds"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteGrounds(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                              @Parameter(description = "List of ground IDs to delete", required = true) @RequestBody List<String> groundIds) {
        return removeAll(ids -> repository.deleteGrounds(networkId, variantNum, ids), groundIds, ResourceType.GROUND);
    }


    // buses

    @PostMapping(value = "/{networkId}/configured-buses")
    @Operation(summary = "Create buses")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully create buses"))
    public ResponseEntity<Void> createBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "Buses resources", required = true) @RequestBody List<Resource<ConfiguredBusAttributes>> busesResources) {
        return createAll(resource -> repository.createBuses(networkId, busesResources), busesResources, ResourceType.CONFIGURED_BUS);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/configured-buses", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get buses")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get buses list"))
    public ResponseEntity<TopLevelDocument<ConfiguredBusAttributes>> getBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                              @Parameter(description = "Max number of buses to get") @RequestParam(required = false) Integer limit) {
        return getAll(() -> repository.getConfiguredBuses(networkId, variantNum), limit, ResourceType.CONFIGURED_BUS);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/configured-buses/{busId}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a bus by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get bus"),
        @ApiResponse(responseCode = "404", description = "bus has not been found")
    })
    public ResponseEntity<TopLevelDocument<ConfiguredBusAttributes>> getBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                              @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                              @Parameter(description = "bus ID", required = true) @PathVariable("busId") String busId) {
        return get(() -> repository.getConfiguredBus(networkId, variantNum, busId));
    }

    @GetMapping(value = "/{networkId}/{variantNum}/voltage-levels/{voltageLevelId}/configured-buses", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a bus by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get buses"),
        @ApiResponse(responseCode = "404", description = "bus has not been found")
    })
    public ResponseEntity<TopLevelDocument<ConfiguredBusAttributes>> getVoltageLevelBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                          @Parameter(description = "voltage level ID", required = true) @PathVariable("voltageLevelId") String voltageLevelId) {
        return getAll(() -> repository.getVoltageLevelBuses(networkId, variantNum, voltageLevelId), null, ResourceType.CONFIGURED_BUS);
    }

    @PutMapping(value = "/{networkId}/configured-buses")
    @Operation(summary = "Update buses")
    @ApiResponses(@ApiResponse(responseCode = "201", description = "Successfully update buses"))
    public ResponseEntity<Void> updateBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "bus resource", required = true) @RequestBody List<Resource<ConfiguredBusAttributes>> busResources) {

        return updateAll(resources -> repository.updateBuses(networkId, resources), busResources, ResourceType.CONFIGURED_BUS);
    }

    @DeleteMapping(value = "/{networkId}/{variantNum}/configured-buses", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete multiple buses by IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted buses"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
    })
    public ResponseEntity<Void> deleteBuses(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                            @Parameter(description = "List of bus IDs to delete", required = true) @RequestBody List<String> busIds) {
        return removeAll(ids -> repository.deleteBuses(networkId, variantNum, ids), busIds, ResourceType.CONFIGURED_BUS);
    }

    @GetMapping(value = "/{networkId}/{variantNum}/identifiables/{id}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an identifiable by id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get the identifiable"),
        @ApiResponse(responseCode = "404", description = "The identifiable has not been found")
    })
    public ResponseEntity<TopLevelDocument<IdentifiableAttributes>> getIdentifiable(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                    @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                    @Parameter(description = "Identifiable ID", required = true) @PathVariable("id") String id) {
        return get(() -> repository.getIdentifiable(networkId, variantNum, id));
    }

    @GetMapping(value = "/{networkUuid}/{variantNum}/identifiables-ids", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all identifiables IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully get the all identifiables IDs"),
        @ApiResponse(responseCode = "404", description = "The network has not been found")
    })
    public List<String> getIdentifiablesIds(@Parameter(description = "Network ID", required = true) @PathVariable("networkUuid") UUID networkUuid,
                                            @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum) {
        return repository.getIdentifiablesIds(networkUuid, variantNum);
    }

    @GetMapping(value = "{networkId}/{variantNum}/identifiables/{identifiableId}/extensions/{extensionName}")
    @Operation(summary = "Get an extension attributes by its identifiable id and extension name")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully get extension attributes"),
                           @ApiResponse(responseCode = "404", description = "The extension attributes has not been found")
    })
    public ResponseEntity<ExtensionAttributesTopLevelDocument> getExtensionAttributes(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                      @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                      @Parameter(description = "Identifiable id", required = true) @PathVariable("identifiableId") String identifiableId,
                                                      @Parameter(description = "Extension name", required = true) @PathVariable("extensionName") String extensionName) {
        return getExtensionAttributes(() -> repository.getExtensionAttributes(networkId, variantNum, identifiableId, extensionName));
    }

    @GetMapping(value = "{networkId}/{variantNum}/identifiables/types/{type}/extensions/{extensionName}")
    @Operation(summary = "Get all extensions attributes with specific extension name for all identifiables with specific type")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get extension attributes"))
    public ResponseEntity<Map<String, ExtensionAttributes>> getAllExtensionsAttributesByResourceTypeAndExtensionName(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                     @Parameter(description = "Resource type", required = true) @PathVariable("type") ResourceType type,
                                                                                                     @Parameter(description = "Extension name", required = true) @PathVariable("extensionName") String extensionName) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(repository.getAllExtensionsAttributesByResourceTypeAndExtensionName(networkId, variantNum, type, extensionName));
    }

    @GetMapping(value = "{networkId}/{variantNum}/identifiables/{identifiableId}/extensions")
    @Operation(summary = "Get all extension attributes for one identifiable with specific identifiable id")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get extension attributes"))
    public ResponseEntity<Map<String, ExtensionAttributes>> getAllExtensionsAttributesByIdentifiableId(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                             @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                             @Parameter(description = "Identifiable id", required = true) @PathVariable("identifiableId") String identifiableId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(repository.getAllExtensionsAttributesByIdentifiableId(networkId, variantNum, identifiableId));

    }

    @GetMapping(value = "{networkId}/{variantNum}/identifiables/types/{type}/extensions")
    @Operation(summary = "Get all extensions attributes for all identifiables with specific type")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get extension attributes"))
    public ResponseEntity<Map<String, Map<String, ExtensionAttributes>>> getAllExtensionsAttributesByResourceType(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                  @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                  @Parameter(description = "Resource type", required = true) @PathVariable("type") ResourceType type) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(repository.getAllExtensionsAttributesByResourceType(networkId, variantNum, type));
    }

    @DeleteMapping(value = "{networkId}/{variantNum}/identifiables/types/{resourceType}/extensions")
    @Operation(summary = "Delete an extension attributes by extension name and identifiable id")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully deleted extension attributes"))
    public ResponseEntity<Void> removeExtensionAttributes(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                          @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                          @Parameter(description = "Identifiable id", required = true) @PathVariable("resourceType") ResourceType type,
                                          @Parameter(description = "identifiables by extension to remove", required = true) @RequestBody Map<String, Set<String>> identifiableIdsByExtensionName) {
        repository.removeExtensionAttributes(networkId, variantNum, identifiableIdsByExtensionName);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "{networkId}/{variantNum}/branch/{branchId}/types/{resourceType}/operationalLimitsGroup/{operationalLimitsGroupId}/side/{side}")
    @Operation(summary = "Get an operational limit group on attributes by its identifiable id and extension name")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get operational limits group attributes"))
    public ResponseEntity<OperationalLimitsGroupAttributesTopLevelDocument> getOperationalLimitsGroupAttributes(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                @Parameter(description = "Resource type", required = true) @PathVariable("resourceType") ResourceType type,
                                                                                                                @Parameter(description = "Branch id", required = true) @PathVariable("branchId") String branchId,
                                                                                                                @Parameter(description = "Operational Limits Group id", required = true) @PathVariable("operationalLimitsGroupId") String operationalLimitsGroupId,
                                                                                                                @Parameter(description = "Branch side", required = true) @PathVariable("side") int side) {
        return getOperationalLimitsGroupAttributes(() -> repository.getOperationalLimitsGroupAttributes(networkId, variantNum, branchId, type, operationalLimitsGroupId, side));
    }

    @DeleteMapping(value = "{networkId}/{variantNum}/branch/types/{resourceType}/operationalLimitsGroup")
    @Operation(summary = "Remove an operational limit group on attributes by its identifiable id and extension name")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Operational limits group attributes successfully removed"))
    public void removeOperationalLimitsGroupAttributes(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                @Parameter(description = "Resource type", required = true) @PathVariable("resourceType") ResourceType type,
                                                                                                                @Parameter(description = "List of olg IDs to delete", required = true)
                                                                                                                @RequestBody Map<String, Map<Integer, Set<String>>> operationalLimitsGroupsToDelete) {
        repository.removeOperationalLimitsGroupAttributes(networkId, variantNum, type, operationalLimitsGroupsToDelete);
    }

    @GetMapping(value = "{networkId}/{variantNum}/branch/types/{resourceType}/operationalLimitsGroup")
    @Operation(summary = "Get all operational limits group attributes for a specific type of equipment")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get operational limits groups attributes"))
    public ResponseEntity<Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>>> getAllOperationalLimitsGroupsAttributesByResourceType(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                     @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                     @Parameter(description = "Resource type", required = true) @PathVariable("resourceType") ResourceType type) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            repository.getAllOperationalLimitsGroupAttributesByResourceType(networkId, variantNum, type));
    }

    @GetMapping(value = "{networkId}/{variantNum}/branch/types/{resourceType}/operationalLimitsGroup/selected")
    @Operation(summary = "Get all selected operational limits groups for a specific type of equipment")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get operational limits groups attributes"))
    public ResponseEntity<Map<String, Map<Integer, Map<String, OperationalLimitsGroupAttributes>>>> getAllSelectedOperationalLimitsGroupAttributesByResourceType(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                                                                      @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                                                                      @Parameter(description = "Resource type", required = true) @PathVariable("resourceType") ResourceType type) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            repository.getAllSelectedOperationalLimitsGroupAttributesByResourceType(networkId, variantNum, type));
    }

    @GetMapping(value = "{networkId}/{variantNum}/branch/{branchId}/types/{resourceType}/side/{side}/operationalLimitsGroup")
    @Operation(summary = "Get all operational limits groups for a branch side")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Successfully get operational limits groups attributes"))
    public ResponseEntity<List<OperationalLimitsGroupAttributes>> getOperationalLimitsGroupAttributesForBranchSide(@Parameter(description = "Network ID", required = true) @PathVariable("networkId") UUID networkId,
                                                                                                                                                                @Parameter(description = "Variant number", required = true) @PathVariable("variantNum") int variantNum,
                                                                                                                                                                @Parameter(description = "Resource type", required = true) @PathVariable("resourceType") ResourceType type,
                                                                                                                                                                @Parameter(description = "Branch id", required = true) @PathVariable("branchId") String branchId,
                                                                                                                                                                @Parameter(description = "Branch side", required = true) @PathVariable("side") int side) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            repository.getOperationalLimitsGroupAttributesForBranchSide(networkId, variantNum, type, branchId, side));
    }
}
