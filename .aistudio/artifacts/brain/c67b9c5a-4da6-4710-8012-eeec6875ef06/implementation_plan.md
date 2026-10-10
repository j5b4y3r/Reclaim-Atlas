# Layer 4: On-Device AI Vision Modesty & Awrah Protection Engine

Comprehensive architecture, research findings, and technical design for the 4th layer of defense: an on-device, battery-optimized vision AI scanner that detects revealing clothing, dance reels, cleavage, and uncovered body parts across Instagram, TikTok, browsers, and media apps, applying an instant protective blur shield in accordance with Islamic modesty principles (*Ghadd al-Basar*).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following requirements:
> 1. **Focus**: Discuss and detail open-source on-device vision models, Islamic modesty tools (*HaramBlur*, *Ghadd al-Basar*), and architecture options.
> 2. **AI Model Selection**: Lightweight quantized TensorFlow Lite (TFLite INT8 / NudeNet MobileNet-YOLOv8 Lite) running on-device at **3 FPS**.
> 3. **Smart Triggering**: Zero CPU/GPU/RAM when reading plain text; only samples frames when media (video playback or images) appears on screen.

- **Confirmed Decision 1**: Model architecture will utilize a quantized TFLite multi-class modesty detector (targeting cleavage, midriff/belly, lingerie/swimwear, thighs, and explicit exposure).
- **Confirmed Decision 2**: Frame rate is capped at 3 FPS with downscaled 256x256 / 320x320 buffers to ensure near-zero battery drain and zero thermal throttling.
- **Confirmed Decision 3**: Protective action triggers an instant, unbypassable "Lower Your Gaze" overlay shield with immediate back-navigation / home exit.

---

## 1. Research & Open-Source Vision Models

### A. Islamic Sources & Real-World Projects
1. **HaramBlur & Ghadd al-Basar ("Lower Your Gaze") Projects**:
   - Derived from the Quranic injunction in Surah An-Nur (24:30): *"Tell the believing men to lower their gaze and protect their private parts..."*
   - Islamic content moderation projects (e.g., **HaramBlur**, **Kahf Browser**, and Muslim recovery tools) address a critical vulnerability: mainstream blockers only block explicit pornography domains, whereas modern social media algorithms (Instagram Reels, TikTok, YouTube Shorts, X) aggressively push revealing attire, dance trends, and soft-erotic imagery.
   - Traditional adult domain blocklists fail on Instagram and TikTok because the domain itself is `instagram.com` or `tiktok.com`. The **only effective solution** is on-device computer vision analyzing screen pixels in real-time.

2. **Open-Source Model Architectures Compared**:
   - **NudeNet Lite (Mobile TFLite / ONNX)**:
     - Based on YOLOv8n / MobileNet backbone, quantized to INT8 (~12 MB).
     - Provides bounding boxes and class labels for specific anatomical zones:
       - `FEMALE_BREAST_EXPOSED`, `FEMALE_BREAST_COVERED` (cleavage, bikini, bra, tight revealing tops)
       - `BELLY_EXPOSED` (crop tops, midriff)
       - `BUTTOCKS_EXPOSED`, `BUTTOCKS_COVERED` (swimwear, tight shorts)
       - `FEMALE_GENITALIA_EXPOSED`, `MALE_GENITALIA_EXPOSED`
     - **Verdict**: Best fit. Because it outputs coordinates, the app can either blur the entire window or render targeted blackout tiles directly over offending bodies.
   - **Yahoo open_nsfw (MobileNet-v2 Quantized)**:
     - File size: ~2.5 MB to 4.2 MB.
     - Single classification score (0.0 to 1.0).
     - Extremely fast (<15ms per frame on mobile NNAPI), but lacks class granularity (cannot distinguish between an arm vs exposed cleavage).
   - **Recommended Stack**: **NudeNet-Lite INT8 TFLite** with NNAPI / GPU delegate fallback, supplemented by a fast skin-tone / exposure ratio pre-filter to bypass fully clothed or landscape frames before running inference.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Setup & Permission Flow**:
   - User navigates to the **Blocking** screen in the Self-Control app.
   - A new dedicated section titled **Layer 4: AI Modesty Guard (On-Device)** is present.
   - Enabling the toggle requests standard Android **MediaProjection** (Screen Capture) and **Display Over Other Apps** (System Alert Window) permissions with clear transparency explainers (100% on-device, zero network upload, strictly private).
2. **Idle State (Zero Resource Consumption)**:
   - While the user is messaging in WhatsApp, reading text articles, or browsing code, the capture loop remains in deep sleep (0 FPS, 0% CPU/GPU).
3. **Active Media State (3 FPS Adaptive Sampling)**:
   - When the user opens Instagram, TikTok, Snapchat, or web video players, the frame buffer awakens, sampling 1 frame every ~333 ms (3 FPS).
4. **Intervention Trigger ("Lower Your Gaze" Shield)**:
   - Upon detecting exposed cleavage, midriff, swimwear, or nudity exceeding the configured sensitivity threshold:
     - An instant full-screen blurred frosted-glass overlay dims the display within < 80ms.
     - A centered, dignified prompt displays:
       - Quranic citation or spiritual reminder: *"Lower your gaze and protect your heart."*
       - Quick action: **"Close App & Return Home"** or **"Skip Next Reel"**.

---

## 3. Battery & Performance Optimization Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                 BATTERY & THERMAL BUDGET                    │
├──────────────────────┬──────────────────────────────────────┤
│ Metric               │ Target Specification                 │
├──────────────────────┼──────────────────────────────────────┤
│ Sampling Rate        │ 3 FPS (333ms interval) during media  │
│ Idle Sampling Rate   │ 0 FPS (dormant during text / static) │
│ Frame Resolution     │ 256 x 256 pixels (downscaled)        │
│ Inference Latency    │ < 22ms per frame (NNAPI/GPU Delegate)│
│ RAM Allocation       │ < 35 MB resident memory              │
│ Battery Impact       │ < 1.2% per hour of social browsing   │
└──────────────────────┴──────────────────────────────────────┘
```

### Three-Stage Optimization Pipeline:
1. **Stage 1: Media Activity Gate**:
   - Checks accessibility node events (`SurfaceView`, `TextureView`, `VideoView`, or `ImageView` presence) and audio focus. If no visual media is present, do not capture any frame.
2. **Stage 2: Fast Skin-Histogram Pre-Filter**:
   - Calculates YCbCr skin-tone color distribution across the 256x256 frame in under 2ms. If human skin pixels account for less than 12% of the frame, skips TFLite inference entirely.
3. **Stage 3: Quantized TFLite Inference**:
   - Runs INT8 quantized weights on Android's Neural Networks API (NNAPI) or GPU delegate.

---

## 4. Technical Architecture & System Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       ANDROID SYSTEM RUNTIME                            │
│                                                                         │
│   Instagram / TikTok / Chrome / YouTube / Social Apps                   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                        (MediaProjection VirtualDisplay)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              SELF-CONTROL AI MODESTY ENGINE (FOREGROUND SERVICE)        │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ 1. Frame Scheduler (3 FPS Capped, Adaptive Media Gate)          │   │
│   └────────────────────────────────┬────────────────────────────────┘   │
│                                    ▼                                    │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ 2. Lightweight Pre-Filter (Skin pixel ratio & motion delta)     │   │
│   └────────────────────────────────┬────────────────────────────────┘   │
│                                    ▼                                    │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │ 3. Quantized TFLite Modesty Model (INT8 MobileNet / NudeNet)    │   │
│   │    - Exposed Breasts / Cleavage                                 │   │
│   │    - Exposed Belly / Midriff                                    │   │
│   │    - Exposed Thighs / Lingerie / Swimwear                       │   │
│   └────────────────────────────────┬────────────────────────────────┘   │
│                                    ▼                                    │
│                       [Violation Threshold Met?]                        │
│                                    │                                    │
│                   YES              │              NO                    │
│                    │               │               │                    │
│                    ▼                               ▼                    │
│   ┌────────────────────────────────┐       ┌──────────────────────┐     │
│   │ 4. System Alert Window Overlay │       │ Sleep until next tick│     │
│   │    "Lower Your Gaze" Shield    │       └──────────────────────┘     │
│   │    Auto-back / Dismiss Action  │                                    │
│   └────────────────────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Implementation Roadmap (Phases for Execution)

### Phase 1: Engine Core & Model Pipeline
- Add TFLite runtime dependencies (`org.tensorflow:tensorflow-lite`, `tensorflow-lite-gpu`, `tensorflow-lite-support`) to `app/build.gradle.kts`.
- Implement `ModestyClassifier.kt`: TFLite wrapper with hardware acceleration delegates, INT8 quantization support, and input/output tensor buffering.
- Implement `SkinExposurePreFilter.kt`: Fast native YCbCr color-space filter to eliminate unnecessary neural inferences.

### Phase 2: Screen Capture & Foreground Service
- Implement `AiModestyScannerService.kt`: Android Foreground Service managing `MediaProjection` and `VirtualDisplay` lifecycle with low-power 3 FPS timer.
- Integrate lifecycle management with existing `SelfControlAccessibilityService` to selectively trigger capture only when media containers are rendered.

### Phase 3: Defensive UI Overlay
- Implement `ModestyShieldOverlay.kt`: WindowManager-based frosted glass overlay displaying Islamic reminder (*"Lower your gaze"*) and rapid escape options.
- Add accountability lock protection so the AI Modesty filter cannot be deactivated during an active streak without partner approval.

### Phase 4: Preferences & Settings Integration
- Add **Layer 4: AI Modesty Guard** controls to `BlockingScreen.kt` with sensitivity presets:
  - *Strict (Awrah)*: Cleavage, midriff, thighs, swimwear.
  - *Standard*: Lingerie, swimwear, explicit exposure.
- Wire into `PreferencesManager`, `SelfControlRepository`, and Firestore sync.
