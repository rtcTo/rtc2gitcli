package to.rtc.cli.migrate;

/**
 * Abstracts stdout
 *
 * @author peter.darton
 */
public interface ILineLogger {
	/**
	 * Logs a line of text, typically with a timestamp prepended.
	 * 
	 * @param lineOfText The human-readable line of text to be logged.
	 */
	void writeLineToLog(String lineOfText);
}
