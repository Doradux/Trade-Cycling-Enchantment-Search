"""Read-only packaging checks. Run after gradlew build (Python 3.11+)."""
import hashlib
import json
from pathlib import Path
import struct
import tomllib
from zipfile import ZipFile

project = Path(__file__).resolve().parent
artifact = project / 'build/libs/trade-cycling-enchantment-search-forge-1.20.1-1.1.0.jar'
with ZipFile(artifact) as jar:
    metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
    mod = metadata['mods'][0]
    assert mod['modId'] == 'trade_search_cycler'
    assert mod['version'] == '1.1.0'
    assert mod['authors'] == 'Doradux'
    assert jar.read(mod['logoFile']) == (project / 'src/main/resources/logo.png').read_bytes()
    en = json.loads(jar.read('assets/trade_search_cycler/lang/en_us.json'))
    es = json.loads(jar.read('assets/trade_search_cycler/lang/es_es.json'))
    assert en.keys() == es.keys(), 'Translation keys differ'
    classes = [name for name in jar.namelist() if name.endswith('.class')]
    assert all(struct.unpack('>H', jar.read(name)[6:8])[0] == 61 for name in classes)
    assert not any(name.startswith(('net/minecraft/', 'de/maxhenkel/')) for name in classes)
    assert not any('RegressionTests' in name for name in classes)
print('PASS: metadata, author, icon, translations, Java 17 and clean addon packaging')
print('SHA256:', hashlib.sha256(artifact.read_bytes()).hexdigest())
print('Artifact:', artifact)
