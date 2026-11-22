"""Strict preservation checks for the one supported additive Room migration."""

BOND_TABLES = {'bond_reference', 'bond_coupons', 'bond_trade_dates', 'bond_coupon_receipts'}


def verify_preservation(before, after):
    old_schema, new_schema = dict(before['schema']), dict(after['schema'])
    assert old_schema.keys() <= new_schema.keys(), 'An existing table was removed'
    for name, sql in old_schema.items():
        assert new_schema[name] == sql, f'Existing schema changed: {name}'
        # Room updates its identity hash when an additive migration completes.
        if name != 'room_master_table':
            assert before['rows'][name] == after['rows'][name], f'Stored records changed: {name}'
    added = new_schema.keys() - old_schema.keys()
    if before['version'] == after['version']:
        assert not added, 'Tables changed without a schema version change'
        assert before == after, 'Same-version database changed'
    else:
        assert (before['version'], after['version']) == (1, 2), 'Unexpected migration'
        assert added == BOND_TABLES, f'Unexpected added tables: {added}'
        assert all(not after['rows'][name] for name in added), 'Migration invented bond records'
    return dict(data_preserved=True, original_schema_preserved=True,
                schema_preserved=before['schema'] == after['schema'],
                migration_from=before['version'], migration_to=after['version'],
                added_tables=sorted(added))
