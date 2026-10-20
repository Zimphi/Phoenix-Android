"""Pin public PersoSim test data; retain signed files, DGs and test CA keys verbatim."""
from pathlib import Path
import hashlib
import json
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
COMMIT="2b04578928ae5a1b40189dabe87c4939cb69bdf1"
CSCA_SHA="8037b8e55acbf1be1015922b7c6a87ce802d4994ee7bad3ab580162295faa728"
def checked(path,expected):
    data=path.read_bytes()
    if hashlib.sha256(data).hexdigest()!=expected: raise ValueError(f"Unexpected test fixture: {path.name}")
    return data

def prepare(workspace):
    repository=ROOT/".private/persosim"
    if not repository.exists():
        subprocess.run(["git","clone","https://github.com/PersoSim/de.persosim.simulator.git",str(repository)],check=True)
        subprocess.run(["git","-C",str(repository),"checkout","--detach",COMMIT],check=True)
    revision=subprocess.check_output(["git","-C",str(repository),"rev-parse","HEAD"],text=True).strip()
    if revision!=COMMIT: raise ValueError("Unexpected PersoSim source revision")
    manifest=json.loads((ROOT/"persosim-fixtures.lock.json").read_text())
    if manifest["commit"]!=COMMIT or len(manifest["profiles"])!=16: raise ValueError("Unexpected fixture inventory")
    output=ROOT/".private/persosim-profiles"
    output.mkdir(exist_ok=True)
    for entry in manifest["profiles"]:
        original=checked(repository/"de.persosim.simulator/personalization/profiles"/entry["file"],entry["source_sha256"]).decode()
        # Only unsigned administration is replaced by today's date and the pinned current CVCA.
        for tag in ["TrustPointCardObject","DateTimeCardObject"]:
            original,count=re.subn(r"<de\.persosim\.simulator\.cardobjects\."+tag+r"\b[^>]*>.*?</de\.persosim\.simulator\.cardobjects\."+tag+r">","",original,flags=re.S)
            if count!=1: raise ValueError("Unexpected PersoSim administration structure")
        if hashlib.sha256(original.encode()).hexdigest()!=entry["adapted_sha256"]: raise ValueError("Unexpected adapted fixture")
        (output/entry["file"]).write_text(original)
    source=workspace/"Phoenix-ePassport-Core/BSI-PKI/CSCA/certificates/test/[ROOT-CA]_Test-CSCA09.cer"
    (ROOT/".private/persosim-csca.cer").write_bytes(checked(source,CSCA_SHA))

def verify():
    for entry in json.loads((ROOT/"persosim-fixtures.lock.json").read_text())["profiles"]:
        checked(ROOT/".private/persosim-profiles"/entry["file"],entry["adapted_sha256"])
    checked(ROOT/".private/persosim-csca.cer",CSCA_SHA)

if __name__=="__main__":
    import sys
    prepare(Path(sys.argv[1]).resolve())
