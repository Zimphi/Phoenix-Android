import json
from pathlib import Path
import unittest
import subprocess
import tomllib
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

    def test_all_platforms_pin_the_same_transitive_cores(self):
        workspace = ROOT.parent
        if not (workspace / 'Phoenix-Vercel/components.lock.json').exists():
            self.skipTest('Cross-platform check requires the integration workspace')
        android = json.loads((ROOT / 'components.lock.json').read_text())
        cloud = json.loads((workspace / 'Phoenix-Vercel/components.lock.json').read_text())
        local = json.loads((workspace / 'Phoenix-Simulator-Rust/components.lock.json').read_text())
        names = {'eid': 'Phoenix-eID-Core-Rust', 'epassport': 'Phoenix-ePassport-Core-Rust',
                 'pqc': 'Phoenix-PQC-Rust', 'license': 'Phoenix-License-Client'}
        local_components = {entry['name']: entry for entry in local['components']}
        for name, repository in names.items():
            expected = android[name]['commit']
            self.assertEqual(cloud[name]['commit'], expected, name)
            entry = local_components[repository]
            self.assertEqual(entry['revision'], expected, name)
            for build in entry.get('builds', []):
                self.assertEqual(build['revision'], expected, build['path'])
        for name in ('eid', 'terminal-rust'):
            for dependency in ('epassport', 'pqc', 'license'):
                self.assertEqual(cloud[name]['dependencies'][dependency]['commit'],
                                 android[dependency]['commit'], name + '/' + dependency)
        self.assertEqual(android['host']['commit'], subprocess.check_output(
            ['git', '-C', str(workspace / 'Phoenix-Simulator-Rust'), 'rev-parse', 'HEAD'],
            text=True).strip())

    def test_passport_catalog_is_identical_across_platforms(self):
        import hashlib
        lock=json.loads((ROOT/'passport-catalog.lock.json').read_text())
        for repository in ('Phoenix-Simulator-Rust','Phoenix-Vercel'):
            path=ROOT.parent/repository/'passport-catalog.lock.json'
            if path.exists():self.assertEqual(json.loads(path.read_text()),lock)
        private=ROOT/'.private/epassport/test-profiles/catalog.json'
        if private.exists():
            self.assertEqual(hashlib.sha256(private.read_bytes()).hexdigest(),lock['sha256'])
            catalog=json.loads(private.read_bytes())
            self.assertEqual(len(catalog['profiles']),33)
            self.assertEqual(sum(p['simulatable'] for p in catalog['profiles']),8)

    def test_shared_registry_dependencies_use_identical_locked_versions(self):
        def registry(path):
            result = {}
            for package in tomllib.loads(path.read_text())['package']:
                if not package.get('source', '').startswith('registry+'):
                    continue
                version = package['version'].split('.')
                key = (package['name'], version[0], version[1] if version[0] == '0' else '')
                result.setdefault(key, set()).add((package['version'], package['checksum']))
            return result
        expected = registry(ROOT / 'native/Cargo.lock')
        for relative in ('Phoenix-Simulator-Rust/Cargo.lock', 'Phoenix-Vercel/Cargo.lock',
                         'Phoenix-Vercel/worker/native-pqc/Cargo.lock',
                         'Phoenix-Simulator-Rust/components/eid/Cargo.lock',
                         'Phoenix-Simulator-Rust/components/passport/Cargo.lock',
                         'Phoenix-Simulator-Rust/components/terminal/Cargo.lock'):
            path = ROOT.parent / relative
            if not path.is_file():
                continue
            for key, versions in registry(path).items():
                if key in expected:
                    self.assertTrue(versions <= expected[key], f'{relative}: {key}: {versions}')

    def test_persosim_catalog_has_all_requested_profiles_without_globaltester(self):
        manifest=json.loads((ROOT/'persosim-fixtures.lock.json').read_text())
        drafts=json.loads((ROOT/'app/src/main/assets/persosim-profiles.json').read_text())
        expected={f'eid-persosim{number:02}' for number in range(1,12)} | {f'eid-persosimub{number:02}' for number in range(1,6)}
        self.assertEqual({entry['id'] for entry in manifest['profiles']},expected)
        self.assertEqual({draft['id'] for draft in drafts},expected)
        self.assertEqual(len(drafts),16)
        for draft in drafts:
            self.assertEqual(draft['kind'],'eid')
            self.assertTrue(draft['groups'])
        for entry in manifest['profiles']:
            self.assertRegex(entry['source_sha256'],r'^[0-9a-f]{64}$')
            self.assertRegex(entry['adapted_sha256'],r'^[0-9a-f]{64}$')

    def test_hce_only_registers_test_document_aids_and_requires_unlock(self):
        root=ET.parse(ROOT/'app/src/main/res/xml/apdu_service.xml').getroot()
        namespace='{http://schemas.android.com/apk/res/android}'
        self.assertEqual(root.attrib[namespace+'requireDeviceUnlock'],'true')
        self.assertEqual([node.attrib[namespace+'name'] for node in root.iter('aid-filter')],['A0000002471001','E80704007F00070302'])
        manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
        self.assertEqual({node.attrib[namespace+'name'] for node in manifest.iter('uses-permission')},{'android.permission.NFC','android.permission.INTERNET'})
        self.assertEqual(next(manifest.iter('service')).attrib[namespace+'permission'],'android.permission.BIND_NFC_SERVICE')

if __name__ == '__main__': unittest.main()
