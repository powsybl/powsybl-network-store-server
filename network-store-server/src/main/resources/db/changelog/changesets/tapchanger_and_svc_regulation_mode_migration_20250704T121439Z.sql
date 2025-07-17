-- phase tap changer load tap changing capabilities for two windings transformer
UPDATE twowindingstransformer
    SET phasetapchangerloadtapchangingcapabilities = true
WHERE phasetapchangertapposition is not null or phasetapchangerlowtapposition is not null or phasetapchangertargetdeadband is not null or phasetapchangerregulationvalue is not null ;

-- phase tap changer load tap changing capabilities for three windings transformer
UPDATE threewindingstransformer
SET phasetapchangerloadtapchangingcapabilities1 = true
WHERE phasetapchangertapposition1 is not null or phasetapchangerlowtapposition1 is not null or phasetapchangertargetdeadband1 is not null or phasetapchangerregulationvalue1 is not null ;
UPDATE threewindingstransformer
SET phasetapchangerloadtapchangingcapabilities2 = true
WHERE phasetapchangertapposition2 is not null or phasetapchangerlowtapposition2 is not null or phasetapchangertargetdeadband2 is not null or phasetapchangerregulationvalue2 is not null ;
UPDATE threewindingstransformer
SET phasetapchangerloadtapchangingcapabilities3 = true
WHERE phasetapchangertapposition3 is not null or phasetapchangerlowtapposition3 is not null or phasetapchangertargetdeadband3 is not null or phasetapchangerregulationvalue3 is not null ;

-- phase tap changer regulation two windings transformer and three windings transformer
UPDATE regulatingpoint
set regulationmode = 'CURRENT_LIMITER', regulating = false
where regulationmode = 'FIXED_TAP' and regulatingtapchangertype = 'PHASE_TAP_CHANGER';

-- static var compensator regulation
UPDATE regulatingpoint
set regulationmode = 'VOLTAGE', regulating = false
where regulationmode = 'OFF' and regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR';