package com.nexustools.genericui.plugin

/**
 * Self-contained C11 source for a high-priority LibVLC 3.0.x `audio decoder` plugin
 * (`libgenericui_opus_fix_plugin`) that works around the upstream VLC 3.0.21–3.0.24
 * `modules/codec/opus.c` heap buffer over-read when Matroska/WebM blocks carry rounded
 * durations (`p_block->i_length` / `p_block->i_nb_samples`) exceeding the decoded Opus
 * packet sample count (`spp`), without replacing VLC's native demuxers.
 */
internal object GenericUiVlcOpusFixPluginSource {
    const val PLUGIN_STEM: String = "libgenericui_opus_fix_plugin"

    val C_SOURCE: String = """
        #if !defined(_WIN32) && !defined(_GNU_SOURCE)
        #define _GNU_SOURCE
        #endif

        #include <stdarg.h>
        #include <stddef.h>
        #include <stdint.h>
        #include <stdlib.h>
        #include <string.h>

        #if defined(_WIN32)
        #include <windows.h>
        #define VLC_EXPORT __declspec(dllexport)
        #define VLC_CALL __cdecl
        #define DIR_SEP_CHAR '\\'
        #define PATH_SEP_STR ";"
        #define PLUGIN_EXT ".dll"
        #else
        #include <dlfcn.h>
        #include <pthread.h>
        #if defined(__GNUC__)
        #define VLC_EXPORT __attribute__((visibility("default")))
        #else
        #define VLC_EXPORT
        #endif
        #define VLC_CALL
        #define DIR_SEP_CHAR '/'
        #define PATH_SEP_STR ":"
        #if defined(__APPLE__)
        #define PLUGIN_EXT ".dylib"
        #else
        #define PLUGIN_EXT ".so"
        #endif
        #endif

        /* VLC 3.0 module property codes from include/vlc_plugin.h (frozen ABI) */
        #define VLC_MODULE_CREATE      0
        #define VLC_CONFIG_CREATE      1
        #define VLC_MODULE_SHORTCUT    0x101
        #define VLC_MODULE_CAPABILITY  0x102
        #define VLC_MODULE_SCORE       0x103
        #define VLC_MODULE_CB_OPEN     0x104
        #define VLC_MODULE_CB_CLOSE    0x105
        #define VLC_MODULE_NO_UNLOAD   0x106
        #define VLC_MODULE_NAME        0x107
        #define VLC_MODULE_SHORTNAME   0x108
        #define VLC_MODULE_DESCRIPTION 0x109

        #define VLC_SUCCESS   0
        #define VLC_EGENERIC -1

        /* 'O','p','u','s' fourcc in little-endian memory order */
        #define VLC_CODEC_OPUS ((uint32_t)('O') | ((uint32_t)('p') << 8) | ((uint32_t)('u') << 16) | ((uint32_t)('s') << 24))

        typedef int (*vlc_set_cb)(void *, void *, int, ...);
        typedef int (*vlc_open_fn)(void *);
        typedef void (*vlc_close_fn)(void *);

        typedef struct genericui_vlc_block_t genericui_vlc_block_t;
        struct genericui_vlc_block_t {
            genericui_vlc_block_t *p_next;
            uint8_t *p_buffer;
            size_t i_buffer;
            uint8_t *p_start;
            size_t i_size;
            uint32_t i_flags;
            unsigned i_nb_samples;
            int64_t i_pts;
            int64_t i_dts;
            int64_t i_length;
            void (*pf_release)(genericui_vlc_block_t *);
        };

        typedef struct {
            const char *object_type;
            char *header;
            int flags;
            uint8_t force;
            void *libvlc;
            void *parent;
        } genericui_vlc_common_t;

        typedef struct {
            uint32_t i_format;
            unsigned int i_rate;
            uint16_t i_physical_channels;
            uint16_t i_chan_mode;
            int channel_type;
            unsigned int i_bytes_per_frame;
            unsigned int i_frame_length;
            unsigned i_bitspersample;
            unsigned i_blockalign;
            uint8_t i_channels;
        } genericui_vlc_audio_format_t;

        typedef struct {
            uint8_t pb_peak[2];
            float pf_peak[2];
            uint8_t pb_gain[2];
            float pf_gain[2];
        } genericui_vlc_audio_replay_gain_t;

        typedef struct {
            uint32_t i_chroma;
            unsigned int i_width;
            unsigned int i_height;
            unsigned int i_x_offset;
            unsigned int i_y_offset;
            unsigned int i_visible_width;
            unsigned int i_visible_height;
            unsigned int i_bits_per_pixel;
            unsigned int i_sar_num;
            unsigned int i_sar_den;
            unsigned int i_frame_rate;
            unsigned int i_frame_rate_base;
            uint32_t i_rmask;
            uint32_t i_gmask;
            uint32_t i_bmask;
            int i_rrshift;
            int i_lrshift;
            int i_rgshift;
            int i_lgshift;
            int i_rbshift;
            int i_lbshift;
            void *p_palette;
            int orientation;
            int primaries;
            int transfer;
            int space;
            uint8_t b_color_range_full;
            int chroma_location;
            int multiview_mode;
            int projection_mode;
            float pose[4];
            struct {
                uint16_t primaries[6];
                uint16_t white_point[2];
                uint32_t max_luminance;
                uint32_t min_luminance;
            } mastering;
            struct {
                uint16_t MaxCLL;
                uint16_t MaxFALL;
            } lighting;
            uint32_t i_cubemap_padding;
        } genericui_vlc_video_format_t;

        typedef struct {
            char *psz_encoding;
            int i_x_origin;
            int i_y_origin;
            struct {
                uint32_t palette[17];
                int i_original_frame_width;
                int i_original_frame_height;
            } spu;
            struct {
                int i_id;
            } dvb;
            struct {
                int i_magazine;
                int i_page;
            } teletext;
            struct {
                uint8_t i_channel;
                int i_reorder_depth;
            } cc;
            void *p_style;
        } genericui_vlc_subs_format_t;

        typedef struct {
            int i_cat;
            uint32_t i_codec;
            uint32_t i_original_fourcc;
            int i_id;
            int i_group;
            int i_priority;
            char *psz_language;
            char *psz_description;
            unsigned i_extra_languages;
            void *p_extra_languages;
            union {
                struct {
                    genericui_vlc_audio_format_t audio;
                    genericui_vlc_audio_replay_gain_t audio_replay_gain;
                };
                genericui_vlc_video_format_t video;
                genericui_vlc_subs_format_t subs;
            };
            unsigned int i_bitrate;
            int i_profile;
            int i_level;
            uint8_t b_packetized;
            int i_extra;
            void *p_extra;
        } genericui_vlc_es_format_t;

        typedef struct genericui_vlc_decoder_t genericui_vlc_decoder_t;
        typedef int (*vlc_decode_fn)(genericui_vlc_decoder_t *, genericui_vlc_block_t *);

        struct genericui_vlc_decoder_t {
            genericui_vlc_common_t obj;
            void *p_module;
            void *p_sys;
            genericui_vlc_es_format_t fmt_in;
            genericui_vlc_es_format_t fmt_out;
            uint8_t b_frame_drop_allowed;
            vlc_decode_fn pf_decode;
            genericui_vlc_block_t *(*pf_packetize)(genericui_vlc_decoder_t *, genericui_vlc_block_t **);
            void (*pf_flush)(genericui_vlc_decoder_t *);
        };

        #if INTPTR_MAX == INT64_MAX
        _Static_assert(offsetof(genericui_vlc_decoder_t, p_sys) == 0x30, "p_sys offset");
        _Static_assert(offsetof(genericui_vlc_decoder_t, fmt_in) + offsetof(genericui_vlc_es_format_t, i_codec) == 0x3c, "fmt_in.i_codec offset");
        _Static_assert(offsetof(genericui_vlc_decoder_t, fmt_out) + offsetof(genericui_vlc_es_format_t, i_codec) == 0x144, "fmt_out.i_codec offset");
        _Static_assert(offsetof(genericui_vlc_decoder_t, pf_decode) == 0x250, "pf_decode offset");
        _Static_assert(offsetof(genericui_vlc_block_t, p_buffer) == 0x08, "p_buffer offset");
        _Static_assert(offsetof(genericui_vlc_block_t, i_buffer) == 0x10, "i_buffer offset");
        _Static_assert(offsetof(genericui_vlc_block_t, i_nb_samples) == 0x2c, "i_nb_samples offset");
        _Static_assert(offsetof(genericui_vlc_block_t, i_pts) == 0x30, "i_pts offset");
        _Static_assert(offsetof(genericui_vlc_block_t, i_length) == 0x40, "i_length offset");
        #endif

        #if defined(_WIN32)
        static SRWLOCK g_lock = SRWLOCK_INIT;
        static void lock_state(void) { AcquireSRWLockExclusive(&g_lock); }
        static void unlock_state(void) { ReleaseSRWLockExclusive(&g_lock); }
        #else
        static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
        static void lock_state(void) { pthread_mutex_lock(&g_lock); }
        static void unlock_state(void) { pthread_mutex_unlock(&g_lock); }
        #endif

        #define MAX_DECODER_SLOTS 64

        typedef struct {
            genericui_vlc_decoder_t *dec;
            vlc_decode_fn orig_decode;
        } decoder_slot_t;

        static int g_probe_attempted = 0;
        static void *g_opus_lib_handle = NULL;
        static vlc_open_fn g_real_opus_open = NULL;
        static vlc_close_fn g_real_opus_close = NULL;
        static vlc_decode_fn g_default_orig_decode = NULL;
        static decoder_slot_t g_slots[MAX_DECODER_SLOTS];

        typedef struct {
            int mod_index;
            int in_audio_decoder;
            vlc_open_fn open_cb;
            vlc_close_fn close_cb;
        } opus_probe_state_t;

        static int probe_vlc_set_cb(void *opaque, void *target, int propid, ...) {
            (void)target;
            opus_probe_state_t *st = (opus_probe_state_t *)opaque;
            va_list ap;
            va_start(ap, propid);
            switch (propid) {
                case VLC_MODULE_CREATE: {
                    void **pp = va_arg(ap, void **);
                    if (pp != NULL) {
                        *pp = (void *)(uintptr_t)(++st->mod_index);
                    }
                    st->in_audio_decoder = 0;
                    break;
                }
                case VLC_CONFIG_CREATE: {
                    (void)va_arg(ap, int);
                    void **pp = va_arg(ap, void **);
                    if (pp != NULL) {
                        *pp = (void *)(uintptr_t)0x1000;
                    }
                    break;
                }
                case VLC_MODULE_CAPABILITY: {
                    const char *cap = va_arg(ap, const char *);
                    st->in_audio_decoder = (cap != NULL && strcmp(cap, "audio decoder") == 0);
                    break;
                }
                case VLC_MODULE_CB_OPEN: {
                    (void)va_arg(ap, const char *);
                    void *fn = va_arg(ap, void *);
                    if (st->in_audio_decoder && st->open_cb == NULL) {
                        st->open_cb = (vlc_open_fn)fn;
                    }
                    break;
                }
                case VLC_MODULE_CB_CLOSE: {
                    (void)va_arg(ap, const char *);
                    void *fn = va_arg(ap, void *);
                    if (st->in_audio_decoder && st->close_cb == NULL) {
                        st->close_cb = (vlc_close_fn)fn;
                    }
                    break;
                }
                default:
                    break;
            }
            va_end(ap);
            return VLC_SUCCESS;
        }

        typedef int (*vlc_entry_fn)(vlc_set_cb, void *);

        static int try_load_opus_plugin_file(const char *path) {
            if (path == NULL || path[0] == '\0') {
                return 0;
            }
        #if defined(_WIN32)
            HMODULE hm = LoadLibraryA(path);
            if (hm == NULL) {
                return 0;
            }
            vlc_entry_fn entry = (vlc_entry_fn)(void *)GetProcAddress(hm, "vlc_entry__3_0_0f");
            if (entry == NULL) {
                FreeLibrary(hm);
                return 0;
            }
        #else
            void *hm = dlopen(path, RTLD_LAZY | RTLD_LOCAL);
            if (hm == NULL) {
                return 0;
            }
            vlc_entry_fn entry = (vlc_entry_fn)dlsym(hm, "vlc_entry__3_0_0f");
            if (entry == NULL) {
                dlclose(hm);
                return 0;
            }
        #endif
            opus_probe_state_t st;
            memset(&st, 0, sizeof(st));
            if (entry(probe_vlc_set_cb, &st) == VLC_SUCCESS && st.open_cb != NULL) {
                g_opus_lib_handle = (void *)hm;
                g_real_opus_open = st.open_cb;
                g_real_opus_close = st.close_cb;
                return 1;
            }
        #if defined(_WIN32)
            FreeLibrary(hm);
        #else
            dlclose(hm);
        #endif
            return 0;
        }

        static int try_load_in_base_dir(const char *base_dir) {
            if (base_dir == NULL || base_dir[0] == '\0') {
                return 0;
            }
            size_t len = strlen(base_dir);
            if (len > 900) {
                return 0;
            }
            char path_buf[1024];
            const char *suffixes[4] = {
                "libopus_plugin" PLUGIN_EXT,
                "codec/libopus_plugin" PLUGIN_EXT,
                "../plugins/libopus_plugin" PLUGIN_EXT,
                "../plugins/codec/libopus_plugin" PLUGIN_EXT
            };
        #if defined(_WIN32)
            const char *win_suffixes[4] = {
                "libopus_plugin.dll",
                "codec\\libopus_plugin.dll",
                "..\\plugins\\libopus_plugin.dll",
                "..\\plugins\\codec\\libopus_plugin.dll"
            };
            (void)suffixes;
            for (int i = 0; i < 4; i++) {
                memcpy(path_buf, base_dir, len);
                path_buf[len] = '\\';
                strcpy(path_buf + len + 1, win_suffixes[i]);
                if (try_load_opus_plugin_file(path_buf)) {
                    return 1;
                }
            }
        #else
            for (int i = 0; i < 4; i++) {
                memcpy(path_buf, base_dir, len);
                path_buf[len] = '/';
                strcpy(path_buf + len + 1, suffixes[i]);
                if (try_load_opus_plugin_file(path_buf)) {
                    return 1;
                }
            }
        #endif
            return 0;
        }

        static int strip_last_component(char *buf) {
            size_t len = strlen(buf);
            while (len > 0 && (buf[len - 1] == '/' || buf[len - 1] == '\\')) {
                buf[--len] = '\0';
            }
            char *sep_fwd = strrchr(buf, '/');
            char *sep_back = strrchr(buf, '\\');
            char *sep = sep_fwd;
            if (sep_back != NULL && (sep == NULL || sep_back > sep)) {
                sep = sep_back;
            }
            if (sep == NULL) {
                return 0;
            }
            if (sep == buf) {
                buf[1] = '\0';
            } else {
                *sep = '\0';
            }
            return 1;
        }

        static void ensure_real_opus_loaded_locked(void) {
            if (g_probe_attempted) {
                return;
            }
            g_probe_attempted = 1;

        #if defined(_WIN32)
            {
                HMODULE self_mod = NULL;
                if (GetModuleHandleExA(
                        GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                        (LPCSTR)(void *)&ensure_real_opus_loaded_locked,
                        &self_mod) &&
                    self_mod != NULL) {
                    char self_dir[1024];
                    DWORD n = GetModuleFileNameA(self_mod, self_dir, (DWORD)sizeof(self_dir));
                    if (n > 0 && n < sizeof(self_dir) && strip_last_component(self_dir)) {
                        if (try_load_in_base_dir(self_dir)) {
                            return;
                        }
                    }
                }
            }
            {
                HMODULE core_mod = GetModuleHandleA("libvlccore.dll");
                if (core_mod != NULL) {
                    char core_dir[1024];
                    DWORD n = GetModuleFileNameA(core_mod, core_dir, (DWORD)sizeof(core_dir));
                    if (n > 0 && n < sizeof(core_dir) && strip_last_component(core_dir)) {
                        size_t dlen = strlen(core_dir);
                        if (dlen < 900) {
                            char plug_dir[1024];
                            memcpy(plug_dir, core_dir, dlen);
                            strcpy(plug_dir + dlen, "\\plugins");
                            if (try_load_in_base_dir(plug_dir)) {
                                return;
                            }
                        }
                    }
                }
            }
        #else
            {
                Dl_info info;
                memset(&info, 0, sizeof(info));
                if (dladdr((const void *)&ensure_real_opus_loaded_locked, &info) != 0 && info.dli_fname != NULL) {
                    size_t plen = strlen(info.dli_fname);
                    if (plen < 1024) {
                        char self_dir[1024];
                        memcpy(self_dir, info.dli_fname, plen + 1);
                        if (strip_last_component(self_dir) && try_load_in_base_dir(self_dir)) {
                            return;
                        }
                    }
                }
            }
            {
                void *core_sym = dlsym(RTLD_DEFAULT, "vlc_object_get_name");
                if (core_sym == NULL) {
                    core_sym = dlsym(RTLD_DEFAULT, "libvlc_get_version");
                }
                if (core_sym != NULL) {
                    Dl_info cinfo;
                    memset(&cinfo, 0, sizeof(cinfo));
                    if (dladdr(core_sym, &cinfo) != 0 && cinfo.dli_fname != NULL) {
                        size_t clen = strlen(cinfo.dli_fname);
                        if (clen < 960) {
                            char cdir[1024];
                            memcpy(cdir, cinfo.dli_fname, clen + 1);
                            if (strip_last_component(cdir)) {
                                if (try_load_in_base_dir(cdir)) {
                                    return;
                                }
                                size_t dlen = strlen(cdir);
                                if (dlen < 900) {
                                    char pdir[1024];
                                    memcpy(pdir, cdir, dlen);
                                    strcpy(pdir + dlen, "/plugins");
                                    if (try_load_in_base_dir(pdir)) {
                                        return;
                                    }
                                    strcpy(pdir + dlen, "/vlc/plugins");
                                    if (try_load_in_base_dir(pdir)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        #endif

            {
                const char *plugin_env = getenv("VLC_PLUGIN_PATH");
                if (plugin_env != NULL && plugin_env[0] != '\0') {
                    size_t elen = strlen(plugin_env);
                    char *copy = (char *)malloc(elen + 1);
                    if (copy != NULL) {
                        memcpy(copy, plugin_env, elen + 1);
                        char *cursor = copy;
                        char *tok = NULL;
                        while (cursor != NULL) {
                            tok = cursor;
        #if defined(_WIN32)
                            char *sep = strchr(cursor, ';');
        #else
                            char *sep = strchr(cursor, ':');
        #endif
                            if (sep != NULL) {
                                *sep = '\0';
                                cursor = sep + 1;
                            } else {
                                cursor = NULL;
                            }
                            if (tok[0] != '\0' && try_load_in_base_dir(tok)) {
                                free(copy);
                                return;
                            }
                        }
                        free(copy);
                    }
                }
            }

        #if defined(_WIN32)
            {
                const char *fallbacks[2] = {
                    "C:\\Program Files\\VideoLAN\\VLC\\plugins\\codec\\libopus_plugin.dll",
                    "C:\\Program Files (x86)\\VideoLAN\\VLC\\plugins\\codec\\libopus_plugin.dll"
                };
                for (int i = 0; i < 2; i++) {
                    if (try_load_opus_plugin_file(fallbacks[i])) {
                        return;
                    }
                }
            }
        #elif defined(__APPLE__)
            {
                const char *fallbacks[3] = {
                    "/Applications/VLC.app/Contents/MacOS/plugins/libopus_plugin.dylib",
                    "/opt/homebrew/lib/vlc/plugins/codec/libopus_plugin.dylib",
                    "/usr/local/lib/vlc/plugins/codec/libopus_plugin.dylib"
                };
                for (int i = 0; i < 3; i++) {
                    if (try_load_opus_plugin_file(fallbacks[i])) {
                        return;
                    }
                }
            }
        #else
            {
                const char *fallbacks[4] = {
                    "/usr/lib/x86_64-linux-gnu/vlc/plugins/codec/libopus_plugin.so",
                    "/usr/lib/aarch64-linux-gnu/vlc/plugins/codec/libopus_plugin.so",
                    "/usr/lib64/vlc/plugins/codec/libopus_plugin.so",
                    "/usr/lib/vlc/plugins/codec/libopus_plugin.so"
                };
                for (int i = 0; i < 4; i++) {
                    if (try_load_opus_plugin_file(fallbacks[i])) {
                        return;
                    }
                }
            }
        #endif
        }

        /* RFC 6716 Opus TOC parser returning sample count at 48 kHz [120..5760], or -1 on invalid input. */
        static int genericui_opus_packet_samples_48k(const uint8_t *data, size_t len) {
            if (data == NULL || len < 1) {
                return -1;
            }
            uint8_t toc = data[0];
            int frame_samples;
            if (toc & 0x80) {
                frame_samples = 120 << ((toc >> 3) & 0x03);
            } else if ((toc & 0x60) == 0x60) {
                frame_samples = (toc & 0x08) ? 960 : 480;
            } else {
                int sz = (toc >> 3) & 0x03;
                frame_samples = (sz == 3) ? 2880 : (480 << sz);
            }

            int nb_frames;
            switch (toc & 0x03) {
                case 0:
                    nb_frames = 1;
                    break;
                case 1:
                case 2:
                    nb_frames = 2;
                    break;
                default:
                    if (len < 2) {
                        return -1;
                    }
                    nb_frames = (int)(data[1] & 0x3F);
                    break;
            }
            if (nb_frames <= 0) {
                return -1;
            }
            int spp = nb_frames * frame_samples;
            if (spp < 120 || spp > 5760) {
                return -1;
            }
            return spp;
        }

        static vlc_decode_fn lookup_orig_decode(genericui_vlc_decoder_t *p_dec) {
            vlc_decode_fn fn = NULL;
            lock_state();
            for (int i = 0; i < MAX_DECODER_SLOTS; i++) {
                if (g_slots[i].dec == p_dec) {
                    fn = g_slots[i].orig_decode;
                    break;
                }
            }
            if (fn == NULL) {
                fn = g_default_orig_decode;
            }
            unlock_state();
            return fn;
        }

        static int GenericUiOpusDecode(genericui_vlc_decoder_t *p_dec, genericui_vlc_block_t *p_block) {
            vlc_decode_fn orig_decode = lookup_orig_decode(p_dec);
            if (orig_decode == NULL) {
                return VLC_EGENERIC;
            }
            if (p_block != NULL && p_block->p_buffer != NULL && p_block->i_buffer >= 1) {
                int spp = genericui_opus_packet_samples_48k(p_block->p_buffer, p_block->i_buffer);
                if (spp >= 120 && spp <= 5760) {
                    if (p_block->i_nb_samples > (unsigned)spp) {
                        p_block->i_nb_samples = (unsigned)spp;
                    }
                    int64_t pkt_dur_us = ((int64_t)spp * 1000000LL) / 48000LL;
                    if (p_block->i_length >= pkt_dur_us) {
                        p_block->i_length = 0;
                    }
                }
            }
            return orig_decode(p_dec, p_block);
        }

        static int GenericUiOpusOpen(void *p_this) {
            genericui_vlc_decoder_t *p_dec = (genericui_vlc_decoder_t *)p_this;
            if (p_dec == NULL || p_dec->fmt_in.i_codec != VLC_CODEC_OPUS) {
                return VLC_EGENERIC;
            }

            lock_state();
            ensure_real_opus_loaded_locked();
            vlc_open_fn real_open = g_real_opus_open;
            unlock_state();

            if (real_open == NULL) {
                return VLC_EGENERIC;
            }

            int ret = real_open(p_this);
            if (ret != VLC_SUCCESS) {
                return ret;
            }

            vlc_decode_fn orig_decode = p_dec->pf_decode;
            if (orig_decode != NULL && orig_decode != GenericUiOpusDecode) {
                lock_state();
                if (g_default_orig_decode == NULL) {
                    g_default_orig_decode = orig_decode;
                }
                int empty_slot = -1;
                for (int i = 0; i < MAX_DECODER_SLOTS; i++) {
                    if (g_slots[i].dec == p_dec) {
                        g_slots[i].orig_decode = orig_decode;
                        empty_slot = i;
                        break;
                    }
                    if (empty_slot < 0 && g_slots[i].dec == NULL) {
                        empty_slot = i;
                    }
                }
                if (empty_slot >= 0 && g_slots[empty_slot].dec != p_dec) {
                    g_slots[empty_slot].dec = p_dec;
                    g_slots[empty_slot].orig_decode = orig_decode;
                }
                unlock_state();
                p_dec->pf_decode = GenericUiOpusDecode;
            }
            return VLC_SUCCESS;
        }

        static void GenericUiOpusClose(void *p_this) {
            genericui_vlc_decoder_t *p_dec = (genericui_vlc_decoder_t *)p_this;
            vlc_close_fn real_close = NULL;

            lock_state();
            real_close = g_real_opus_close;
            if (p_dec != NULL) {
                for (int i = 0; i < MAX_DECODER_SLOTS; i++) {
                    if (g_slots[i].dec == p_dec) {
                        if (p_dec->pf_decode == GenericUiOpusDecode) {
                            p_dec->pf_decode = g_slots[i].orig_decode;
                        }
                        g_slots[i].dec = NULL;
                        g_slots[i].orig_decode = NULL;
                        break;
                    }
                }
            }
            unlock_state();

            if (real_close != NULL) {
                real_close(p_this);
            }
        }

        VLC_EXPORT int VLC_CALL vlc_entry__3_0_0f(vlc_set_cb vlc_set, void *opaque) {
            void *module = NULL;
            void *config = NULL;
            if (vlc_set(opaque, NULL, VLC_MODULE_CREATE, &module) != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_NAME, "genericui_opus_fix") != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_DESCRIPTION, "GenericUI Opus packet duration sanitizer") != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_CAPABILITY, "audio decoder") != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_SCORE, (int)200) != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_SHORTNAME, "GenericUI Opus Fix") != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_CB_OPEN, "OpenDecoder", (void *)GenericUiOpusOpen) != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_CB_CLOSE, "CloseDecoder", (void *)GenericUiOpusClose) != 0) {
                return -1;
            }
            if (vlc_set(opaque, module, VLC_MODULE_NO_UNLOAD) != 0) {
                return -1;
            }
            (void)config;
            return 0;
        }

        VLC_EXPORT const char * VLC_CALL vlc_entry_copyright__3_0_0f(void) {
            return "Copyright (C) GenericUI";
        }

        VLC_EXPORT const char * VLC_CALL vlc_entry_license__3_0_0f(void) {
            return "LGPLv2.1+";
        }
    """.trimIndent()
}
