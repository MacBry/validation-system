ALTER TABLE measurement_series
    ADD COLUMN is_reference_recorder BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE validations
    ADD COLUMN control_sensor_position VARCHAR(30);
