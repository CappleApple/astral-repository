"""Validate and collect the original build and all seven loader ports."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = re.search(r'^mod_version=(.+)$', (ROOT / 'gradle.properties').read_text(), re.MULTILINE).group(1).strip()
TARGETS = [
    ('1.21.1', 'neoforge', ROOT, 65),
    ('1.20.1', 'forge', ROOT / 'ports/forge-1.20.1', 61),
    ('1.20.1', 'fabric', ROOT / 'ports/fabric-1.20.1', 61),
    ('1.21.1', 'fabric', ROOT / 'ports/fabric-1.21.1', 65),
    ('26.2', 'fabric', ROOT / 'ports/fabric-26.2', 69),
    ('26.2', 'neoforge', ROOT / 'ports/neoforge-26.2', 69),
    ('26.3', 'fabric', ROOT / 'ports/fabric-26.3', 69),
    ('26.3', 'neoforge', ROOT / 'ports/neoforge-26.3', 69),
]
MAIN = 'com/cappleapple/astralrepository/AstralRepository.class'


def inspect(source: Path, minecraft: str, loader: str, major: int, original: bool):
    with zipfile.ZipFile(source) as jar:
        names = jar.namelist()
        if jar.read('LICENSE') != (ROOT / 'LICENSE').read_bytes():
            raise ValueError(f'Incorrect project license in {source}')
        if jar.testzip() is not None:
            raise ValueError(f'Corrupt JAR: {source}')
        actual = struct.unpack('>H', jar.read(MAIN)[6:8])[0]
        if actual != major:
            raise ValueError(f'{source}: Java class version {actual}, expected {major}')
        if loader == 'fabric':
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata['id'] != 'astral_repository' or metadata['version'] != VERSION:
                raise ValueError(f'Unexpected Fabric metadata: {source}')
            if minecraft not in metadata['depends']['minecraft']:
                raise ValueError(f'Wrong Minecraft dependency: {source}')
            if 'smoke' in json.dumps(metadata.get('entrypoints', {})).lower():
                raise ValueError(f'Development entrypoint in {source}')
            for nested in metadata.get('jars', []):
                if nested['file'] not in names:
                    raise ValueError(f'Missing bundled dependency {nested["file"]}: {source}')
        else:
            path = 'META-INF/mods.toml' if loader == 'forge' else 'META-INF/neoforge.mods.toml'
            metadata = jar.read(path).decode()
            if 'modId="astral_repository"' not in metadata.replace(' ', '') or minecraft not in metadata:
                raise ValueError(f'Unexpected loader metadata: {source}')
            if '${' in metadata:
                raise ValueError(f'Unexpanded loader metadata: {source}')
        if 'CC-BY-NC-SA-4.0' not in str(metadata):
            raise ValueError(f'Incorrect license metadata in {source}')
        if not original:
            forbidden = ('/gametest/', '/porttest/', '/smoke/')
            leaked = [name for name in names if name.endswith('.class') and any(part in name for part in forbidden)]
            if leaked:
                raise ValueError(f'Test classes in {source}: {leaked}')
        if loader == 'forge':
            mixins = json.loads(jar.read('astral_repository.mixins.json'))
            refmap = mixins.get('refmap')
            if not refmap or refmap not in names or not json.loads(jar.read(refmap)).get('mappings'):
                raise ValueError(f'Missing or empty production Mixin refmap: {source}')
        if 'assets/astral_repository/lang/en_us.json' not in names:
            raise ValueError(f'Missing production resources: {source}')
        return sum(name.endswith('.class') for name in names)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--branch-root', type=Path, help='Directory containing standalone loader-version checkouts; the original build still comes from the repository root.')
    args = parser.parse_args()
    output = ROOT / 'build/ports'
    output.mkdir(parents=True, exist_ok=True)
    results = []
    for minecraft, loader, project, major in TARGETS:
        original = project == ROOT
        if args.branch_root and not original:
            project = (args.branch_root / f'{loader}-{minecraft}').resolve()
        candidates = [path for path in (project / 'build/libs').glob(f'*{VERSION}.jar')
                      if not any(part in path.name for part in ('-sources', '-dev', '-javadoc'))]
        if len(candidates) != 1:
            raise ValueError(f'Expected one production JAR for {project}, found {candidates}')
        source = candidates[0]
        classes = inspect(source, minecraft, loader, major, original)
        name = f'astral_repository-{VERSION}-{minecraft}-{loader}.jar'
        target = output / name
        shutil.copyfile(source, target)
        results.append(dict(minecraft=minecraft, loader=loader, mod_version=VERSION, file=name,
                            bytes=target.stat().st_size, sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                            java_class_version=major, classes=classes, source=source.relative_to(project).as_posix(), branch='main' if original else f'CappleApple/{loader}-{minecraft}'))
    manifest = output / 'manifest.json'
    manifest.write_text(json.dumps(results, indent=2) + '\n')
    hashes = output / 'SHA256SUMS.txt'
    hashes.write_text(''.join(f'{row["sha256"]}  {row["file"]}\n' for row in results))
    instructions = output / 'INSTALL.txt'
    instructions.write_text(
        'Astral Repository ' + VERSION + '\n\n'
        'Install exactly ONE JAR matching both your Minecraft version and loader.\n'
        'Install the matching JAR on both the client and server.\n'
        'Fabric targets require Fabric API; their supporting configuration/model/energy libraries are bundled.\n'
        'Java runtime: 1.20.1 = Java 17; 1.21.1 = Java 21; 26.2 and 26.3 = Java 25.\n'
        'The original 1.21.1 NeoForge build is included.\n'
        'The 26.3 NeoForge build was validated with the beta loader 26.3.0.6-beta.\n'
        'The manifest records exact artifacts and SHA-256 checksums.\n')
    archive = output / f'astral_repository-{VERSION}-all-targets.zip' 
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        for row in results:
            bundle.write(output / row['file'], row['file'])
        for file in (manifest, hashes, instructions):
            bundle.write(file, file.name)
    print(f'Collected {len(results)} verified JARs in {output}')
    print(archive)


if __name__ == '__main__':
    main()
