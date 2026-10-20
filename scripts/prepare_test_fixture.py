"""Pin public PersoSim test data; retain signed files, DGs and test CA keys verbatim."""
from pathlib import Path
import hashlib
import re
import subprocess

ROOT=Path(__file__).resolve().parents[1]
COMMIT="2b04578928ae5a1b40189dabe87c4939cb69bdf1"
PROFILE_SHA="17d430207a03953f6061d9902c860e77dfa71d8bf48324ed54c99a9c6a747155"
ADAPTED_SHA="b915bdfe2c322c0fb9168933d8035d1137c08616ae7ad31f583d5a5df7e33764"
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
    original=checked(repository/"de.persosim.simulator/personalization/profiles/Profile03.perso",PROFILE_SHA).decode()
    # Local administration uses today's date and the independently pinned current Governikus CVCA.
    # These two unsigned nodes are not part of any signed security object or data group.
    for tag in ["TrustPointCardObject","DateTimeCardObject"]:
        original,count=re.subn(r"<de\.persosim\.simulator\.cardobjects\."+tag+r"\b[^>]*>.*?</de\.persosim\.simulator\.cardobjects\."+tag+r">","",original,flags=re.S)
        if count!=1: raise ValueError("Unexpected PersoSim administration structure")
    output=ROOT/".private/PersoSimProfile03.perso"
    if hashlib.sha256(original.encode()).hexdigest()!=ADAPTED_SHA: raise ValueError("Unexpected adapted fixture")
    output.write_text(original)
    source=workspace/"Phoenix-ePassport-Core/BSI-PKI/CSCA/certificates/test/[ROOT-CA]_Test-CSCA09.cer"
    (ROOT/".private/persosim-csca.cer").write_bytes(checked(source,CSCA_SHA))

def verify():
    checked(ROOT/".private/PersoSimProfile03.perso",ADAPTED_SHA)
    checked(ROOT/".private/persosim-csca.cer",CSCA_SHA)

if __name__=="__main__":
    import sys
    prepare(Path(sys.argv[1]).resolve())
