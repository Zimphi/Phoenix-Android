"""Build the private Rust adapter with the official Android NDK; do not publish its output."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import json

ROOT=Path(__file__).resolve().parents[1]

def environment(ndk, current):
    toolchain=ndk/'toolchains/llvm/prebuilt/darwin-x86_64/bin'
    if not (toolchain/'aarch64-linux-android26-clang').is_file():
        raise ValueError('Android NDK macOS toolchain missing')
    env=dict(current)
    env['PATH']=str(Path.home()/'.cargo/bin')+os.pathsep+str(toolchain)+os.pathsep+env.get('PATH','')
    env['ANDROID_NDK_HOME']=str(ndk)
    env['CC_aarch64_linux_android']=str(toolchain/'aarch64-linux-android26-clang')
    env['AR_aarch64_linux_android']=str(toolchain/'llvm-ar')
    env['RANLIB_aarch64_linux_android']=str(toolchain/'llvm-ranlib')
    env['CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER']=env['CC_aarch64_linux_android']
    env['CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS']='-C link-arg=-Wl,-z,max-page-size=16384'
    return env

def verify_dependencies(source, dependencies):
    for path, expected in dependencies.items():
        fields = subprocess.run(['git', '-C', str(source), 'ls-tree', 'HEAD', path],
                                check=True, text=True, capture_output=True).stdout.split()
        if fields != ['160000', 'commit', expected, path]:
            raise ValueError(f'Unexpected shared Rust dependency: {path}')

def build(ndk,check):
    from prepare_test_fixture import verify
    verify()
    locations={'host':'simulator','terminal':'terminal','eid':'eid','epassport':'epassport','pqc':'pqc','license':'license','eid-reference':'eid-reference'}
    lock=json.loads((ROOT/'components.lock.json').read_text())
    for name,entry in lock.items():
        source=ROOT/'.private'/locations[name]
        actual=subprocess.run(['git','-C',str(source),'rev-parse','HEAD'],check=True,text=True,capture_output=True).stdout.strip()
        if actual!=entry['commit']: raise ValueError(f'Unexpected {name} source revision')
        subprocess.run(['git','-C',str(source),'diff','--quiet','--ignore-submodules','HEAD','--','*.rs','*.toml','build.rs'],check=True)
        untracked=subprocess.run(['git','-C',str(source),'ls-files','--others','--exclude-standard','--','*.rs','*.toml'],check=True,text=True,capture_output=True).stdout
        if untracked: raise ValueError(f'Untracked Rust build inputs in {name}')
    for name, dependencies in {
        'eid': {'dependencies/epassport': 'epassport',
                'dependencies/Phoenix-PQC-Rust': 'pqc',
                'dependencies/Phoenix-License-Client': 'license',
                'dependencies/eid-reference': 'eid-reference'},
        'epassport': {'dependencies/Phoenix-PQC-Rust': 'pqc',
                      'dependencies/Phoenix-License-Client': 'license'},
        'terminal': {'dependencies/epassport':'epassport','dependencies/Phoenix-License-Client':'license','dependencies/Phoenix-PQC-Rust':'pqc'},
        'host': {'components/eid': 'eid', 'components/passport': 'epassport', 'components/terminal':'terminal'},
    }.items():
        verify_dependencies(ROOT/'.private'/locations[name],
                            {path: lock[key]['commit'] for path, key in dependencies.items()})
    command=['cargo','check' if check else 'build','--manifest-path',str(ROOT/'native/Cargo.toml'),'--target','aarch64-linux-android','--locked','--offline']
    subprocess.run(command,env=environment(ndk,os.environ),check=True)
    if not check:
        source=ROOT/'native/target/aarch64-linux-android/debug/libphoenix_android.so'
        output=ROOT/'app/src/main/jniLibs/arm64-v8a';output.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,output/source.name)
        subprocess.run([str(ndk/'toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip'),'--strip-debug',str(output/source.name)],check=True)
        print('Private native library copied into local APK build inputs; excluded from Git.')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--ndk',required=True,type=Path);parser.add_argument('--check',action='store_true');args=parser.parse_args();build(args.ndk.resolve(),args.check)
