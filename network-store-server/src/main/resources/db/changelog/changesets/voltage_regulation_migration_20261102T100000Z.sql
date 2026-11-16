-- Migration of the voltage regulation data to the powsybl-core 7.4.0 model.
-- The conversion follows what powsybl-core does when it reads an IIDM file older than 1.18:
--   * local regulation: the target is the local target of the equipment (localtargetv / localtargetq),
--     the target value of the regulating point is undefined (NaN)
--   * regulation on an explicit terminal: the target is the target value of the regulating point
--   * the RemoteReactivePowerControl, VoltagePerReactivePowerControl and battery VoltageRegulation
--     extensions become attributes of the regulating point

-- 1. local targets
UPDATE generator
SET localtargetq = targetq,
    localtargetv = CASE WHEN equivalentlocaltargetv IS NULL OR equivalentlocaltargetv = 'NaN' THEN targetv ELSE equivalentlocaltargetv END;
UPDATE battery SET localtargetq = targetq;
UPDATE vscconverterstation SET localtargetq = reactivepowersetpoint, localtargetv = voltagesetpoint;
UPDATE staticvarcompensator SET localtargetq = reactivepowersetpoint, localtargetv = voltagesetpoint;
UPDATE shuntcompensator SET localtargetv = targetv;

-- 2. an injection regulating on its own terminal regulates locally: no explicit terminal
UPDATE regulatingpoint
SET regulatingterminalconnectableid = NULL, regulatingterminalside = NULL, regulatedequipmenttype = regulatingequipmenttype
WHERE regulatingtapchangertype = 'NONE'
  AND regulatingequipmenttype IN ('GENERATOR', 'SHUNT_COMPENSATOR', 'VSC_CONVERTER_STATION', 'STATIC_VAR_COMPENSATOR')
  AND regulatingterminalconnectableid = regulatingequipmentid;

-- 3. generators, shunt compensators and vsc converter stations had no regulation mode (they could only regulate voltage)
UPDATE regulatingpoint
SET regulationmode = 'VOLTAGE'
WHERE regulationmode IS NULL
  AND ((regulatingtapchangertype = 'NONE' AND regulatingequipmenttype IN ('GENERATOR', 'SHUNT_COMPENSATOR', 'VSC_CONVERTER_STATION', 'STATIC_VAR_COMPENSATOR'))
    OR regulatingtapchangertype LIKE 'RATIO_TAP_CHANGER%');

-- 4. regulation on an explicit terminal: the target moves to the regulating point
UPDATE regulatingpoint rp
SET targetvalue = CASE WHEN g.targetv IS NULL OR g.targetv = 'NaN' THEN COALESCE(g.equivalentlocaltargetv, 'NaN') ELSE g.targetv END
FROM generator g
WHERE rp.regulatingequipmenttype = 'GENERATOR' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND g.networkuuid = rp.networkuuid AND g.variantnum = rp.variantnum AND g.id = rp.regulatingequipmentid;
UPDATE generator g
SET localtargetv = CASE WHEN g.targetv IS NULL OR g.targetv = 'NaN' THEN 'NaN' ELSE COALESCE(g.equivalentlocaltargetv, 'NaN') END
FROM regulatingpoint rp
WHERE rp.regulatingequipmenttype = 'GENERATOR' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND g.networkuuid = rp.networkuuid AND g.variantnum = rp.variantnum AND g.id = rp.regulatingequipmentid;

UPDATE regulatingpoint rp
SET targetvalue = COALESCE(v.voltagesetpoint, 'NaN')
FROM vscconverterstation v
WHERE rp.regulatingequipmenttype = 'VSC_CONVERTER_STATION' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND v.networkuuid = rp.networkuuid AND v.variantnum = rp.variantnum AND v.id = rp.regulatingequipmentid;
UPDATE vscconverterstation v
SET localtargetv = 'NaN'
FROM regulatingpoint rp
WHERE rp.regulatingequipmenttype = 'VSC_CONVERTER_STATION' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND v.networkuuid = rp.networkuuid AND v.variantnum = rp.variantnum AND v.id = rp.regulatingequipmentid;

UPDATE regulatingpoint rp
SET targetvalue = COALESCE(CASE WHEN rp.regulationmode = 'REACTIVE_POWER' THEN s.reactivepowersetpoint ELSE s.voltagesetpoint END, 'NaN')
FROM staticvarcompensator s
WHERE rp.regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND s.networkuuid = rp.networkuuid AND s.variantnum = rp.variantnum AND s.id = rp.regulatingequipmentid;
UPDATE staticvarcompensator s
SET localtargetv = CASE WHEN rp.regulationmode = 'REACTIVE_POWER' THEN s.localtargetv ELSE 'NaN' END,
    localtargetq = CASE WHEN rp.regulationmode = 'REACTIVE_POWER' THEN 'NaN' ELSE s.localtargetq END
FROM regulatingpoint rp
WHERE rp.regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND s.networkuuid = rp.networkuuid AND s.variantnum = rp.variantnum AND s.id = rp.regulatingequipmentid;

-- the target deadband of the shunt compensators moves to the regulating point, with or without explicit terminal
UPDATE regulatingpoint rp
SET targetdeadband = COALESCE(s.targetdeadband, 'NaN'),
    targetvalue = CASE WHEN rp.regulatingterminalconnectableid IS NOT NULL THEN COALESCE(s.targetv, 'NaN') ELSE 'NaN' END
FROM shuntcompensator s
WHERE rp.regulatingequipmenttype = 'SHUNT_COMPENSATOR'
  AND s.networkuuid = rp.networkuuid AND s.variantnum = rp.variantnum AND s.id = rp.regulatingequipmentid;
UPDATE shuntcompensator s
SET localtargetv = 'NaN'
FROM regulatingpoint rp
WHERE rp.regulatingequipmenttype = 'SHUNT_COMPENSATOR' AND rp.regulatingterminalconnectableid IS NOT NULL
  AND s.networkuuid = rp.networkuuid AND s.variantnum = rp.variantnum AND s.id = rp.regulatingequipmentid;

-- 5. RemoteReactivePowerControl extension of the generators: remote reactive power regulation.
-- As in powsybl-core, it is ignored when the generator already has a voltage regulation
-- (voltage regulator on, or a remote regulating terminal).
UPDATE regulatingpoint rp
SET regulationmode = 'REACTIVE_POWER',
    regulating = COALESCE((g.remotereactivepowercontrol::json ->> 'enabled')::boolean, false),
    targetvalue = COALESCE((g.remotereactivepowercontrol::json ->> 'targetQ')::float8, 'NaN'),
    regulatingterminalconnectableid = g.remotereactivepowercontrol::json -> 'regulatingTerminal' ->> 'connectableId',
    regulatingterminalside = g.remotereactivepowercontrol::json -> 'regulatingTerminal' ->> 'side',
    regulatedequipmenttype = NULL
FROM generator g
WHERE rp.regulatingequipmenttype = 'GENERATOR'
  AND g.networkuuid = rp.networkuuid AND g.variantnum = rp.variantnum AND g.id = rp.regulatingequipmentid
  AND g.remotereactivepowercontrol IS NOT NULL
  AND g.remotereactivepowercontrol::json -> 'regulatingTerminal' ->> 'connectableId' IS NOT NULL
  AND rp.regulating IS NOT TRUE AND rp.regulatingterminalconnectableid IS NULL;

-- 6. VoltagePerReactivePowerControl extension of the static var compensators: the slope moves to the regulating point.
-- As in powsybl-core, a null slope is ignored. The mode only changes for the compensators regulating voltage.
UPDATE regulatingpoint rp
SET slope = (s.voltageperreactivepowercontrol::json ->> 'slope')::float8,
    regulationmode = CASE WHEN rp.regulationmode = 'VOLTAGE' THEN 'VOLTAGE_PER_REACTIVE_POWER' ELSE rp.regulationmode END
FROM staticvarcompensator s
WHERE rp.regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR'
  AND s.networkuuid = rp.networkuuid AND s.variantnum = rp.variantnum AND s.id = rp.regulatingequipmentid
  AND s.voltageperreactivepowercontrol IS NOT NULL
  AND (s.voltageperreactivepowercontrol::json ->> 'slope')::float8 NOT IN ('NaN', 0);

-- 7. VoltageRegulation extension of the batteries: voltage regulation of the battery
INSERT INTO regulatingpoint (networkuuid, variantnum, regulatingequipmentid, regulatingequipmenttype, regulatingtapchangertype, regulationmode,
                             localterminalconnectableid, localterminalside, regulatingterminalconnectableid, regulatingterminalside,
                             regulatedequipmenttype, regulating, targetvalue)
SELECT e.networkuuid, e.variantnum, e.equipmentid, 'BATTERY', 'NONE', 'VOLTAGE',
       e.equipmentid, NULL, t.connectableid, CASE WHEN t.connectableid IS NULL THEN NULL ELSE t.side END,
       CASE WHEN t.connectableid IS NULL THEN 'BATTERY' ELSE NULL END,
       COALESCE((e.value_::json ->> 'voltageRegulatorOn')::boolean, false),
       CASE WHEN t.connectableid IS NULL THEN 'NaN' ELSE COALESCE((e.value_::json ->> 'targetV')::float8, 'NaN') END
FROM extension e
CROSS JOIN LATERAL (SELECT NULLIF(e.value_::json -> 'regulatingTerminal' ->> 'connectableId', e.equipmentid) AS connectableid,
                           e.value_::json -> 'regulatingTerminal' ->> 'side' AS side) t
WHERE e.name = 'voltageRegulation' AND e.equipmenttype = 'BATTERY';
UPDATE battery b
SET localtargetv = COALESCE((e.value_::json ->> 'targetV')::float8, 'NaN')
FROM extension e
WHERE e.name = 'voltageRegulation' AND e.equipmenttype = 'BATTERY'
  AND b.networkuuid = e.networkuuid AND b.variantnum = e.variantnum AND b.id = e.equipmentid
  AND NULLIF(e.value_::json -> 'regulatingTerminal' ->> 'connectableId', e.equipmentid) IS NULL;
DELETE FROM extension WHERE name = 'voltageRegulation' AND equipmenttype = 'BATTERY';

-- 8. ratio tap changers: the regulation value and the target deadband move to the regulating point
UPDATE regulatingpoint rp
SET targetvalue = COALESCE(t.ratiotapchangerregulationvalue, 'NaN'), targetdeadband = COALESCE(t.ratiotapchangertargetdeadband, 'NaN')
FROM twowindingstransformer t
WHERE rp.regulatingequipmenttype = 'TWO_WINDINGS_TRANSFORMER' AND rp.regulatingtapchangertype = 'RATIO_TAP_CHANGER'
  AND t.networkuuid = rp.networkuuid AND t.variantnum = rp.variantnum AND t.id = rp.regulatingequipmentid;
UPDATE regulatingpoint rp
SET targetvalue = COALESCE(t.ratiotapchangerregulationvalue1, 'NaN'), targetdeadband = COALESCE(t.ratiotapchangertargetdeadband1, 'NaN')
FROM threewindingstransformer t
WHERE rp.regulatingequipmenttype = 'THREE_WINDINGS_TRANSFORMER' AND rp.regulatingtapchangertype = 'RATIO_TAP_CHANGER_SIDE_ONE'
  AND t.networkuuid = rp.networkuuid AND t.variantnum = rp.variantnum AND t.id = rp.regulatingequipmentid;
UPDATE regulatingpoint rp
SET targetvalue = COALESCE(t.ratiotapchangerregulationvalue2, 'NaN'), targetdeadband = COALESCE(t.ratiotapchangertargetdeadband2, 'NaN')
FROM threewindingstransformer t
WHERE rp.regulatingequipmenttype = 'THREE_WINDINGS_TRANSFORMER' AND rp.regulatingtapchangertype = 'RATIO_TAP_CHANGER_SIDE_TWO'
  AND t.networkuuid = rp.networkuuid AND t.variantnum = rp.variantnum AND t.id = rp.regulatingequipmentid;
UPDATE regulatingpoint rp
SET targetvalue = COALESCE(t.ratiotapchangerregulationvalue3, 'NaN'), targetdeadband = COALESCE(t.ratiotapchangertargetdeadband3, 'NaN')
FROM threewindingstransformer t
WHERE rp.regulatingequipmenttype = 'THREE_WINDINGS_TRANSFORMER' AND rp.regulatingtapchangertype = 'RATIO_TAP_CHANGER_SIDE_THREE'
  AND t.networkuuid = rp.networkuuid AND t.variantnum = rp.variantnum AND t.id = rp.regulatingequipmentid;

-- 9. type of the equipments regulated through the terminals coming from the extensions
UPDATE regulatingpoint rp
SET regulatedequipmenttype = c.equipmenttype
FROM (SELECT networkuuid, variantnum, id, 'BATTERY' AS equipmenttype FROM battery
      UNION ALL SELECT networkuuid, variantnum, id, 'BUSBAR_SECTION' FROM busbarsection
      UNION ALL SELECT networkuuid, variantnum, id, 'LOAD' FROM load
      UNION ALL SELECT networkuuid, variantnum, id, 'GENERATOR' FROM generator
      UNION ALL SELECT networkuuid, variantnum, id, 'SHUNT_COMPENSATOR' FROM shuntcompensator
      UNION ALL SELECT networkuuid, variantnum, id, 'VSC_CONVERTER_STATION' FROM vscconverterstation
      UNION ALL SELECT networkuuid, variantnum, id, 'LCC_CONVERTER_STATION' FROM lccconverterstation
      UNION ALL SELECT networkuuid, variantnum, id, 'STATIC_VAR_COMPENSATOR' FROM staticvarcompensator
      UNION ALL SELECT networkuuid, variantnum, id, 'TWO_WINDINGS_TRANSFORMER' FROM twowindingstransformer
      UNION ALL SELECT networkuuid, variantnum, id, 'THREE_WINDINGS_TRANSFORMER' FROM threewindingstransformer
      UNION ALL SELECT networkuuid, variantnum, id, 'LINE' FROM line
      UNION ALL SELECT networkuuid, variantnum, id, 'BOUNDARY_LINE' FROM boundaryline) c
WHERE rp.regulatedequipmenttype IS NULL AND rp.regulatingterminalconnectableid IS NOT NULL
  AND c.networkuuid = rp.networkuuid AND c.variantnum = rp.variantnum AND c.id = rp.regulatingterminalconnectableid;
