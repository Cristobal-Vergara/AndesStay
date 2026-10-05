INSERT INTO units (name,type,capacity,price,available,description)
SELECT 'Habitación de ejemplo','HABITACION',2,50000.00,TRUE,'Unidad de demostración local'
WHERE NOT EXISTS (SELECT 1 FROM units WHERE name='Habitación de ejemplo');
INSERT INTO units (name,type,capacity,price,available,description)
SELECT 'Cabaña de ejemplo','CABANA',4,80000.00,TRUE,'Unidad de demostración local'
WHERE NOT EXISTS (SELECT 1 FROM units WHERE name='Cabaña de ejemplo');
