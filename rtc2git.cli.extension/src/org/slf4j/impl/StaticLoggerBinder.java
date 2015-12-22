package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

/**
 * Default NoOp logger binder implementation to satisfy minimum requirements.
 *
 * @author patrick.reinhart
 */
public enum StaticLoggerBinder implements ILoggerFactory {
	INSTANCE;

	public static StaticLoggerBinder getSingleton() {
		return StaticLoggerBinder.INSTANCE;
	}

	public ILoggerFactory getLoggerFactory() {
		return this;
	}

	@Override
	public Logger getLogger(String name) {
		return NOPLogger.NOP_LOGGER;
	}
}
