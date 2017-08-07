package com.jenkov.nioserver;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.jenkov.nioserver.memory.MappedMemory;

/**
 * Created by jjenkov on 21-10-2015.
 * Modified by nagalun on 07-08-2017.
 */
public class MessageWriter {
	
	public enum Status {
		OK, FULL, EMPTY, ERROR
	}

	private final List<MappedMemory> writeQueue = new ArrayList<>();
	private final Socket socket;
	private MappedMemory messageInProgress = null;
	private ByteBuffer bufferInProgress = null; /* This may be risky (when resizing the MemoryManager), use weak reference? */
	private int bufferBytesWritten = 0;

	public MessageWriter(final Socket socket) {
		this.socket = socket;
	}

	public Status enqueue(final MappedMemory message) {
		message.ref();
		/* Tell the server we'd like to write to this socket */
		if (this.messageInProgress == null) {
			this.messageInProgress = message;
			this.bufferInProgress = message.getBuffer();
			/* Try to pass the message on to the TCP buffer, TODO: check if full (avoids block?) */
			return write();
		} else {
			this.writeQueue.add(message);
		}
		return Status.OK;
	}

	/* Returns true if there aren't any more messages to be written */
	public Status write() {
		this.bufferInProgress.position(this.bufferBytesWritten);
		int bytesWritten = socket.write(this.bufferInProgress);
		
		if (bytesWritten < 0) {
			/* IO error */
			return Status.ERROR;
		}
		
		this.bufferBytesWritten += bytesWritten;

		if (this.bufferInProgress.remaining() == 0) {
			this.bufferBytesWritten = 0;
			this.messageInProgress.unref();
			if (this.writeQueue.size() > 0) {
				this.messageInProgress = this.writeQueue.remove(0);
				this.bufferInProgress = this.messageInProgress.getBuffer();
			} else {
				this.messageInProgress = null;
				this.bufferInProgress = null;
				return Status.EMPTY;
			}
		} else if (bytesWritten == 0) {
			/* TCP buffer is full? */
			return Status.FULL;
		}
		return Status.OK;
	}
	
	/* Unreferences (possibly frees) the memory used by the MappedMemory objects from the MemoryManager */
	public void clear() {
		for (final MappedMemory mm : writeQueue) {
			mm.unref();
		}
		writeQueue.clear();
		if (this.messageInProgress != null) {
			this.messageInProgress.unref();
			this.messageInProgress = null;
			this.bufferInProgress = null;
		}
	}

	public boolean isEmpty() {
		return this.messageInProgress == null;
	}

}
