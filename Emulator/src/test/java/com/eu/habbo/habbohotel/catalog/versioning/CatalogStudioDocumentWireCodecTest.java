package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogStudioDocumentWireCodecTest {
    @Test
    void roundTripsSqlLargerThanOneWireString() {
        StringBuilder sql = new StringBuilder();
        for (int index = 0; index < 80_000; index++) {
            sql.append(Integer.toString(index, 36))
                    .append(':')
                    .append(Integer.toUnsignedString(index * 0x9E3779B1))
                    .append('|');
        }

        CatalogStudioDocumentWireCodec.EncodedDocument encoded = CatalogStudioDocumentWireCodec.encode(sql.toString());

        assertTrue(encoded.chunks().size() > 1);
        assertTrue(encoded.chunks().stream().allMatch(chunk -> chunk.length() <= Short.MAX_VALUE));
        assertEquals(sql.toString(), CatalogStudioDocumentWireCodec.decode(encoded.encoding(), encoded.chunks()));
    }

    @Test
    void rejectsTooManyChunksBeforeAllocatingTheDocument() {
        List<String> chunks = java.util.Collections.nCopies(CatalogStudioDocumentWireCodec.MAX_CHUNKS + 1, "A");

        assertThrows(
                IllegalArgumentException.class,
                () -> CatalogStudioDocumentWireCodec.decode(CatalogStudioDocumentWireCodec.ENCODING, chunks));
    }
}
