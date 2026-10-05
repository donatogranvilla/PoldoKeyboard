package org.florisboard.lib.localai.engine

/**
 * Identifies the underlying Speech-to-Text inference engine.
 */
enum class EngineType(val displayName: String) {
    WHISPER_CPP("whisper.cpp (GGML)"),
    SHERPA_ONNX("sherpa-onnx (ONNX)"),
    MOCK_TEST("Mock Test Engine")
}
