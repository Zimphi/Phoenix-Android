import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class RepositoryContract(unittest.TestCase):
    def test_apache_license_and_separate_cores(self):
        self.assertIn('Apache License', (ROOT/'LICENSE').read_text())
        self.assertIn('NOT covered', (ROOT/'NOTICE').read_text())
        for path in ('*.so', '*.aar', '.private/'):
            self.assertIn(path, (ROOT/'.gitignore').read_text())
        self.assertFalse((ROOT/'cores').exists())

    def test_core_build_identity_is_exact_and_packaged(self):
        raw=(ROOT/'components.lock.json').read_bytes()
        self.assertEqual(raw,(ROOT/'app/src/main/assets/components.lock.json').read_bytes())
        components=json.loads(raw)
        for name in ('eid','epassport','pqc','license','host'):
            self.assertRegex(components[name]['commit'],r'^[0-9a-f]{40}$')
            self.assertTrue(components[name]['version'])

    def test_hce_only_registers_test_document_aids_and_requires_unlock(self):
        root=ET.parse(ROOT/'app/src/main/res/xml/apdu_service.xml').getroot()
        namespace='{http://schemas.android.com/apk/res/android}'
        self.assertEqual(root.attrib[namespace+'requireDeviceUnlock'],'true')
        self.assertEqual([node.attrib[namespace+'name'] for node in root.iter('aid-filter')],['A0000002471001','E80704007F00070302'])
        manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        self.assertEqual({node.attrib[namespace+'name'] for node in manifest.iter('uses-permission')},{'android.permission.NFC','android.permission.INTERNET'})
        self.assertEqual(next(manifest.iter('service')).attrib[namespace+'permission'],'android.permission.BIND_NFC_SERVICE')

if __name__ == '__main__': unittest.main()
