-- Runs inside the integration test transaction; no persistent fixture data.
DO $$
DECLARE
    probe_id UUID := '10000000-0000-0000-0000-000000000099';
    invalid_assignment TEXT;
BEGIN
    INSERT INTO event_logs (event_id, occurred_at, distinct_id, event_name)
    VALUES (probe_id, '2026-11-14T08:00:00+07:00', 'anonymous-visitor', 'product_view');

    IF NOT EXISTS (
        SELECT 1 FROM event_logs WHERE event_id = probe_id
          AND occurred_at = '2026-11-14T01:00:00Z'::timestamptz
          AND source = 'website' AND synthetic AND environment = 'dev'
          AND NOT consent AND NOT bot AND received_at IS NOT NULL
          AND product_id IS NULL AND session_id IS NULL AND page IS NULL
    ) THEN
        RAISE EXCEPTION 'Event defaults or occurred_at normalization differ from the contract';
    END IF;

    UPDATE event_logs SET source = 'simulator', environment = 'staging',
        product_id = 'sim-product-1', order_id = 'sim-order-1',
        session_id = repeat('s', 150), order_item_id = repeat('i', 150),
        request_id = repeat('r', 150), list_id = repeat('l', 150),
        query = repeat('q', 200), page = 'product_detail', position = 1
    WHERE event_id = probe_id;

    FOREACH invalid_assignment IN ARRAY ARRAY[
        'distinct_id = ''''', 'distinct_id = NULL',
        'distinct_id = repeat(''x'', 101)', 'occurred_at = NULL',
        'event_name = ''unknown''', 'event_name = NULL',
        'source = ''backend''', 'source = NULL',
        'environment = ''test''', 'environment = NULL',
        'page = ''checkout''', 'position = 0',
        'synthetic = NULL', 'consent = NULL', 'bot = NULL',
        'query = repeat(''q'', 201)', 'order_id = repeat(''o'', 101)',
        'session_id = repeat(''s'', 151)', 'order_item_id = repeat(''i'', 151)',
        'request_id = repeat(''r'', 151)', 'list_id = repeat(''l'', 151)'
    ] LOOP
        BEGIN
            EXECUTE 'UPDATE event_logs SET ' || invalid_assignment || ' WHERE event_id = $1' USING probe_id;
            RAISE EXCEPTION 'Invalid event accepted: %', invalid_assignment;
        EXCEPTION
            WHEN check_violation OR not_null_violation OR string_data_right_truncation THEN NULL;
        END;
    END LOOP;

    BEGIN
        INSERT INTO event_logs (event_id, occurred_at, distinct_id, event_name)
        VALUES (probe_id, now(), 'anonymous-visitor', 'search');
        RAISE EXCEPTION 'Duplicate event_id accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END;
$$;
