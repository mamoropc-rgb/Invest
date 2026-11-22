import copy
import unittest
import xml.etree.ElementTree as ET
from upgrade_policy import BOND_TABLES, verify_preservation
from upgrade_ui import bounds, launcher_anr_close


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


def emulator_dialog(title="Pixel Launcher isn't responding", package='android', enabled='true'):
    root = ET.Element('hierarchy')
    ET.SubElement(root, 'node', {'package': package, 'resource-id': 'android:id/alertTitle', 'text': title})
    ET.SubElement(root, 'node', {'package': 'android', 'resource-id': 'android:id/aerr_close',
                               'enabled': enabled, 'clickable': 'true', 'bounds': '[72,1051][1008,1183]'})
    return root


class EmulatorUpgradeUiTest(unittest.TestCase):
    def test_pixel_launcher_dialog_can_be_closed(self):
        self.assertEqual((72, 1051, 1008, 1183), launcher_anr_close(emulator_dialog()))

    def test_investments_anr_is_never_hidden(self):
        self.assertIsNone(launcher_anr_close(emulator_dialog("Инвестиции isn't responding")))

    def test_arbitrary_anr_is_never_hidden(self):
        self.assertIsNone(launcher_anr_close(emulator_dialog("Another app isn't responding")))

    def test_app_ui_cannot_spoof_the_system_dialog(self):
        self.assertIsNone(launcher_anr_close(emulator_dialog(package='com.mamoro.investments')))

    def test_disabled_close_is_not_tapped(self):
        self.assertIsNone(launcher_anr_close(emulator_dialog(enabled='false')))

    def test_invalid_or_empty_bounds_are_not_tapped(self):
        for value in ('', '[5,5][5,6]', '[-1,2][4,5]', '[0,8][4,2]'):
            self.assertIsNone(bounds(ET.Element('node', {'bounds': value})))


if __name__ == '__main__':
    unittest.main()
