package org.dynmap.servlet;

import org.eclipse.jetty.util.log.Logger;

public class JettyNullLogger implements Logger {
    public String getName() {
        return "Dynmap";
    }

    public void warn(String s, Object... objects) {
    }

    public void warn(Throwable throwable) {
    }

    public void warn(String s, Throwable throwable) {
    }

    public void info(String s, Object... objects) {
    }

    public void info(Throwable throwable) {
    }

    public void info(String s, Throwable throwable) {
    }

    public boolean isDebugEnabled() {
        return false;
    }

    public void setDebugEnabled(boolean b) {
    }

    public void debug(String s, Object... objects) {
    }

    @Override
    public void debug(String s, long l) {
    }

    public void debug(Throwable throwable) {
    }

    public void debug(String s, Throwable throwable) {
    }

    public Logger getLogger(String s) {
        return this;
    }

    public void ignore(Throwable throwable) {
    }
}
