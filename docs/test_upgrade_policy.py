import copy
import unittest
from upgrade_policy import BOND_TABLES, verify_preservation


class UpgradePolicyTest(unittest.TestCase):
    def setUp(self):
        self.before = dict(version=1, schema=[('income', 'CREATE TABLE income (...)'),
                                             ('room_master_table', 'CREATE TABLE room_master_table (...)')],
                           rows={'income': [('paid', 1234)], 'room_master_table': [(42, 'old')]})
        self.after = copy.deepcopy(self.before)
        self.after['version'] = 2
        self.after['rows']['room_master_table'] = [(42, 'new')]
        for table in sorted(BOND_TABLES):
            self.after['schema'].append((table, f'CREATE TABLE {table} (...)'))
            self.after['rows'][table] = []

    def test_same_schema_and_additive_migration(self):
        self.assertTrue(verify_preservation(self.before, self.before)['schema_preserved'])
        self.assertTrue(verify_preservation(self.before, self.after)['data_preserved'])

    def test_changed_income_is_rejected(self):
        self.after['rows']['income'] = []
        with self.assertRaises(AssertionError):
            verify_preservation(self.before, self.after)

    def test_changed_original_schema_is_rejected(self):
        self.after['schema'][0] = ('income', 'CREATE TABLE income (changed)')
        with self.assertRaises(AssertionError):
            verify_preservation(self.before, self.after)

    def test_unknown_tables_or_seeded_cash_are_rejected(self):
        for change in ['extra', 'rows']:
            after = copy.deepcopy(self.after)
            if change == 'extra':
                after['schema'].append(('cash', 'CREATE TABLE cash (...)'))
            else:
                after['rows']['bond_coupons'] = [('invented',)]
            with self.assertRaises(AssertionError):
                verify_preservation(self.before, after)


if __name__ == '__main__':
    unittest.main()
