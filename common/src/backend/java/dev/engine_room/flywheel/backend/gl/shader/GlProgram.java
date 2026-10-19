package dev.engine_room.flywheel.backend.gl.shader;

import static org.lwjgl.opengl.GL20.glBindAttribLocation;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniformMatrix3fv;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL30.glBindFragDataLocation;
import static org.lwjgl.opengl.GL30.glUniform1ui;
import static org.lwjgl.opengl.GL30.glUniform2ui;
import static org.lwjgl.opengl.GL31.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

import java.nio.FloatBuffer;

import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.logging.LogUtils;

import dev.engine_room.flywheel.backend.gl.GlObject;
import dev.engine_room.flywheel.backend.gl.GlTextureUnit;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class GlProgram extends GlObject {
	private static final Logger LOGGER = LogUtils.getLogger();

	private final Object2IntMap<String> uniformLocationCache = new Object2IntOpenHashMap<>();

	/**
	 * Scratch storage shared by every matrix uniform upload.
	 *
	 * <p>Matrix uniforms are only ever uploaded from the render thread, and the driver copies the
	 * array out before the call returns, so one instance per size is enough for the whole JVM.
	 * Without them every draw call that carries a pose hands a fresh {@code new float[16]} to the
	 * garbage collector - a steady per-frame allocation aimed straight at the weakest CPU in the
	 * box.
	 */
	private static final float[] MATRIX4_ARRAY = new float[16];
	private static final float[] MATRIX3_ARRAY = new float[9];
	private static final FloatBuffer MATRIX4_BUFFER = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer MATRIX3_BUFFER = BufferUtils.createFloatBuffer(9);

	public GlProgram(int handle) {
		handle(handle);
	}

	public void bind() {
		ProgramManager.glUseProgram(handle());
	}

	public static void unbind() {
		ProgramManager.glUseProgram(0);
	}

	public void setFloat(String glslName, float value) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform1f(uniform, value);
	}

	public void setVec2(String glslName, float x, float y) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform2f(uniform, x, y);
	}

	public void setVec3(String glslName, float x, float y, float z) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform3f(uniform, x, y, z);
	}

	public void setVec4(String glslName, float x, float y, float z, float w) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform4f(uniform, x, y, z, w);
	}

	public void setMat4(String glslName, Matrix4fc matrix) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		matrix.get(MATRIX4_ARRAY);
		MATRIX4_BUFFER.clear();
		MATRIX4_BUFFER.put(MATRIX4_ARRAY);
		MATRIX4_BUFFER.flip();
		glUniformMatrix4fv(uniform, false, MATRIX4_BUFFER);
	}

	public void setMat3(String glslName, Matrix3fc matrix) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		matrix.get(MATRIX3_ARRAY);
		MATRIX3_BUFFER.clear();
		MATRIX3_BUFFER.put(MATRIX3_ARRAY);
		MATRIX3_BUFFER.flip();
		glUniformMatrix3fv(uniform, false, MATRIX3_BUFFER);
	}

	public void setBool(String glslName, boolean bool) {
		setInt(glslName, bool ? 1 : 0);
	}

	public void setUInt(String glslName, int value) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform1ui(uniform, value);
	}

	public void setUVec2(String name, int x, int y) {
		int uniform = getUniformLocation(name);

		if (uniform < 0) {
			return;
		}

		glUniform2ui(uniform, x, y);
	}

	public void setInt(String glslName, int value) {
		int uniform = getUniformLocation(glslName);

		if (uniform < 0) {
			return;
		}

		glUniform1i(uniform, value);
	}

	/**
	 * Retrieves the index of the uniform with the given name.
	 *
	 * @param uniform The name of the uniform to find the index of
	 * @return The uniform's index
	 */
	public int getUniformLocation(String uniform) {
		return uniformLocationCache.computeIfAbsent(uniform, s -> {
			int index = glGetUniformLocation(this.handle(), uniform);

			if (index < 0) {
				LOGGER.debug("No active uniform '{}' exists. Could be unused.", uniform);
			}
			return index;
		});
	}

	/**
	 * Binds a sampler uniform to the given texture unit.
	 *
	 * @param name    The name of the sampler uniform.
	 * @param binding The index of the texture unit.
	 */
	public void setSamplerBinding(String name, GlTextureUnit binding) {
		setSamplerBinding(name, binding.number);
	}

	public void setSamplerBinding(String name, int binding) {
		int samplerUniform = getUniformLocation(name);

		if (samplerUniform >= 0) {
			glUniform1i(samplerUniform, binding);
		}
	}

	public void setUniformBlockBinding(String name, int binding) {
		int index = glGetUniformBlockIndex(handle(), name);

		if (index == GL_INVALID_INDEX) {
			LOGGER.debug("No active uniform block '{}' exists. Could be unused.", name);
			return;
		}

		glUniformBlockBinding(handle(), index, binding);
	}

	public void bindAttribLocation(String attribute, int binding) {
		glBindAttribLocation(handle(), binding, attribute);
	}

	/**
	 * Binds a fragment shader output variable to a specific color attachment (draw buffer) slot.
	 *
	 * <p>This must happen before the program is linked. Binding a name that isn't an active output
	 * in the linked program is a no-op, so it's safe to bind every possible output unconditionally.
	 *
	 * <p>Without this, the GL implementation is free to assign output locations in any order it
	 * likes when a fragment shader declares multiple outputs without explicit {@code layout(location)}
	 * qualifiers. Most desktop drivers happen to assign them in declaration order, but Apple's legacy
	 * OpenGL driver does not, which scrambles the OIT coefficient targets and makes the transparent
	 * geometry vanish. Binding the locations explicitly is correct everywhere.
	 */
	public void bindFragDataLocation(String name, int binding) {
		glBindFragDataLocation(handle(), binding, name);
	}

	@Override
	protected void deleteInternal(int handle) {
		glDeleteProgram(handle);
	}
}
