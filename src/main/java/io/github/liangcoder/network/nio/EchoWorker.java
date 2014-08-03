package io.github.liangcoder.network.nio;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EchoWorker implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(EchoWorker.class);

	private List<ServerDataEvent> queue = new LinkedList<ServerDataEvent>();

	@Override
	public void run() {
		logger.info("start the EchoWorker");

		ServerDataEvent dataEvent;

		while (true) {
			synchronized (queue) {
				while (queue.isEmpty()) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
					}
				}
				dataEvent = queue.remove(0);
			}
			dataEvent.server.send(dataEvent.socket, dataEvent.data);
		}
	}

	public void processData(NioServer server, SocketChannel socket, byte[] data, int count) {
		byte[] dataCopy = new byte[count];
		System.arraycopy(data, 0, dataCopy, 0, count);
		synchronized (queue) {
			queue.add(new ServerDataEvent(server, socket, dataCopy));
			queue.notify();
		}
	}

	private class ServerDataEvent {
		private NioServer server;

		private SocketChannel socket;

		private byte[] data;

		public ServerDataEvent(NioServer server, SocketChannel socket, byte[] data) {
			this.server = server;
			this.socket = socket;
			this.data = data;
		}
	}

}
