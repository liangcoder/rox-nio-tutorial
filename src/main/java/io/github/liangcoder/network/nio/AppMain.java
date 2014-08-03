package io.github.liangcoder.network.nio;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppMain {

	private static final Logger logger = LoggerFactory.getLogger(AppMain.class);

	public static void main(String[] args) {
		try {
			EchoWorker worker = new EchoWorker();
			new Thread(worker).start();
			new Thread(new NioServer(null, 9000, worker)).start();
		} catch (IOException e) {
			logger.error("create NioServer, failure.", e);
		}
	}

}
