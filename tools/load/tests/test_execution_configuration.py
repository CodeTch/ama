import unittest
from tools.load.transport import configured_pool_maximum


class ExecutionConfigurationTest(unittest.TestCase):
    def test_generated_local_configuration_takes_precedence_over_shell_environment(self):
        self.assertEqual(32, configured_pool_maximum({'databasePoolMaximum': 32}, {}))
        self.assertEqual(32, configured_pool_maximum({'databasePoolMaximum': 32}, {'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE': '64'}))
        self.assertEqual(20, configured_pool_maximum({}, {}))
        for value in (0, 65, '32', True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                configured_pool_maximum({'databasePoolMaximum': value}, {})
