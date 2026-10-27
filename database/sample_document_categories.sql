-- Sample data: a starter set of document categories. You can also create these in the app
-- (Admin -> Document Categories). Safe to run more than once: existing names are skipped.

USE staffsuite;

INSERT IGNORE INTO document_categories (name, description) VALUES
    ('ID Proof',                 'Government-issued identity documents'),
    ('Offer Letter',             'Offer and appointment letters'),
    ('Certificate',              'Educational and professional certificates'),
    ('Policy Acknowledgement',   'Signed acknowledgements of company policies'),
    ('Address Proof',            'Documents confirming the current address'),
    ('Contract',                 'Employment contracts and amendments');
