package org.dynmap.debug;

public class NullDebugger implements Debugger {
	@Override
	public void debug(String message) {
	}

	@Override
	public void error(String message) {
	}

	@Override
	public void error(String message, Throwable thrown) {
	}

}
