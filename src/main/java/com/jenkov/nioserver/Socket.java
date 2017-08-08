package com.jenkov.nioserver;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.jenkov.nioserver.memory.MappedMemory;

/**
 * Created by jjenkov on 16-10-2015.
 * Modified by nagalun on 07-08-2017.
 */
public class Socket {

	private final SocketProcessor socketProcessor;

	private final MessageWriter messageWriter = new MessageWriter(this);
	public final SocketChannel socketChannel;
	private int registeredOps = SelectionKey.OP_READ; /* Guess */
	public IMessageReader messageReader;

	public boolean endOfStreamReached = false;
	public boolean awaitingClose = false;

	private final SocketAddress address;
	public Object metaData = null;

	public Socket(SocketChannel socketChannel, SocketProcessor socketProcessor, IMessageReader msgReader) {
		this.socketChannel = socketChannel;
		this.socketProcessor = socketProcessor;
		this.messageReader = msgReader;
		SocketAddress addr = null;
		try {
			addr = socketChannel.getRemoteAddress();
		} catch (final IOException e) {
			/* Why? */
			e.printStackTrace();
			deferredClose();
		}
		this.address = addr;
	}

	public final int read(final ByteBuffer byteBuffer) {
		/*
		 * while (bytesRead > 0) { bytesRead = this.socketChannel.read(byteBuffer);
		 * 
		 * totalBytesRead += bytesRead; }
		 */

		try {
			return this.socketChannel.read(byteBuffer);
		} catch (final IOException e) {
			return -1;
		}
	}

	public final int write(final ByteBuffer byteBuffer) {
		/* TODO: check for EWOULDBLOCK? */
		/*
		 * while (bytesWritten > 0 && byteBuffer.hasRemaining()) { /* TODO: check if
		 * this is necessary bytesWritten = this.socketChannel.write(byteBuffer);
		 * totalBytesWritten += bytesWritten; }
		 */

		try {
			return this.socketChannel.write(byteBuffer);
		} catch (final IOException e) {
			return -1;
		}
	}

	public final void writeQueuedMessages() {
		if (messageWriter.isEmpty()) {
			registerWrites(false);
			return;
		}
		
		MessageWriter.Status s;
		do {
			s = messageWriter.write();
		} while (s == MessageWriter.Status.OK);
		
		if (s == MessageWriter.Status.ERROR) {
			close();
		}
	}

	public final void queueMessage(final byte[] arr) {
		if (endOfStreamReached || awaitingClose) {
			return;
		}
		
		final MappedMemory msg = this.socketProcessor.getMemoryManager().getMemory(arr.length);
		final ByteBuffer bb = msg.getBuffer();
		bb.put(arr);
		bb.flip();
		queueMessage(msg);
		msg.unref();
	}

	public final void queueMessage(final MappedMemory msg) {
		if (endOfStreamReached || awaitingClose) {
			return;
		}
		
		registerWrites(true);
		switch (messageWriter.enqueue(msg)) {
		case EMPTY:
			registerWrites(false);
			break;

		case ERROR:
			deferredClose();
			break;

		default:
			break;
		}
	}

	protected final void registerWrites(final boolean state) {
		if (((registeredOps & SelectionKey.OP_WRITE) != 0) != state) {
			registeredOps = SelectionKey.OP_READ | (state ? SelectionKey.OP_WRITE : 0);
			socketProcessor.interestSocket(this, registeredOps);
		}
	}
	
	public final void deferredClose() {
		awaitingClose = true;
	}

	public final void closeChannel() {
		registerWrites(false);
		try {
			socketChannel.close();
		} catch (final IOException e) {
			/* Let's hope it actually got closed */
			e.printStackTrace();
		}
	}

	public final void close() {
		if (!endOfStreamReached) {
			endOfStreamReached = true;
			messageWriter.clear();
			messageReader.clear();
			closeChannel();
			socketProcessor.closedSocket(this);
		}
	}
	
	public final SocketAddress getAddress() {
		return address;
	}
}
