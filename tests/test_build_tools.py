import importlib.util
from pathlib import Path
import tempfile
import unittest
import subprocess
from unittest.mock import patch
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/f'{name}.py')
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result

class BuildTools(unittest.TestCase):
    def test_build_requires_actual_ndk_and_does_not_mutate_environment(self):
        build=module('build_native')
        with tempfile.TemporaryDirectory() as directory:
            ndk=Path(directory)
            with self.assertRaises(ValueError):build.environment(ndk,{'PATH':'old'})
            tools=ndk/'toolchains/llvm/prebuilt/darwin-x86_64/bin';tools.mkdir(parents=True);(tools/'aarch64-linux-android26-clang').touch()
            current={'PATH':'old','UNCHANGED':'yes'};env=build.environment(ndk,current)
            self.assertEqual(current,{'PATH':'old','UNCHANGED':'yes'})
            self.assertEqual(env['RANLIB_aarch64_linux_android'],str(tools/'llvm-ranlib'))
            self.assertIn('16384',env['CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS'])
            self.assertEqual(env['UNCHANGED'],'yes')
    def test_build_rejects_source_override_even_when_package_version_matches(self):
        build=module('build_native')
        path='dependencies/epassport'; revision='a'*40
        result=SimpleNamespace(stdout=f'160000 commit {revision}\t{path}\n')
        with patch.object(build.subprocess,'run',return_value=result):
            build.verify_dependencies(Path('synthetic'),{path:revision})
            with self.assertRaisesRegex(ValueError,'shared Rust dependency'):
                build.verify_dependencies(Path('synthetic'),{path:'b'*40})
    def test_fresh_private_checkout_resolves_pinned_source(self):
        prepare=module('prepare_native')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); source=root/'source'; destination=root/'private'
            subprocess.run(['git','init','-q',str(source)],check=True)
            (source/'core.rs').write_text('pub const VERSION: &str = "test";')
            subprocess.run(['git','-C',str(source),'add','core.rs'],check=True)
            subprocess.run(['git','-C',str(source),'-c','user.name=Test','-c','user.email=test@invalid',
                            '-c','commit.gpgsign=false','commit','-qm','synthetic source'],check=True)
            revision=prepare.revision(source)
            prepare.clone(source,destination,revision)
            prepare.clone(source,destination,revision)
            self.assertEqual((source/'core.rs').read_bytes(),(destination/'core.rs').read_bytes())
            self.assertEqual(prepare.revision(destination),revision)
    def test_private_source_link_cannot_replace_populated_directory(self):
        prepare=module('prepare_native')
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);existing=root/'existing';existing.mkdir();(existing/'keep').write_text('original')
            target=root/'target';target.mkdir()
            with self.assertRaises(RuntimeError):prepare.link(root,'existing',target)
            self.assertEqual((existing/'keep').read_text(),'original')
            prepare.link(root,'new',target);prepare.link(root,'new',target)
            self.assertEqual((root/'new').resolve(),target.resolve())
            other=root/'other';other.mkdir()
            with self.assertRaises(RuntimeError):prepare.link(root,'new',other)
if __name__=='__main__':unittest.main()
