--  set area
INSERT INTO area(networkuuid, variantnum, id, name, areatype, voltagelevelids, interchangetarget, fictitious,
                 properties, aliasbytype)
SELECT uuid,
       variantnum,
       valuesTest.el ->> 'id',
       valuesTest.el ->> 'name',
       'ControlAreaTypeKind.Interchange',
       '[]',
       cast(valuesTest.el ->> 'netInterchange' as double precision),
       false,
       CONCAT('{"pTolerance":"', valuesTest.el ->> 'ptolerance', '"}'),
       CONCAT('{"energyIdentCodeEic":"', valuesTest.el ->> 'energyIdentificationCodeEic', '"}')
FROM (SELECT uuid, variantnum, jsonb_array_elements(cgmescontrolareas::jsonb -> 'controlAreas') as el
      FROM network WHERE variantnum = 0) AS valuesTest;

-- set area boundaries with terminals
INSERT INTO areaboundary (areaid, networkuuid, variantnum, terminalconnectableid, terminalside)
SELECT areaid, uuid, variantnum, term ->> 'connectableId', term ->> 'side'
FROM (SELECT el ->> 'id'                             as areaid,
             uuid,
             variantnum,
             jsonb_array_elements(el -> 'terminals') as term
      FROM (SELECT uuid, variantnum, jsonb_array_elements(cgmescontrolareas::jsonb -> 'controlAreas') as el
            FROM network WHERE variantnum = 0) as valueTest) as valueTest2;
-- set area boundaries with dangling lines
INSERT INTO areaboundary (areaid, networkuuid, variantnum, boundarydanglinglineid, ac)
SELECT valuesTest.el ->> 'id',
       uuid,
       variantnum,
       jsonb_array_elements(valuesTest.el -> 'boundaries') ->> 'connectableId',
       true
FROM (SELECT uuid, variantnum, jsonb_array_elements(cgmescontrolareas::jsonb -> 'controlAreas') as el
      FROM network WHERE variantnum = 0) as valuesTest;
-- set ac boolean for terminal check if it is a hvdc terminal
Update areaboundary
set ac = terminalconnectableid in (SELECT id FROM hvdcline) OR
         terminalconnectableid in (SELECT converterstationid1 FROM hvdcline) OR
         terminalconnectableid in (SELECT converterstationid2 FROM hvdcline)
where terminalconnectableid is not null;