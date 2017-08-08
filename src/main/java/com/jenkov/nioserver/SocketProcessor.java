package com.jenkov.nioserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.jenkov.nioserver.memory.MemoryManager;

/**
 * Created by jjenkov on 16-10-2015.
 * Modified by nagalun on 08-08-2017.
 */
public class SocketProcessor implements Runnable {

	private final ServerSocketChannel serverSocket = ServerSocketChannel.open();
	private final int tcpPort;
	private final boolean noDelay;

	private final IMessageReaderFactory messageReaderFactory;

	//private final Map<Long, Socket> socketMap = new HashMap<>();

	private final MemoryManager memoryManager = new MemoryManager(1024); /* 1024 * 512 bytes */

	private final Selector generalSelector = Selector.open();

	private final IEventHandler eventHandler;

	public SocketProcessor(int tcpPort, IMessageReaderFactory messageReaderFactory, IEventHandler eventHandler,
			List<SocketOption<Boolean>> opt) throws IOException {
		this.tcpPort = tcpPort;
		this.serverSocket.configureBlocking(false);

		this.messageReaderFactory = messageReaderFactory;

		this.eventHandler = eventHandler;
		this.noDelay = opt.contains(StandardSocketOptions.TCP_NODELAY);
		if (opt.contains(StandardSocketOptions.SO_REUSEADDR)) {
			this.serverSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		}
	}

	public void run() {
		try {
			this.serverSocket.bind(new InetSocketAddress(tcpPort));
			serverSocket.register(generalSelector, SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		while (serverSocket.isOpen()) {
			try {
				if (generalSelector.select() == 0) {
					continue;
				}

				executeCycle();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			generalSelector.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		generalSelector.wakeup();
	}

	public void executeCycle() throws IOException {
		Set<SelectionKey> selectedKeys = generalSelector.selectedKeys();
		Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

		while (keyIterator.hasNext()) {
			SelectionKey key = keyIterator.next();
			int readyOps = key.isValid() ? key.readyOps() : 0;
			try {
				if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
					socketOpened(this.serverSocket.accept());
					continue;
				}
				
				Socket socket = (Socket) key.attachment();
				
				if (socket != null) {
					if ((readyOps & SelectionKey.OP_READ) != 0) {
						readFromSocket(socket);
						if (!key.isValid()) {
							/* Socket was probably closed */
							continue;
						}
					}
					
					if ((readyOps & SelectionKey.OP_WRITE) != 0) {
						socket.writeQueuedMessages();
						/* No need to check if key is valid (last operation) */
					}
					
					if (socket.awaitingClose) {
						socket.close();
						continue;
					}
				}
			} catch (final Throwable e) { /* Try to not crash D: */
				e.printStackTrace();
			} finally {
				keyIterator.remove();
			}
		}
	}

	private void readFromSocket(Socket socket) throws IOException {
		/* Message reader should pass the full messages to the application via the IEventHandler */
		socket.messageReader.read(socket);
	}

	/* For external calls */
	public void interestSocket(Socket socket, int ops) {
		SelectionKey key = socket.socketChannel.keyFor(this.generalSelector);
		if (key.isValid()) {
			key.interestOps(ops);
		}
	}

	private void socketOpened(SocketChannel sc) throws IOException {
		if (sc == null) {
			return;
		}

		IMessageReader msgReader = this.messageReaderFactory.createMessageReader(this.memoryManager, eventHandler);

		Socket newSocket = new Socket(sc, this, msgReader);
		sc.configureBlocking(false);

		if (this.noDelay) {
			sc.setOption(StandardSocketOptions.TCP_NODELAY, true);
		}

		//this.socketMap.put(newSocket.socketId, newSocket);

		sc.register(this.generalSelector, SelectionKey.OP_READ, newSocket);
		eventHandler.onSocketOpen(newSocket);
	}

	public void closedSocket(Socket socket) {
		SelectionKey key = socket.socketChannel.keyFor(this.generalSelector);
		//socketMap.remove(socket.socketId);
		key.attach(null);
		eventHandler.onSocketClose(socket);
	}

	public MemoryManager getMemoryManager() {
		return this.memoryManager;
	}
	
	public SocketAddress getAddress() {
		try {
			return serverSocket.getLocalAddress();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
