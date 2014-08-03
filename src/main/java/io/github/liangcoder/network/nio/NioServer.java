package io.github.liangcoder.network.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioServer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(NioServer.class);

	private InetAddress hostAddress;
	private int port;

	private ServerSocketChannel serverChannel;

	private Selector selector;

	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);

	private EchoWorker worker;

	private List<ChangeRequest> changeRequests = new LinkedList<ChangeRequest>();

	private Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<SocketChannel, List<ByteBuffer>>();

	public NioServer(InetAddress hostAddress, int port, EchoWorker worker) throws IOException {
		this.hostAddress = hostAddress;
		this.port = port;
		this.selector = this.initSelector();
		this.worker = worker;
	}

	private Selector initSelector() throws IOException {
		// create a selector
		Selector socketSelector = SelectorProvider.provider().openSelector();

		// create a non-blocking server socket channel
		this.serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);

		// bind the server socket to the specified address and port
		InetSocketAddress isa = new InetSocketAddress(this.hostAddress, this.port);
		serverChannel.socket().bind(isa);

		// register the server socket channel, indicating an interest in accepting new connections
		serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

		return socketSelector;
	}

	@Override
	public void run() {
		logger.info("start the NioServer");

		while (true) {
			try {
				// / process any pending changes
				synchronized (this.changeRequests) {
					Iterator<ChangeRequest> changes = this.changeRequests.iterator();
					while (changes.hasNext()) {
						ChangeRequest change = changes.next();
						switch (change.type) {
						case ChangeRequest.CHANGEEOPS:
							SelectionKey key = change.socket.keyFor(this.selector);
							key.interestOps(change.ops);
						}
					}
				}

				this.selector.select();

				Iterator<SelectionKey> selectKeys = this.selector.selectedKeys().iterator();
				while (selectKeys.hasNext()) {
					SelectionKey key = selectKeys.next();
					selectKeys.remove();
					logger.debug("selector on key [{}]", key);

					if (!key.isValid()) {
						continue;
					}

					if (key.isAcceptable()) {
						this.accept(key);
					} else if (key.isReadable()) {
						this.read(key);
					} else if (key.isWritable()) {
						this.write(key);
					}
				}

			} catch (Exception e) {
				logger.error("caught an exception in the Selector event loop", e);
			}
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);
		socketChannel.register(this.selector, SelectionKey.OP_READ);
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();

		this.readBuffer.clear();
		int numRead = 0;
		try {
			numRead = socketChannel.read(readBuffer);
		} catch (IOException e) {
			key.cancel();
			socketChannel.close();
			return;
		}

		if (numRead == -1) {
			key.channel().close();
			key.cancel();
			return;
		}

		this.worker.processData(this, socketChannel, this.readBuffer.array(), numRead);
	}

	private void write(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		synchronized (this.pendingData) {
			List<ByteBuffer> queue = this.pendingData.get(socketChannel);
			while (!queue.isEmpty()) {
				ByteBuffer buf = queue.get(0);
				socketChannel.write(buf);
				if (buf.remaining() > 0) {
					break;
				}
				queue.remove(0);
			}

			if (queue.isEmpty()) {
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	public void send(SocketChannel socket, byte[] data) {
		synchronized (this.changeRequests) {
			this.changeRequests.add(new ChangeRequest(socket, ChangeRequest.CHANGEEOPS, SelectionKey.OP_WRITE));

			synchronized (this.pendingData) {
				List<ByteBuffer> queue = this.pendingData.get(socket);
				if (queue == null) {
					this.pendingData.put(socket, new ArrayList<ByteBuffer>());
				}
				queue.add(ByteBuffer.wrap(data));
			}
		}

		this.selector.wakeup();
	}
}
