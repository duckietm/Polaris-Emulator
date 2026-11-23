package polaris.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.messages.outgoing.rooms.pets.PetPackageNameValidationComposer;

/** The detail the pet name dialog puts into its message for a refused name. */
class PetPackageNameDetailTest {
    @Test
    void invalidCharactersAreNamedOnceEach() {
        assertEquals(
                "! ?",
                PetPackageNameEvent.errorDetail(PetPackageNameValidationComposer.CONTAINS_INVALID_CHARS, "Re!x ?!"));
    }

    @Test
    void otherAnswersCarryNoDetail() {
        assertEquals("", PetPackageNameEvent.errorDetail(PetPackageNameValidationComposer.CLOSE_WIDGET, "Rex"));
        assertEquals("", PetPackageNameEvent.errorDetail(PetPackageNameValidationComposer.FORBIDDEN_WORDS, "Rex"));
        assertEquals(
                "", PetPackageNameEvent.errorDetail(PetPackageNameValidationComposer.CONTAINS_INVALID_CHARS, null));
    }

    @Test
    void theClientReadsOneAsTooLongAndTwoAsTooShort() {
        assertEquals(1, PetPackageNameValidationComposer.NAME_TOO_LONG);
        assertEquals(2, PetPackageNameValidationComposer.NAME_TOO_SHORT);
    }
}
