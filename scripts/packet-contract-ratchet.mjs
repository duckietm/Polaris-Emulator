// Keeps packet contract coverage from going down: compares the manifest of this branch with the
// manifest of the branch it merges into. A packet that leaves `contracts`, or a rise in the number
// of exemptions, fails unless protocol/packet-contract-ratchet-exceptions.json declares it with a reason.
// This file is kept identical in the emulator and the renderer.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const key = packet => `${ packet.direction }:${ packet.header }`;

export const compareManifests = (base, head, exceptions) =>
{
    const errors = [];
    const excused = new Set();

    for(const exception of exceptions)
    {
        if(typeof exception.reason !== 'string' || !exception.reason.trim())
        {
            errors.push(`exception without reason: ${ key(exception) }`);
            continue;
        }
        excused.add(key(exception));
    }

    const headContracts = new Set(head.contracts.map(key));
    for(const packet of base.contracts)
    {
        const id = key(packet);
        if(!headContracts.has(id) && !excused.has(id)) errors.push(`${ id } left contracts (${ packet.name })`);
    }

    // Packet by packet, not by count: a contract gained elsewhere must not hide a new exemption.
    const baseExemptions = new Set(base.exemptions.map(key));
    for(const packet of head.exemptions)
    {
        const id = key(packet);
        if(!baseExemptions.has(id) && !excused.has(id)) errors.push(`${ id } became exempt (${ packet.name })`);
    }

    return errors;
};

const signed = value => (value >= 0 ? `+${ value }` : `${ value }`);

const main = () =>
{
    const argumentsList = process.argv.slice(2);
    const baseIndex = argumentsList.indexOf('--base');
    if(baseIndex < 0 || !argumentsList[baseIndex + 1])
    {
        console.error('Usage: node scripts/packet-contract-ratchet.mjs --base <git-ref>');
        process.exit(2);
    }

    const root = fileURLToPath(new URL('..', import.meta.url));
    const manifestPath = 'protocol/packet-field-contracts.json';
    const base = JSON.parse(execFileSync('git', [ 'show', `${ argumentsList[baseIndex + 1] }:${ manifestPath }` ],
        { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }));
    const head = JSON.parse(readFileSync(new URL(`../${ manifestPath }`, import.meta.url), 'utf8'));
    const exceptions = JSON.parse(readFileSync(new URL('../protocol/packet-contract-ratchet-exceptions.json', import.meta.url), 'utf8'));

    const errors = compareManifests(base, head, exceptions);
    if(errors.length)
    {
        errors.forEach(error => console.error(error));
        process.exit(1);
    }

    console.log(`Coverage ratchet ok: ${ head.contracts.length } verified (${ signed(head.contracts.length - base.contracts.length) }), `
        + `${ head.exemptions.length } exempt (${ signed(head.exemptions.length - base.exemptions.length) })`);
};

if(process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) main();
