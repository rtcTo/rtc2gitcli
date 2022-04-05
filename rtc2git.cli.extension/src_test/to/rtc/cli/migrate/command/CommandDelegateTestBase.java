package to.rtc.cli.migrate.command;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.ibm.team.filesystem.cli.core.AbstractSubcommand;

abstract class CommandDelegateTestBase {

	protected void testCommandMatchesClassName(RtcCommandDelegate instanceUnderTest) throws Exception {
		final String expectedRtcCommandClassNameEnding = "Cmd";
		testCommandMatchesClassName(instanceUnderTest, expectedRtcCommandClassNameEnding);
	}

	protected void testCommandMatchesClassName(RtcCommandDelegate instanceUnderTest,
			final String expectedRtcCommandClassNameEnding) {
		final String instanceClassName = getSimpleClassName(instanceUnderTest.getClass());
		assertThat(instanceClassName, endsWith("CommandDelegate"));
		final String expectedCommand = instanceClassName.replaceFirst("CommandDelegate$", "");

		final AbstractSubcommand rtcCommand = instanceUnderTest.getCommand();
		final String rtcCommandClassName = rtcCommand.getClass().getSimpleName();
		assertThat(rtcCommandClassName, endsWith(expectedRtcCommandClassNameEnding));
		assertThat(rtcCommandClassName, equalTo(expectedCommand + expectedRtcCommandClassNameEnding));
	}

	private static String getSimpleClassName(final Class<?> o) {
		final String result = o.getSimpleName();
		if (!result.isEmpty()) {
			return result;
		}
		final Class<?> superclass = o.getSuperclass();
		if (superclass == null) {
			return "";
		}
		return getSimpleClassName(superclass);
	}
}
