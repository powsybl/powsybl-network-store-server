-- phase tap changer load tap changing capabilities
UPDATE twowindingstransformer
    SET phasetapchangerloadtapchangingcapabilities = true
WHERE phasetapchangertapposition is not null or phasetapchangerlowtapposition is not null or phasetapchangertargetdeadband is not null or phasetapchangerregulationvalue is not null ;

-- phase tap changer regulation
UPDATE regulatingpoint
set regulationmode = 'CURRENT_LIMITER', regulating = false
where regulationmode = 'FIXED_TAP' and regulatingequipmenttype = 'TWO_WINDINGS_TRANSFORMER' and regulatingtapchangertype = 'PHASE_TAP_CHANGER';

-- static var compensator regulation
UPDATE regulatingpoint
set regulationmode = 'VOLTAGE', regulating = false
where regulationmode = 'OFF' and regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR';