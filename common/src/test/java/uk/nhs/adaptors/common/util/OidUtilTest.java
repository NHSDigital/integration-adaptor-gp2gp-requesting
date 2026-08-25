package uk.nhs.adaptors.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class OidUtilTest {

    @Test
    public void shouldReturnUrnWhenOidIsValid() {
        var parsed = OidUtil.tryParseToUrn("1.2.826.1285.2.107");

        assertTrue(parsed.isPresent());
        assertEquals("urn:oid:1.2.826.1285.2.107", parsed.get());
    }

    @Test
    public void shouldReturnEmptyOptionalWhenOidIsInvalid() {
        var parsed = OidUtil.tryParseToUrn("urn:oid:1.2.3");

        assertTrue(parsed.isEmpty());
    }

    @Test
    public void shouldDetectValidAndInvalidOids() {
        assertTrue(OidUtil.isOid("2.16.840.1.113883.2.1.3.2.4.17.101"));
        assertFalse(OidUtil.isOid(""));
        assertFalse(OidUtil.isOid("0.0"));
    }
}
