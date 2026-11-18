import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { compareManifests } from './packet-contract-ratchet.mjs';

const packet = (direction, header) => ({ name: `P${ header }`, direction, header });

const manifest = ({ contracts = [], unpaired = [], exemptions = [] } = {}) => ({
    schemaVersion: 2,
    contracts,
    unpaired,
    exemptions
});

const base = manifest({
    contracts: [ packet('server_to_client', 2000), packet('client_to_server', 10) ],
    exemptions: [ packet('server_to_client', 3000), packet('server_to_client', 3001), packet('client_to_server', 11) ]
});

describe('packet contract coverage ratchet', () =>
{
    it('passes when nothing changes', () =>
    {
        assert.deepEqual(compareManifests(base, base, []), []);
    });

    it('fails when a contract becomes an exemption', () =>
    {
        const head = manifest({
            contracts: [ packet('client_to_server', 10) ],
            exemptions: [ ...base.exemptions, packet('server_to_client', 2000) ]
        });

        const errors = compareManifests(base, head, []);

        assert.ok(errors.some(error => error.includes('server_to_client:2000 left contracts')), errors.join('\n'));
    });

    it('fails when a contract becomes unpaired', () =>
    {
        const head = manifest({
            contracts: [ packet('client_to_server', 10) ],
            unpaired: [ packet('server_to_client', 2000) ],
            exemptions: base.exemptions
        });

        const errors = compareManifests(base, head, []);

        assert.ok(errors.some(error => error.includes('server_to_client:2000 left contracts')), errors.join('\n'));
    });

    it('fails when the exemption count rises', () =>
    {
        const head = manifest({
            contracts: base.contracts,
            exemptions: [ ...base.exemptions, packet('client_to_server', 12) ]
        });

        const errors = compareManifests(base, head, []);

        assert.ok(errors.some(error => error.includes('client_to_server:12 became exempt')), errors.join('\n'));
    });

    it('fails when a new exemption hides behind a packet that became a contract', () =>
    {
        const head = manifest({
            contracts: [ ...base.contracts, packet('server_to_client', 3000) ],
            exemptions: [ packet('server_to_client', 3001), packet('client_to_server', 11), packet('client_to_server', 12) ]
        });

        const errors = compareManifests(base, head, []);

        assert.ok(errors.some(error => error.includes('client_to_server:12 became exempt')), errors.join('\n'));
    });

    it('fails when an unpaired packet becomes exempt', () =>
    {
        const withUnpaired = manifest({ ...base, unpaired: [ packet('client_to_server', 12) ] });
        const head = manifest({ contracts: base.contracts, exemptions: [ ...base.exemptions, packet('client_to_server', 12) ] });

        const errors = compareManifests(withUnpaired, head, []);

        assert.ok(errors.some(error => error.includes('client_to_server:12 became exempt')), errors.join('\n'));
    });

    it('accepts a declared exception', () =>
    {
        const head = manifest({
            contracts: [ packet('client_to_server', 10) ],
            exemptions: [ ...base.exemptions, packet('server_to_client', 2000) ]
        });

        const exceptions = [ { direction: 'server_to_client', header: 2000, reason: 'needs a runtime decision' } ];

        assert.deepEqual(compareManifests(base, head, exceptions), []);
    });

    it('rejects an exception without reason', () =>
    {
        const exceptions = [ { direction: 'server_to_client', header: 2000, reason: ' ' } ];

        const errors = compareManifests(base, base, exceptions);

        assert.ok(errors.some(error => error.includes('exception without reason')), errors.join('\n'));
    });

    it('passes when exemptions fall and contracts rise', () =>
    {
        const head = manifest({
            contracts: [ ...base.contracts, packet('server_to_client', 3000) ],
            exemptions: [ packet('server_to_client', 3001), packet('client_to_server', 11) ]
        });

        assert.deepEqual(compareManifests(base, head, []), []);
    });
});
