package to.rtc.cli.migrate.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

public class CapturingPrintStream extends PrintStream {
    private final PrintStream delegate;
    private final CapturingWrapper capturer;

    public CapturingPrintStream(PrintStream delegate, CapturingWrapper capturer) {
        super(System.out); // not really going there, but we had to say something
        this.delegate = delegate;
        this.capturer = capturer;
    }

    @Override
    public PrintStream append(char c) {
        capturer.getIncompleteLine().append(c);
        return delegate.append(c);
    }

    @Override
    public PrintStream append(CharSequence csq, int start, int end) {
        capturer.getIncompleteLine().append(csq, start, end);
        return delegate.append(csq, start, end);
    }

    @Override
    public PrintStream append(CharSequence csq) {
        capturer.getIncompleteLine().append(csq);
        return delegate.append(csq);
    }

    @Override
    public void print(char[] x) {
        capturer.getIncompleteLine().append(x);
        delegate.print(x);
    }

    @Override
    public void print(String x) {
        capturer.getIncompleteLine().append(x);
        delegate.print(x);
    }

    @Override
    public void println() {
        capturer.completeIncompleteLine();
        delegate.println();
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        capturer.getIncompleteLine().append(new String(buf, off, len));
        delegate.write(buf, off, len);
    }

    @Override
    public final PrintStream format(Locale l, String format, Object... args) {
        print(String.format(l, format, args));
        return this;
    }

    @Override
    public final PrintStream format(String format, Object... args) {
        print(String.format(format, args));
        return this;
    }

    @Override
    public final void print(boolean x) {
        print(String.valueOf(x));
    }

    @Override
    public final void print(double x) {
        print(String.valueOf(x));
    }

    @Override
    public final void print(float x) {
        print(String.valueOf(x));
    }

    @Override
    public final void print(int x) {
        print(String.valueOf(x));
    }

    @Override
    public final void print(long x) {
        print(String.valueOf(x));
    }

    @Override
    public final void print(Object x) {
        print(String.valueOf(x));
    }

    @Override
    public final PrintStream printf(Locale l, String format, Object... args) {
        return format(l, format, args);
    }

    @Override
    public final PrintStream printf(String format, Object... args) {
        return format(format, args);
    }

    @Override
    public final void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public final void write(int b) {
        write(new byte[] { (byte)b }, 0, 1);
    }

    @Override
    public final void print(char c) {
        print(new char[] { c });
    }

    @Override
    public final void println(boolean x) {
        print(x);
        println();
    }

    @Override
    public final void println(char x) {
        print(x);
        println();
    }

    @Override
    public final void println(char[] x) {
        print(x);
        println();
    }

    @Override
    public final void println(double x) {
        print(x);
        println();
    }

    @Override
    public final void println(float x) {
        print(x);
        println();
    }

    @Override
    public final void println(int x) {
        print(x);
        println();
    }

    @Override
    public final void println(long x) {
        print(x);
        println();
    }

    @Override
    public final void println(Object x) {
        print(x);
        println();
    }

    @Override
    public final void println(String x) {
        print(x);
        println();
    }

    @Override
    public boolean checkError() {
        return delegate.checkError();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void flush() {
        delegate.flush();
    }
}
