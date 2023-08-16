package to.rtc.cli.migrate.util;

import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class DelegatingChangeLogOutput implements IChangeLogOutput {
    private final IChangeLogOutput delegate;

    protected DelegatingChangeLogOutput(IChangeLogOutput delegate) {
        this.delegate = delegate;
    }

    public IChangeLogOutput getDelegate() {
        return delegate;
    }

    @Override
    public void setIndent(int arg0) {
        delegate.setIndent(arg0);
    }

    @Override
    public void writeLine(String arg0) {
        delegate.writeLine(arg0);
    }
}
