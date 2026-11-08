#include <android/bitmap.h>
#include <jni.h>
#include <webp/encode.h>
#include <webp/demux.h>
#include <webp/mux.h>

#include <cstdio>
#include <memory>
#include <string>
#include <vector>
#include <cstring>

namespace {
struct Encoder {
  int width;
  int height;
  float quality;
  int method;
  WebPAnimEncoder* value = nullptr;
  int last_timestamp = -1;

  ~Encoder() {
    if (value) WebPAnimEncoderDelete(value);
  }
};

bool AddBitmap(JNIEnv* env, Encoder* encoder, jobject bitmap, int timestamp) {
  if (!encoder || !encoder->value || !bitmap || timestamp <= encoder->last_timestamp) return false;
  AndroidBitmapInfo info{};
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
      info.width != static_cast<uint32_t>(encoder->width) ||
      info.height != static_cast<uint32_t>(encoder->height) ||
      info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return false;

  void* pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
  WebPPicture picture;
  WebPPictureInit(&picture);
  picture.width = encoder->width;
  picture.height = encoder->height;
  picture.use_argb = 1;
  const bool imported = WebPPictureImportRGBA(&picture, static_cast<const uint8_t*>(pixels), info.stride) != 0;
  AndroidBitmap_unlockPixels(env, bitmap);
  if (!imported) { WebPPictureFree(&picture); return false; }

  WebPConfig config;
  if (!WebPConfigPreset(&config, WEBP_PRESET_DEFAULT, encoder->quality)) {
    WebPPictureFree(&picture);
    return false;
  }
  config.method = encoder->method;
  config.thread_level = 1;
  config.alpha_quality = 100;
  config.exact = 1;
  const bool ok = WebPAnimEncoderAdd(encoder->value, &picture, timestamp, &config) != 0;
  WebPPictureFree(&picture);
  if (ok) encoder->last_timestamp = timestamp;
  return ok;
}

bool Write(const std::string& path, const WebPData& data) {
  FILE* file = fopen(path.c_str(), "wb");
  if (!file) return false;
  const bool ok = fwrite(data.bytes, 1, data.size, file) == data.size;
  return fclose(file) == 0 && ok;
}

bool Read(const char* path, std::vector<uint8_t>* bytes) {
  FILE* file = fopen(path, "rb");
  if (!file) return false;
  if (fseek(file, 0, SEEK_END) != 0) { fclose(file); return false; }
  const long length = ftell(file);
  if (length <= 0 || length > 16 * 1024 * 1024 || fseek(file, 0, SEEK_SET) != 0) {
    fclose(file);
    return false;
  }
  bytes->resize(static_cast<size_t>(length));
  const bool ok = fread(bytes->data(), 1, bytes->size(), file) == bytes->size();
  fclose(file);
  return ok;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_create(
    JNIEnv*, jobject, jint width, jint height, jfloat quality, jint method) {
  if (width != 512 || height != 512 || quality < 0.f || quality > 100.f || method < 0 || method > 6) return 0;
  auto encoder = std::make_unique<Encoder>();
  encoder->width = width;
  encoder->height = height;
  encoder->quality = quality;
  encoder->method = method;
  WebPAnimEncoderOptions options;
  if (!WebPAnimEncoderOptionsInit(&options)) return 0;
  options.anim_params.loop_count = 0;
  // Candidate outputs are measured and the quality search is repeated as needed.
  // The muxer's exhaustive size minimization is redundant here and very expensive.
  options.minimize_size = 0;
  options.allow_mixed = 1;
  encoder->value = WebPAnimEncoderNew(width, height, &options);
  if (!encoder->value) return 0;
  return reinterpret_cast<jlong>(encoder.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_addFrame(
    JNIEnv* env, jobject, jlong handle, jobject bitmap, jint timestamp) {
  return AddBitmap(env, reinterpret_cast<Encoder*>(handle), bitmap, timestamp) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_finish(
    JNIEnv* env, jobject, jlong handle, jstring output_path, jint duration) {
  std::unique_ptr<Encoder> encoder(reinterpret_cast<Encoder*>(handle));
  if (!encoder || encoder->last_timestamp < 0 || duration <= encoder->last_timestamp) return JNI_FALSE;
  if (!encoder->value || !WebPAnimEncoderAdd(encoder->value, nullptr, duration, nullptr)) return JNI_FALSE;
  WebPData data;
  WebPDataInit(&data);
  const bool assembled = WebPAnimEncoderAssemble(encoder->value, &data) != 0;
  WebPAnimEncoderDelete(encoder->value);
  encoder->value = nullptr;
  if (!assembled) return JNI_FALSE;
  const char* chars = env->GetStringUTFChars(output_path, nullptr);
  const bool written = chars && Write(chars, data);
  if (chars) env->ReleaseStringUTFChars(output_path, chars);
  WebPDataClear(&data);
  return written ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_abort(
    JNIEnv*, jobject, jlong handle) {
  // Encoder owns the WebPAnimEncoder and deletes it from its destructor.
  // Do not delete encoder->value here as well: that double-frees libwebp state.
  std::unique_ptr<Encoder> encoder(reinterpret_cast<Encoder*>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_encodeStaticTwoFrames(
    JNIEnv* env, jobject, jobject bitmap, jstring output_path, jint duration) {
  AndroidBitmapInfo info{};
  if (!bitmap || duration < 8 || AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
      info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width != 512 || info.height != 512) return JNI_FALSE;
  void* pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
  uint8_t* frame_bytes = nullptr;
  const size_t frame_size = WebPEncodeRGBA(static_cast<const uint8_t*>(pixels), info.width, info.height,
                                           info.stride, 90.f, &frame_bytes);
  AndroidBitmap_unlockPixels(env, bitmap);
  if (!frame_size || !frame_bytes) return JNI_FALSE;
  WebPData image{frame_bytes, frame_size};
  WebPMux* mux = WebPMuxNew();
  if (!mux) {
    WebPFree(frame_bytes);
    return JNI_FALSE;
  }
  WebPMuxAnimParams anim{0, 0};
  const bool configured = WebPMuxSetCanvasSize(mux, info.width, info.height) == WEBP_MUX_OK &&
      WebPMuxSetAnimationParams(mux, &anim) == WEBP_MUX_OK;
  WebPMuxFrameInfo frame{};
  frame.bitstream = image;
  frame.duration = duration;
  frame.id = WEBP_CHUNK_ANMF;
  frame.blend_method = WEBP_MUX_BLEND;
  frame.dispose_method = WEBP_MUX_DISPOSE_NONE;
  const bool pushed = configured && WebPMuxPushFrame(mux, &frame, 1) == WEBP_MUX_OK &&
      WebPMuxPushFrame(mux, &frame, 1) == WEBP_MUX_OK;
  WebPData output;
  WebPDataInit(&output);
  const bool assembled = pushed && WebPMuxAssemble(mux, &output) == WEBP_MUX_OK;
  const char* chars = env->GetStringUTFChars(output_path, nullptr);
  const bool written = assembled && chars && Write(chars, output);
  if (chars) env->ReleaseStringUTFChars(output_path, chars);
  WebPDataClear(&output);
  WebPMuxDelete(mux);
  WebPFree(frame_bytes);
  return written ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_isValidAnimatedWebp(
    JNIEnv* env, jobject, jstring path) {
  const char* chars = env->GetStringUTFChars(path, nullptr);
  if (!chars) return JNI_FALSE;
  std::vector<uint8_t> bytes;
  const bool read_ok = Read(chars, &bytes);
  env->ReleaseStringUTFChars(path, chars);
  if (!read_ok || bytes.size() < 12 || memcmp(bytes.data(), "RIFF", 4) != 0 ||
      memcmp(bytes.data() + 8, "WEBP", 4) != 0) return JNI_FALSE;
  WebPData data{bytes.data(), bytes.size()};
  WebPDemuxer* demux = WebPDemux(&data);
  if (!demux) return JNI_FALSE;
  const uint32_t flags = WebPDemuxGetI(demux, WEBP_FF_FORMAT_FLAGS);
  const uint32_t width = WebPDemuxGetI(demux, WEBP_FF_CANVAS_WIDTH);
  const uint32_t height = WebPDemuxGetI(demux, WEBP_FF_CANVAS_HEIGHT);
  const uint32_t count = WebPDemuxGetI(demux, WEBP_FF_FRAME_COUNT);
  uint64_t total = 0;
  bool valid = (flags & ANIMATION_FLAG) != 0 && width == 512 && height == 512 && count > 1;
  WebPIterator frame;
  if (valid && WebPDemuxGetFrame(demux, 1, &frame)) {
    do {
      valid = valid && frame.duration >= 8;
      total += static_cast<uint64_t>(frame.duration);
    } while (valid && WebPDemuxNextFrame(&frame));
    WebPDemuxReleaseIterator(&frame);
  } else {
    valid = false;
  }
  valid = valid && total <= 10000 && count == WebPDemuxGetI(demux, WEBP_FF_FRAME_COUNT);
  WebPDemuxDelete(demux);
  return valid ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_wppsticker_ui_videoeditor_AnimatedWebpNative_decodeFirstFrame(
    JNIEnv* env, jobject, jstring path) {
  const char* chars = env->GetStringUTFChars(path, nullptr);
  if (!chars) return nullptr;
  std::vector<uint8_t> bytes;
  const bool read_ok = Read(chars, &bytes);
  env->ReleaseStringUTFChars(path, chars);
  if (!read_ok) return nullptr;
  WebPData data{bytes.data(), bytes.size()};
  WebPAnimDecoderOptions options;
  if (!WebPAnimDecoderOptionsInit(&options)) return nullptr;
  options.color_mode = MODE_RGBA;
  WebPAnimDecoder* decoder = WebPAnimDecoderNew(&data, &options);
  if (!decoder) return nullptr;
  uint8_t* rgba = nullptr;
  int timestamp = 0;
  const bool decoded = WebPAnimDecoderGetNext(decoder, &rgba, &timestamp) != 0;
  WebPAnimInfo info{};
  WebPAnimDecoderGetInfo(decoder, &info);
  jobject bitmap = nullptr;
  if (decoded && info.canvas_width > 0 && info.canvas_height > 0) {
    jclass bitmap_class = env->FindClass("android/graphics/Bitmap");
    jclass config_class = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb = env->GetStaticFieldID(config_class, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject config = env->GetStaticObjectField(config_class, argb);
    jmethodID create = env->GetStaticMethodID(bitmap_class, "createBitmap",
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    bitmap = env->CallStaticObjectMethod(bitmap_class, create, static_cast<jint>(info.canvas_width),
                                        static_cast<jint>(info.canvas_height), config);
    AndroidBitmapInfo bitmap_info{};
    void* pixels = nullptr;
    if (bitmap && AndroidBitmap_getInfo(env, bitmap, &bitmap_info) == ANDROID_BITMAP_RESULT_SUCCESS &&
        AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
      for (uint32_t y = 0; y < info.canvas_height; ++y) {
        memcpy(static_cast<uint8_t*>(pixels) + y * bitmap_info.stride,
               rgba + y * info.canvas_width * 4, info.canvas_width * 4);
      }
      AndroidBitmap_unlockPixels(env, bitmap);
    } else {
      bitmap = nullptr;
    }
  }
  WebPAnimDecoderDelete(decoder);
  return bitmap;
}
