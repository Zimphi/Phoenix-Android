"""Prepare private, pinned build inputs without copying their source into public Git."""
from pathlib import Path
import argparse
import json
import subprocess

ROOT=Path(__file__).resolve().parents[1]
def run(*args, **kw):
    return subprocess.run(args,check=True,text=True,**kw)
def revision(path):
    return run('git','-C',str(path),'rev-parse','HEAD',capture_output=True).stdout.strip()
def clone(source,destination,commit):
    if not destination.exists():
        destination.mkdir(parents=True)
        run('git','init',str(destination))
        objects=run('git','-C',str(source),'rev-parse','--git-path','objects',capture_output=True).stdout.strip()
        objects=(source/objects).resolve() if not Path(objects).is_absolute() else Path(objects)
        (destination/'.git/objects/info/alternates').write_text(str(objects)+'\n')
        run('git','-C',str(destination),'checkout','--detach',commit)
    if revision(destination)!=commit: run('git','-C',str(destination),'checkout','--detach',commit)
    run('git','-C',str(destination),'diff','--quiet','--ignore-submodules','HEAD','--','*.rs','Cargo.toml','build.rs')
def link(parent,relative,destination):
    path=parent/relative
    if path.is_symlink():
        if path.resolve()!=destination.resolve(): raise RuntimeError(f'Unexpected build input: {path}')
    elif path.exists():
        if any(path.iterdir()): raise RuntimeError(f'Refusing to overwrite {path}')
        path.rmdir()
    path.parent.mkdir(parents=True,exist_ok=True)
    if not path.exists(): path.symlink_to(destination,target_is_directory=True)
def prepare(workspace):
    lock=json.loads((ROOT/'components.lock.json').read_text()); private=ROOT/'.private';private.mkdir(exist_ok=True)
    sources={'host':workspace/'Phoenix-Simulator-Rust','eid':workspace/'Phoenix-eID-Core-Rust','epassport':workspace/'Phoenix-eID-Core-Rust/dependencies/epassport','pqc':workspace/'Phoenix-PQC-Rust','license':workspace/'Phoenix-License-Client','eid-reference':workspace/'Phoenix-eID-Core-Rust/dependencies/eid-reference'}
    locations={'host':'simulator','eid':'eid','epassport':'epassport','pqc':'pqc','license':'license','eid-reference':'eid-reference'}
    for name,source in sources.items(): clone(source,private/locations[name],lock[name]['commit'])
    link(private,'Phoenix-PQC-Rust',private/'pqc')
    for parent in [private/'eid',private/'epassport']:
        link(parent,'dependencies/Phoenix-License-Client',private/'license')
        link(parent,'dependencies/Phoenix-PQC-Rust',private/'pqc')
    link(private/'eid','dependencies/eid-reference',private/'eid-reference')
    link(private/'eid','dependencies/epassport',private/'epassport')
    link(private/'simulator','components/eid',private/'eid')
    print('Private source inputs prepared; card core source files remain unchanged.')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('workspace',type=Path);args=parser.parse_args();prepare(args.workspace.resolve())
