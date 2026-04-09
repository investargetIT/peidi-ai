package com.cyanrocks.ai.utils.wechat;

import java.util.ArrayList;

class ByteGroup {
	ArrayList<Byte> byteContainer = new ArrayList<Byte>();

	/**
	 * Convert the group's contents to a new primitive byte array in insertion order.
	 *
	 * @return a new {@code byte[]} containing the bytes currently stored in the group; modifying the returned array does not affect the group
	 */
	public byte[] toBytes() {
		byte[] bytes = new byte[byteContainer.size()];
		for (int i = 0; i < byteContainer.size(); i++) {
			bytes[i] = byteContainer.get(i);
		}
		return bytes;
	}

	/**
	 * Appends all bytes from the provided array to the internal byte buffer.
	 *
	 * @param bytes the bytes to append
	 * @return this ByteGroup instance for method chaining
	 */
	public ByteGroup addBytes(byte[] bytes) {
		for (byte b : bytes) {
			byteContainer.add(b);
		}
		return this;
	}

	/**
	 * Get the number of bytes currently stored in this group.
	 *
	 * @return the number of bytes in the internal buffer
	 */
	public int size() {
		return byteContainer.size();
	}
}
