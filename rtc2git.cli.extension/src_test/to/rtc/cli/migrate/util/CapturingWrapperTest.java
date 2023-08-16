package to.rtc.cli.migrate.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.Test;

public class CapturingWrapperTest {
    @Test
    public void getCapturedOutputGivenNothingThenReturnsEmptyList() {
        // Given
        final CapturingWrapper instance = new CapturingWrapper();

        // When
        final List<String> actual = instance.getCompleteLines();

        // Then
        assertThat(actual.isEmpty(), equalTo(true));
    }
}
