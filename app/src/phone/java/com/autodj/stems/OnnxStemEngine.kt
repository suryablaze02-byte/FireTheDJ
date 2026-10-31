package com.autodj.stems

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.autodj.dj.stems.DemucsDsp
import com.autodj.dj.stems.StemEngine
import com.autodj.dj.stems.StemSession
import java.io.File
import java.nio.FloatBuffer

/**
 * The phone app's stem separator: Demucs htdemucs on ONNX Runtime, on four CPU threads. Its
 * memory arena and planned buffers are off, which halves the peak (about 1.3 GB for a 3.9 s
 * chunk on a Moto G64) at the same speed (about real time). Found by name from [com.autodj.dj.stems.Stems].
 */
class OnnxStemEngine : StemEngine {
    override fun open(model: File): StemSession {
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        }
        val session = env.createSession(model.absolutePath, opts)
        val mixShape = longArrayOf(1, 2, DemucsDsp.CHUNK.toLong())
        val specShape = longArrayOf(1, 4, DemucsDsp.BINS.toLong(), DemucsDsp.CHUNK_FRAMES.toLong())
        return object : StemSession {
            override fun run(mix: FloatArray, spec: FloatArray): DemucsDsp.ChunkOutput {
                OnnxTensor.createTensor(env, FloatBuffer.wrap(mix), mixShape).use { m ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(spec), specShape).use { s ->
                        session.run(mapOf("mix" to m, "spec" to s)).use { out ->
                            val freq = (out.get("freq").get() as OnnxTensor).floatBuffer
                            val time = (out.get("time").get() as OnnxTensor).floatBuffer
                            return DemucsDsp.ChunkOutput(
                                FloatArray(freq.remaining()).also { freq.get(it) },
                                FloatArray(time.remaining()).also { time.get(it) },
                            )
                        }
                    }
                }
            }

            override fun close() {
                session.close()
                opts.close()
            }
        }
    }
}
