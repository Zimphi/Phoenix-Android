"""Execute Kotlin verdict, NFC authentication and ephemeral TLS checks on the JVM."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT.parent / 'Phoenix-NFC-Device-Tests/.tools/gradle/caches/modules-2/files-2.1'
JAVA = Path(os.environ.get('JAVA_HOME', '/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home')) / 'bin/java'


def jar(group, name, version):
    return next((CACHE / group / name / version).glob('*/*.jar'))


class NfcTestAssessmentTests(unittest.TestCase):
    def test_authenticated_radio_evidence_and_temporary_pairing(self):
        compiler = [jar('org.jetbrains.kotlin', name, '2.1.20') for name in
                    ['kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-script-runtime', 'kotlin-daemon-embeddable']]
        compiler += [jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                     jar('org.jetbrains', 'annotations', '13.0'),
                     jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
        stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.1.20')
        bc = jar('org.bouncycastle', 'bcprov-jdk18on', '1.83')
        with tempfile.TemporaryDirectory(prefix='phoenix-nfc-kotlin-') as directory:
            sources = [ROOT / 'app/src/main/java/de/phoenix/simulator' / (name + '.kt') for name in
                       ['NfcTestAssessment', 'NfcTestProtocol', 'NfcTestTls', 'NfcCodePairing']]
            sources.append(ROOT / 'tests/kotlin/NfcTestCheck.kt')
            result = subprocess.run([str(JAVA), '-cp', os.pathsep.join(map(str, compiler)),
                'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                '-classpath', os.pathsep.join(map(str, [stdlib, compiler[-2], bc])),
                '-d', directory, *map(str, sources)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([str(JAVA), '-cp', directory + os.pathsep + str(stdlib) + os.pathsep + str(bc),
                'de.phoenix.simulator.NfcTestCheckKt'], capture_output=True, text=True, timeout=90)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
