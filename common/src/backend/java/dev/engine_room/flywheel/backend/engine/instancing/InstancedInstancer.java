package dev.engine_room.flywheel.backend.engine.instancing;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceWriter;
import dev.engine_room.flywheel.backend.MobileOptions;
import dev.engine_room.flywheel.backend.engine.BaseInstancer;
import dev.engine_room.flywheel.backend.engine.InstancerKey;
import dev.engine_room.flywheel.backend.gl.TextureBuffer;
import dev.engine_room.flywheel.backend.gl.buffer.GlBuffer;
import dev.engine_room.flywheel.backend.gl.buffer.GlBufferUsage;
import dev.engine_room.flywheel.lib.math.MoreMath;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;

public class InstancedInstancer<I extends Instance> extends BaseInstancer<I> {
	private final int instanceStride;

	private final InstanceWriter<I> writer;
	@Nullable
	private GlBuffer vbo;
	/**
	 * This instancer's own view of {@link #vbo}. Kept per instancer so the texture only has
	 * to be re-attached to the buffer, instead of being re-attached for every single draw
	 * call like a single shared texture would be.
	 */
	@Nullable
	private TextureBuffer texture;
	private boolean textureNeedsAttach = true;
	/**
	 * Persistent staging block for orphaned uploads, so the render loop does not allocate.
	 */
	@Nullable
	private MemoryBlock uploadScratch;

	private final List<InstancedDraw> draws = new ArrayList<>();

	public InstancedInstancer(InstancerKey<I> key, Recreate<I> recreate) {
		super(key, recreate);
		var layout = type.layout();
		// Align to one texel in the texture buffer
		instanceStride = MoreMath.align16(layout.byteSize());
		writer = type.writer();
	}

	public List<InstancedDraw> draws() {
		return draws;
	}

	public void init() {
		if (vbo != null) {
			return;
		}

		vbo = new GlBuffer(GlBufferUsage.DYNAMIC_DRAW);
		texture = new TextureBuffer();
		textureNeedsAttach = true;
	}

	public void updateBuffer() {
		if (changed.isEmpty() || vbo == null) {
			return;
		}

		int byteSize = instanceStride * instances.size();
		if (needsToGrow(byteSize)) {
			var temp = MemoryBlock.malloc(increaseSize(byteSize));

			writeAll(temp.ptr());

			vbo.upload(temp);

			temp.free();
		} else if (byteSize > 0 && MobileOptions.mobileDriver && MobileOptions.orphanInstanceUpload) {
			// Re-specify the store instead of patching it with glBufferSubData.
			//
			// A sub-data update into a region the GPU may still be reading (the same VBO was
			// just used by an earlier draw this frame) has to be ordered against that read.
			// Drivers on tile-based mobile GPUs resolve that by draining the queue, so the
			// CPU stalls for as long as the GPU has work left, no matter how small the span
			// is. glBufferData with the whole array hands over a fresh allocation instead and
			// lets the old one retire in the background.
			//
			// Measured on a PowerVR GE8320 at 2500 instances: ~0.6 ms per frame orphaned,
			// against ~2.0-3.0 ms for a partial glBufferSubData update. The rewrite of the
			// whole instance array is a straight memory copy of a few tens of kB.
			long ptr = scratch(byteSize);

			writeAll(ptr);

			vbo.upload(ptr, byteSize);
		} else {
			writeChanged();
		}

		changed.clear();
	}

	/**
	 * Get a staging pointer of at least {@code needed} bytes, reusing the same block between
	 * frames. Only grows, so this stays allocation-free once the instancer has settled.
	 */
	private long scratch(long needed) {
		MemoryBlock block = uploadScratch;
		if (block == null) {
			block = MemoryBlock.malloc(needed);
			uploadScratch = block;
		} else if (block.size() < needed) {
			block = block.realloc(needed);
			uploadScratch = block;
		}
		return block.ptr();
	}

	private void writeChanged() {
		changed.forEachSetSpan((startInclusive, endInclusive) -> {
			// Generally we're good about ensuring we don't have changed bits set out of bounds, but check just in case
			if (startInclusive >= instances.size()) {
				return;
			}
			int actualEnd = Math.min(endInclusive, instances.size() - 1);
			var temp = MemoryBlock.malloc((long) instanceStride * (actualEnd - startInclusive + 1));
			long ptr = temp.ptr();
			for (int i = startInclusive; i <= actualEnd; i++) {
				writer.write(ptr, instances.get(i));
				ptr += instanceStride;
			}

			vbo.uploadSpan((long) startInclusive * instanceStride, temp);

			temp.free();
		});
	}

	private void writeAll(long ptr) {
		for (I instance : instances) {
			writer.write(ptr, instance);
			ptr += instanceStride;
		}
	}

	private long increaseSize(long capacity) {
		return Math.max(capacity + (long) instanceStride * 16, (long) (capacity * 1.6));
	}

	public boolean needsToGrow(long capacity) {
		if (capacity < 0) {
			throw new IllegalArgumentException("Size " + capacity + " < 0");
		}

		if (capacity == 0) {
			return false;
		}

        return capacity > vbo.size();
    }

	public void parallelUpdate() {
		if (deleted.isEmpty()) {
			return;
		}

		// Figure out which elements are to be removed.
		final int oldSize = this.instances.size();
		int removeCount = deleted.cardinality();

		if (oldSize == removeCount) {
			clear();
			return;
		}

		final int newSize = oldSize - removeCount;

		// Start from the first deleted index.
		int writePos = deleted.nextSetBit(0);

		if (writePos < newSize) {
			// Since we'll be shifting everything into this space we can consider it all changed.
			changed.set(writePos, newSize);
		}

		// We definitely shouldn't consider the deleted instances as changed though,
		// else we might try some out of bounds accesses later.
		changed.clear(newSize, oldSize);

		// Punch out the deleted instances, shifting over surviving instances to fill their place.
		for (int scanPos = writePos; (scanPos < oldSize) && (writePos < newSize); scanPos++, writePos++) {
			// Find next non-deleted element.
			scanPos = deleted.nextClearBit(scanPos);

			if (scanPos != writePos) {
				// Grab the old instance/handle from scanPos...
				var handle = handles.get(scanPos);
				I instance = instances.get(scanPos);

				// ... and move it to writePos.
				handles.set(writePos, handle);
				instances.set(writePos, instance);

				// Make sure the handle knows it's been moved
				handle.index = writePos;
			}
		}

		deleted.clear();
		instances.subList(newSize, oldSize)
				.clear();
		handles.subList(newSize, oldSize)
				.clear();
	}

	public void delete() {
		if (vbo == null) {
			return;
		}
		vbo.delete();
		vbo = null;

		if (texture != null) {
			texture.delete();
			texture = null;
		}

		if (uploadScratch != null) {
			uploadScratch.free();
			uploadScratch = null;
		}

		for (InstancedDraw instancedDraw : draws) {
			instancedDraw.delete();
		}
	}

	public void addDrawCall(InstancedDraw instancedDraw) {
		draws.add(instancedDraw);
	}

	/**
	 * Point the instance buffer sampler's texture unit at this instancer's data. The caller
	 * must have made that unit active first.
	 */
	public void bind() {
		if (vbo == null || texture == null) {
			return;
		}

		if (textureNeedsAttach) {
			// glTexBuffer has to happen while the unit the sampler reads from is active, and it
			// only has to happen once. Every later draw only needs the texture rebind, since
			// re-specifying the buffer's data store does not break the attachment.
			texture.attach(vbo.handle());
			textureNeedsAttach = false;
		} else {
			texture.bindOnly();
		}
	}
}
