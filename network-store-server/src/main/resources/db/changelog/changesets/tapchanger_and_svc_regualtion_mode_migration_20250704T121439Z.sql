-- phase tap changer
UPDATE regulatingpoint
set regulationmode = 'CURRENT_LIMITER', regulating = false
where regulationmode = 'FIXED_TAP' and regulatingequipmenttype = 'TWO_WINDINGS_TRANSFORMER' and regulatingtapchangertype = 'PHASE_TAP_CHANGER';

-- static var compensator
UPDATE regulatingpoint
set regulationmode = 'VOLTAGE', regulating = false
where regulationmode = 'OFF' and regulatingequipmenttype = 'STATIC_VAR_COMPENSATOR';