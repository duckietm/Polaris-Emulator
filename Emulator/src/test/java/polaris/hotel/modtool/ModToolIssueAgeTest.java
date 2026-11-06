package polaris.hotel.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModToolIssueAgeTest {
    @Test
    void ageIsSentInMilliseconds() {
        assertEquals(90_000, ModToolIssue.ageInMilliseconds(1_000_090, 1_000_000));
    }

    @Test
    void ageNeverGoesNegative() {
        assertEquals(0, ModToolIssue.ageInMilliseconds(1_000_000, 1_000_050));
    }

    @Test
    void ageOfAnIssueOpenForWeeksIsCappedInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, ModToolIssue.ageInMilliseconds(2_000_000_000, 1_000_000_000));
    }
}
