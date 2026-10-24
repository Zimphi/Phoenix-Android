"""Test the actual Kotlin OpenPGP sender; produce synthetic GnuPG interoperability inputs."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from test_nfc_test_assessment import ROOT, JAVA, jar


class NfcUploadCryptoTests(unittest.TestCase):
    def test_openpgp_recipient_integrity_and_request_binding(self):
        compiler = [jar('org.jetbrains.kotlin', name, '2.1.20') for name in
                    ['kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-script-runtime', 'kotlin-daemon-embeddable']]
        compiler += [jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
                     jar('org.jetbrains', 'annotations', '13.0'),
                     jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')]
        libraries = [compiler[1], compiler[-2]] + [jar('org.bouncycastle', name, '1.83') for name in
                    ['bcprov-jdk18on', 'bcpg-jdk18on', 'bcutil-jdk18on']]
        with tempfile.TemporaryDirectory(prefix='phoenix-nfc-pgp-') as directory:
            sources = [ROOT / 'app/src/main/java/de/phoenix/simulator' / (name+'.kt') for name in ['NfcUploadCrypto','NfcUploadProof']]
            sources += [ROOT/'tests/kotlin/NfcUploadCheck.kt']
            result = subprocess.run([str(JAVA), '-cp', os.pathsep.join(map(str, compiler)),
                'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
                '-classpath', os.pathsep.join(map(str,libraries)), '-d', directory, *map(str,sources)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            output = Path(os.environ.get('PHOENIX_PGP_TEST_OUTPUT', directory))
            output.mkdir(parents=True,exist_ok=True)
            result = subprocess.run([str(JAVA), '-cp', os.pathsep.join([directory,*map(str,libraries)]),
                'de.phoenix.simulator.NfcUploadCheckKt',str(ROOT/'app/src/main/assets/nfc-diagnostics-recipient.asc'),
                str(output/'synthetic.pgp'),str(output/'expected.json')],capture_output=True,text=True,timeout=30)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)


if __name__ == '__main__': unittest.main()
