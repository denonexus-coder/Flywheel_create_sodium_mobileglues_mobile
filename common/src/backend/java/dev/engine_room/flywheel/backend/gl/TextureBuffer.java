package dev.engine_room.flywheel.backend.gl;

import org.lwjgl.opengl.GL32;

public class TextureBuffer extends GlObject {
	public static final int MAX_TEXELS = GL32.glGetInteger(GL32.GL_MAX_TEXTURE_BUFFER_SIZE);
	public static final int MAX_BYTES = MAX_TEXELS * 16; // 4 channels * 4 bytes
	private final int format;

	public TextureBuffer() {
		this(GL32.GL_RGBA32UI);
	}

	public TextureBuffer(int format) {
		handle(GL32.glGenTextures());
		this.format = format;
	}

	public void bind(int buffer) {
		attach(buffer);
	}

	/**
	 * Point this texture at {@code buffer}'s storage, binding the texture to the current
	 * texture buffer unit. Only needs to be done once per buffer: re-specifying the
	 * buffer's data store with {@code glBufferData} keeps the attachment alive.
	 */
	public void attach(int buffer) {
		GL32.glBindTexture(GL32.GL_TEXTURE_BUFFER, handle());
		GL32.glTexBuffer(GL32.GL_TEXTURE_BUFFER, format, buffer);
	}

	/**
	 * Bind this texture to the current texture buffer unit, leaving its storage alone.
	 * This is the cheap half of {@link #bind(int)} and all that is needed when the buffer
	 * this texture is attached to has not changed.
	 */
	public void bindOnly() {
		GL32.glBindTexture(GL32.GL_TEXTURE_BUFFER, handle());
	}

	@Override
	protected void deleteInternal(int handle) {
		GL32.glDeleteTextures(handle);
	}
}
