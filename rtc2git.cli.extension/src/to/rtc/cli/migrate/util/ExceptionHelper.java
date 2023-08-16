package to.rtc.cli.migrate.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.IStatus;

import com.ibm.team.rtc.cli.infrastructure.internal.core.CLIClientException;

public class ExceptionHelper {
    /**
     * The {@link CLIClientException} doesn't have a useful
     * {@link Throwable#toString()} or {@link Throwable#getMessage()}. This provides
     * one
     * 
     * @param ex The exception
     * @return the full contents of that exception
     */
    public static String cliClientExceptionToString(final CLIClientException ex) {
        StringBuilder b = new StringBuilder();
        b.append(ex.getClass().getName()).append('[');
        b.append("showErrorMessage=").append(ex.showErrorMessage());
        b.append(", status=");
        append(b, ex.getStatus());
        b.append(']');
        return b.toString();
    }

    /**
     * Returns {@link Throwable#toString()}, unless it's a
     * {@link CLIClientException} in which case we return
     * {@link #cliClientExceptionToString(CLIClientException)}.
     * 
     * @param ex The exception
     * @return the full contents of that exception
     */
    public static String exceptionToString(final Throwable ex) {
        if (ex instanceof CLIClientException) {
            return cliClientExceptionToString((CLIClientException) ex);
        }
        return ex.toString();
    }

    /**
     * Returns the contents of an {@link IStatus} as a string.
     * 
     * @param s The {@link IStatus} instance.
     * @return the full contents.
     */
    public static String istatusToString(final IStatus s) {
        StringBuilder b = new StringBuilder();
        append(b, s);
        return b.toString();
    }

    private static void append(StringBuilder b, final IStatus s) {
        if (s == null) {
            b.append("null");
            return;
        }
        b.append("IStatus[");
        appendContent(b, s);
        b.append(']');
    }

    private static void appendContent(StringBuilder b, final IStatus s) {
        b.append("code=").append(s.getCode());
        b.append(", exception=");
        final Throwable ex = s.getException();
        if (ex == null) {
            b.append("null");
        } else {
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            pw.flush();
            b.append(sw.toString());
        }
        b.append(", message=");
        appendContent(b, s.getMessage());
        b.append(", plugin=").append(s.getPlugin());
        b.append(", severity=").append(s.getSeverity());
        b.append(", multiStatus=").append(s.isMultiStatus());
        b.append(", ok=").append(s.isOK());
        final IStatus[] sc = s.getChildren();
        b.append(", children=");
        if (sc == null) {
            b.append("null");
        } else {
            b.append('[');
            for (final IStatus c : sc) {
                append(b, c);
            }
            b.append(']');
        }
    }

    private static void appendContent(StringBuilder b, final String s) {
        if (s == null) {
            b.append("null");
        } else {
            b.append('"');
            final String regexBackslash = "\\\\";
            final String quote = "\"";
            final String cr = "\r";
            final String lf = "\n";
            final String escaped = s.replaceAll(regexBackslash, regexBackslash + regexBackslash)
                    .replaceAll(quote, regexBackslash + quote).replaceAll(cr, regexBackslash + "r")
                    .replaceAll(lf, regexBackslash + "n");
            b.append(escaped);
            b.append('"');
        }
    }
}
