import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import AsyncMock, patch
from tools.load.transport import Transport, ROOT


class DependencyPreflightTest(unittest.TestCase):
    def test_missing_websocket_dependency_precedes_all_process_and_cas_mutations(self):
        with tempfile.TemporaryDirectory(dir=ROOT / '.tools/local-app') as folder:
            output = Path(folder) / 'case'
            driver = Transport(dict(recorders=1, totalUsers=1, chunkMs=2000, durationSeconds=2), output)
            with patch('importlib.import_module', side_effect=ModuleNotFoundError('websockets missing')), patch.object(driver, 'freeze_worker') as worker, patch.object(driver, 'freeze_file_service') as files, patch.object(driver, 'setup', new=AsyncMock(side_effect=RuntimeError('No actual CAS setup in this regression'))) as setup:
                with self.assertRaises(ModuleNotFoundError):
                    asyncio.run(driver.run_all())
                worker.assert_not_called()
                files.assert_not_called()
                setup.assert_not_called()
            self.assertFalse(output.exists(), 'An unavailable test dependency must not create partial load evidence')
